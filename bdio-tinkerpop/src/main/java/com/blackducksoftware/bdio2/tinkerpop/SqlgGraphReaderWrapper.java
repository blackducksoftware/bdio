/*
 * Copyright 2017 Black Duck Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blackducksoftware.bdio2.tinkerpop;

import static com.blackducksoftware.bdio2.tinkerpop.GraphMapper.FILE_PARENT_KEY;
import static com.blackducksoftware.common.base.ExtraOptionals.and;
import static java.lang.Boolean.FALSE;
import static org.umlg.sqlg.structure.SqlgGraph.TRANSACTION_MUST_BE_IN;
import static org.umlg.sqlg.structure.topology.Topology.EDGE_PREFIX;
import static org.umlg.sqlg.structure.topology.Topology.ID;
import static org.umlg.sqlg.structure.topology.Topology.IN_VERTEX_COLUMN_END;
import static org.umlg.sqlg.structure.topology.Topology.OUT_VERTEX_COLUMN_END;
import static org.umlg.sqlg.structure.topology.Topology.VERTEX_PREFIX;

import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.umlg.sqlg.sql.dialect.PostgresDialect;
import org.umlg.sqlg.sql.dialect.SqlBulkDialect;
import org.umlg.sqlg.sql.dialect.SqlDialect;
import org.umlg.sqlg.structure.BatchManager;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgExceptions;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;
import org.umlg.sqlg.util.SqlgUtil;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/**
 * Specialization of the read graph context to use when the underlying graph is a Sqlg graph.
 *
 * @author jgustie
 */
class SqlgGraphReaderWrapper extends GraphReaderWrapper {

    /**
     * Until issue #296 is resolved we cannot flush or commit concurrently because of the potential for interleaving
     * {@code COPY} statements getting assigned the wrong identifier.
     */
    private static final Object flushLock = new Object();

    /**
     * Flag indicating if the graph supports batch mode or not.
     */
    private final boolean supportsBatchMode;

    /**
     * Tables that need to be vacuumed and analyzed.
     */
    private final Deque<SchemaTable> vacuumAnalyzeTables = new ArrayDeque<>();

    protected SqlgGraphReaderWrapper(SqlgGraph sqlgGraph, GraphMapper mapper, List<TraversalStrategy<?>> strategies, Optional<Object> expandContext,
            int batchSize) {
        // Sqlg issue #296 workaround: the extra synchronization overhead requires a smaller default batch size
        super(sqlgGraph, mapper, strategies, expandContext, batchSize != 10_000 ? batchSize : 2_000);
        this.supportsBatchMode = sqlgGraph.features().supportsBatchMode();
    }

    @Override
    public SqlgGraph graph() {
        return (SqlgGraph) super.graph();
    }

    /**
     * Schedules the specified table to vacuumed and analyzed after the next commit.
     */
    void vacuumAnalyze(SchemaTable table) {
        vacuumAnalyzeTables.push(table);
    }

    @Override
    public void startBatchTx() {
        // (Re-)enable batch mode if it is supported
        if (supportsBatchMode) {
            graph().tx().normalBatchModeOn();
        }
    }

    @Override
    public void flushTx() {
        super.flushTx();
        if (supportsBatchMode) {
            // Sqlg issue #296 workaround
            synchronized (flushLock) {
                graph().tx().flush();
            }
        }
    }

    @Override
    public void createMetadata(BdioMetadata metadata) {
        // If we are streaming, we cannot create a new metadata vertex unless it is also streamed
        if (graph().tx().isInStreamingBatchMode()) {
            mapper().metadataLabel().ifPresent(metadataLabel -> {
                Object[] metadataKeyValues = getMetadataProperties(metadata);
                Vertex metadataVertex = traversal().V().hasLabel(metadataLabel).tryNext().orElse(null);
                if (metadataVertex != null) {
                    mergeProperties(metadataVertex, null, metadataKeyValues);
                } else {
                    flushTx();
                    graph().streamVertex(metadataKeyValues);
                    flushTx();
                }
            });
        } else {
            super.createMetadata(metadata);
        }
    }

    @Override
    public void commitTx() {
        // Sqlg issue #296 workaround
        synchronized (flushLock) {
            super.commitTx();

            // Now that we have committed, we can vacuum
            if (!vacuumAnalyzeTables.isEmpty()) {
                try (Connection conn = graph().getConnection()) {
                    try (Statement statement = conn.createStatement()) {
                        SqlDialect dialect = graph().getSqlDialect();
                        while (!vacuumAnalyzeTables.isEmpty()) {
                            SchemaTable table = vacuumAnalyzeTables.pop();
                            StringBuilder sql = new StringBuilder();
                            sql.append("VACUUM ANALYZE ")
                                    .append(dialect.maybeWrapInQoutes(table.getTable()))
                                    .append(dialect.needsSemicolon() ? ";" : "");
                            statement.execute(sql.toString());
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public void getNodeDataProperties(Map<String, Object> node, BiConsumer<Object, Object> dataPropertyHandler, boolean includeId) {
        // Sqlg issue #294 workaround
        BiConsumer<Object, Object> handler = dataPropertyHandler;
        boolean isMetadata = and(Optional.ofNullable(node.get(JsonLdConsts.TYPE)), mapper().metadataLabel(), Objects::equals).orElse(FALSE);
        if (!isMetadata && (graph().tx().isInNormalBatchMode() || graph().tx().isInStreamingBatchMode())) {
            handler = (key, value) -> {
                if (!(value instanceof ZonedDateTime)) {
                    dataPropertyHandler.accept(key, value);
                }
            };
        }
        super.getNodeDataProperties(node, handler, includeId);
    }

    public <L, R> void bulkAddEdges(String outVertexLabel, String inVertexLabel, String edgeLabel,
            Pair<String, String> idFields, Collection<Pair<L, R>> uids, Object... keyValues) {
        // Sqlg issue #300 workaround

        // No key values means we can just use the real implementation
        if (keyValues.length == 0 || !(graph().getSqlDialect() instanceof PostgresDialect)) {
            graph().bulkAddEdges(outVertexLabel, inVertexLabel, edgeLabel, idFields, uids);
            return;
        }

        Triple<Map<String, PropertyType>, Map<String, Object>, Map<String, Object>> keyValueMapTriple = SqlgUtil
                .validateVertexKeysValues(graph().getSqlDialect(), keyValues);
        Map<String, PropertyType> edgeColumns = keyValueMapTriple.getLeft();
        Map<String, Object> edgePropertyMap = keyValueMapTriple.getRight();
        class BulkAddEdgesWithProperties extends PostgresDialect {

            private <LL, RR> void _copyInBulkTempEdges(SqlgGraph sqlgGraph, SchemaTable schemaTable, Collection<Pair<LL, RR>> uids,
                    PropertyType inPropertyType, PropertyType outPropertyType) {
                try {
                    Method m = PostgresDialect.class.getDeclaredMethod("copyInBulkTempEdges",
                            SqlgGraph.class, SchemaTable.class, Collection.class, PropertyType.class, PropertyType.class);
                    m.setAccessible(true);
                    m.invoke(this, sqlgGraph, schemaTable, uids, outPropertyType, inPropertyType);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public <LL, RR> void bulkAddEdges(SqlgGraph sqlgGraph, SchemaTable out, SchemaTable in, String edgeLabel,
                    Pair<String, String> idFields, Collection<Pair<LL, RR>> uids) {
                if (!sqlgGraph.tx().isInStreamingBatchMode() && !sqlgGraph.tx().isInStreamingWithLockBatchMode()) {
                    throw SqlgExceptions.invalidMode("Transaction must be in " + BatchManager.BatchModeType.STREAMING + " or "
                            + BatchManager.BatchModeType.STREAMING_WITH_LOCK + " mode for bulkAddEdges");
                }
                if (!uids.isEmpty()) {
                    // createVertexLabel temp table and copy the uids into it
                    Map<String, PropertyType> columns = new HashMap<>();
                    Map<String, PropertyType> outProperties = sqlgGraph.getTopology().getTableFor(out.withPrefix(VERTEX_PREFIX));
                    Map<String, PropertyType> inProperties = sqlgGraph.getTopology().getTableFor(in.withPrefix(VERTEX_PREFIX));
                    PropertyType outPropertyType;
                    if (idFields.getLeft().equals(Topology.ID)) {
                        outPropertyType = PropertyType.INTEGER;
                    } else {
                        outPropertyType = outProperties.get(idFields.getLeft());
                    }
                    PropertyType inPropertyType;
                    if (idFields.getRight().equals(Topology.ID)) {
                        inPropertyType = PropertyType.INTEGER;
                    } else {
                        inPropertyType = inProperties.get(idFields.getRight());
                    }
                    columns.put("out", outPropertyType);
                    columns.put("in", inPropertyType);
                    SecureRandom random = new SecureRandom();
                    byte bytes[] = new byte[6];
                    random.nextBytes(bytes);
                    String tmpTableIdentified = Base64.getEncoder().encodeToString(bytes);
                    tmpTableIdentified = Topology.BULK_TEMP_EDGE + tmpTableIdentified;
                    sqlgGraph.getTopology().getPublicSchema().createTempTable(tmpTableIdentified, columns);
                    this._copyInBulkTempEdges(sqlgGraph, SchemaTable.of(out.getSchema(), tmpTableIdentified), uids, outPropertyType, inPropertyType);
                    // executeRegularQuery copy from select. select the edge ids to copy into the new table by joining
                    // on the temp table

                    Optional<VertexLabel> outVertexLabelOptional = sqlgGraph.getTopology().getVertexLabel(out.getSchema(), out.getTable());
                    Optional<VertexLabel> inVertexLabelOptional = sqlgGraph.getTopology().getVertexLabel(in.getSchema(), in.getTable());
                    Preconditions.checkState(outVertexLabelOptional.isPresent(), "Out VertexLabel must be present. Not found for %s", out.toString());
                    Preconditions.checkState(inVertexLabelOptional.isPresent(), "In VertexLabel must be present. Not found for %s", in.toString());

                    // noinspection OptionalGetWithoutIsPresent
                    sqlgGraph.getTopology().ensureEdgeLabelExist(edgeLabel, outVertexLabelOptional.get(), inVertexLabelOptional.get(), edgeColumns);

                    StringBuilder sql = new StringBuilder("INSERT INTO \n");
                    sql.append(this.maybeWrapInQoutes(out.getSchema()));
                    sql.append(".");
                    sql.append(this.maybeWrapInQoutes(EDGE_PREFIX + edgeLabel));
                    sql.append(" (");
                    sql.append(this.maybeWrapInQoutes(out.getSchema() + "." + out.getTable() + Topology.OUT_VERTEX_COLUMN_END));
                    sql.append(",");
                    sql.append(this.maybeWrapInQoutes(in.getSchema() + "." + in.getTable() + Topology.IN_VERTEX_COLUMN_END));
                    edgePropertyMap.keySet().forEach(k -> sql.append(',').append(this.maybeWrapInQoutes(k)));
                    sql.append(") \n");
                    sql.append("select _out.\"ID\" as \"");
                    sql.append(out.getSchema() + "." + out.getTable() + Topology.OUT_VERTEX_COLUMN_END);
                    sql.append("\", _in.\"ID\" as \"");
                    sql.append(in.getSchema() + "." + in.getTable() + Topology.IN_VERTEX_COLUMN_END);
                    sql.append("\"");
                    edgePropertyMap.forEach((k, v) -> {
                        sql.append(',');
                        sql.append(this.valueToValuesString(edgeColumns.get(k), v));
                        sql.append(" as ");
                        sql.append(this.maybeWrapInQoutes(k));
                    });
                    sql.append(" FROM ");
                    sql.append(this.maybeWrapInQoutes(in.getSchema()));
                    sql.append(".");
                    sql.append(this.maybeWrapInQoutes(VERTEX_PREFIX + in.getTable()));
                    sql.append(" _in join ");
                    sql.append(this.maybeWrapInQoutes(tmpTableIdentified) + " ab on ab.in = _in." + this.maybeWrapInQoutes(idFields.getRight()) + " join ");
                    sql.append(this.maybeWrapInQoutes(out.getSchema()));
                    sql.append(".");
                    sql.append(this.maybeWrapInQoutes(VERTEX_PREFIX + out.getTable()));
                    sql.append(" _out on ab.out = _out." + this.maybeWrapInQoutes(idFields.getLeft()));

                    Connection conn = sqlgGraph.tx().getConnection();
                    try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                        preparedStatement.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        SqlBulkDialect sqlBulkDialect = new BulkAddEdgesWithProperties();
        SqlgGraph self = graph();

        // This is what SqlgGraph.bulkAddEdges does after validating the dialect
        if (!self.tx().isInStreamingBatchMode() && !self.tx().isInStreamingWithLockBatchMode()) {
            throw SqlgExceptions.invalidMode(TRANSACTION_MUST_BE_IN + BatchManager.BatchModeType.STREAMING + " or "
                    + BatchManager.BatchModeType.STREAMING_WITH_LOCK + " mode for bulkAddEdges");
        }
        if (!uids.isEmpty()) {
            SchemaTable outSchemaTable = SchemaTable.from(self, outVertexLabel);
            SchemaTable inSchemaTable = SchemaTable.from(self, inVertexLabel);
            sqlBulkDialect.bulkAddEdges(self, outSchemaTable, inSchemaTable, edgeLabel, idFields, uids);
        }
    }

    protected static class SqlgAddMissingFileParentsOperation extends BlackDuckIoOperations.AddMissingFileParentsOperation {
        public SqlgAddMissingFileParentsOperation(GraphReaderWrapper wrapper) {
            super(wrapper);
        }

        @Override
        protected void createParentEdges(GraphTraversalSource g, GraphMapper mapper) {
            SqlgGraph sqlgGraph = (SqlgGraph) wrapper().graph();
            SqlDialect dialect = sqlgGraph.getSqlDialect();
            SchemaTable file = SchemaTable.from(sqlgGraph, Bdio.Class.File.name()).withPrefix(VERTEX_PREFIX);
            SchemaTable parent = SchemaTable.from(sqlgGraph, Bdio.ObjectProperty.parent.name()).withPrefix(EDGE_PREFIX);

            Map<String, PropertyType> properties = new HashMap<>();
            properties.put(wrapper().mapper().implicitKey().get(), PropertyType.BOOLEAN);
            wrapper().forEachPartition((k, v) -> properties.put(k, PropertyType.from(v)));
            sqlgGraph.getTopology().ensureEdgeLabelExist(Bdio.ObjectProperty.parent.name(), file.withOutPrefix(), file.withOutPrefix(), properties);

            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO ")
                    .append(dialect.maybeWrapInQoutes(parent.getTable()))
                    .append(" (")
                    .append(dialect.maybeWrapInQoutes(wrapper().mapper().implicitKey().get()));
            wrapper().forEachPartition((k, v) -> sql.append(", ").append(dialect.maybeWrapInQoutes(k)));
            sql.append(", ")
                    .append(dialect.maybeWrapInQoutes(file.withOutPrefix() + IN_VERTEX_COLUMN_END))
                    .append(", ")
                    .append(dialect.maybeWrapInQoutes(file.withOutPrefix() + OUT_VERTEX_COLUMN_END))
                    .append(")\n  SELECT true");
            wrapper().forEachPartition((k, v) -> sql.append(", ").append(dialect.valueToValuesString(properties.get(k), v)));
            sql.append(", p.")
                    .append(dialect.maybeWrapInQoutes(ID))
                    .append(", c.")
                    .append(dialect.maybeWrapInQoutes(ID))
                    .append(" FROM ")
                    .append(dialect.maybeWrapInQoutes(file.getTable()))
                    .append(" p JOIN ")
                    .append(dialect.maybeWrapInQoutes(file.getTable()))
                    .append(" c ON p.")
                    .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.path.name()))
                    .append(" = c.")
                    .append(dialect.maybeWrapInQoutes(FILE_PARENT_KEY))
                    .append(dialect.needsSemicolon() ? ";" : "");

            executeUpdate(sqlgGraph, sql);
        }
    }

    protected static class SqlgImplyFileSystemTypeOperation extends BlackDuckIoOperations.ImplyFileSystemTypeOperation {
        public SqlgImplyFileSystemTypeOperation(GraphReaderWrapper wrapper) {
            super(wrapper);
        }

        @Override
        protected void updateDirectoryTypes(GraphTraversalSource g) {
            SqlgGraph sqlgGraph = (SqlgGraph) g.getGraph();
            updateFileSystemType(sqlgGraph, Bdio.FileSystemType.DIRECTORY_ARCHIVE);
            updateFileSystemType(sqlgGraph, Bdio.FileSystemType.DIRECTORY);
        }

        private static void updateFileSystemType(SqlgGraph sqlgGraph, Bdio.FileSystemType fileSystemType) {
            SqlDialect dialect = sqlgGraph.getSqlDialect();
            SchemaTable file = SchemaTable.from(sqlgGraph, Bdio.Class.File.name()).withPrefix(VERTEX_PREFIX);
            SchemaTable parent = SchemaTable.from(sqlgGraph, Bdio.ObjectProperty.parent.name()).withPrefix(EDGE_PREFIX);

            // Make sure everything we are about to query is properly constructed
            VertexLabel fileLabel = sqlgGraph.getTopology().ensureVertexLabelExist(file.getSchema(), file.withOutPrefix().getTable());
            sqlgGraph.getTopology().ensureEdgeLabelExist(parent.withOutPrefix().getTable(), fileLabel, fileLabel, ImmutableMap.of());
            sqlgGraph.getTopology().ensureVertexLabelPropertiesExist(file.getSchema(), file.withOutPrefix().getTable(), ImmutableMap.of(
                    Bdio.DataProperty.fileSystemType.name(), PropertyType.STRING,
                    Bdio.DataProperty.byteCount.name(), PropertyType.LONG,
                    Bdio.DataProperty.contentType.name(), PropertyType.STRING));

            StringBuilder sql = new StringBuilder();
            sql.append("\nUPDATE\n\t")
                    .append(dialect.maybeWrapInQoutes(file.getSchema()))
                    .append('.')
                    .append(dialect.maybeWrapInQoutes(file.getTable()))
                    .append("\nSET\n\t")
                    .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.fileSystemType.name()))
                    .append(" = ")
                    .append(dialect.valueToValuesString(PropertyType.STRING, fileSystemType.toString()))
                    .append("\nFROM\n\t")
                    .append(dialect.maybeWrapInQoutes(parent.getSchema()))
                    .append('.')
                    .append(dialect.maybeWrapInQoutes(parent.getTable()))
                    .append("\nWHERE\n\t")
                    .append(dialect.maybeWrapInQoutes(file.getTable()))
                    .append('.')
                    .append(dialect.maybeWrapInQoutes(Topology.ID))
                    .append(" = ")
                    .append(dialect.maybeWrapInQoutes(parent.getTable()))
                    .append('.')
                    .append(dialect.maybeWrapInQoutes(file.withOutPrefix() + Topology.IN_VERTEX_COLUMN_END));
            if (fileSystemType == Bdio.FileSystemType.DIRECTORY_ARCHIVE) {
                sql.append(" AND (")
                        .append(dialect.maybeWrapInQoutes(file.getTable()))
                        .append(".")
                        .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.byteCount.name()))
                        .append(" IS NOT NULL OR ")
                        .append(dialect.maybeWrapInQoutes(file.getTable()))
                        .append(".")
                        .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.contentType.name()))
                        .append(" IS NOT NULL)");
            } else if (fileSystemType == Bdio.FileSystemType.DIRECTORY) {
                sql.append(" AND ")
                        .append(dialect.maybeWrapInQoutes(file.getTable()))
                        .append(".")
                        .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.fileSystemType.name()))
                        .append(" IS NULL");
            } else {
                throw new IllegalArgumentException("file system type must be DIRECTORY or DIRECTORY_ARCHIVE");
            }
            if (dialect.needsSemicolon()) {
                sql.append(";");
            }

            executeUpdate(sqlgGraph, sql);
        }
    }

    /**
     * Helper to execute a raw SQL update against a Sqlg graph.
     */
    private static void executeUpdate(SqlgGraph sqlgGraph, CharSequence sql) {
        Connection conn = sqlgGraph.tx().getConnection();
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
