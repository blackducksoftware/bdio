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

import static com.blackducksoftware.common.base.ExtraOptionals.and;
import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static java.lang.Boolean.FALSE;
import static java.util.stream.Collectors.joining;
import static org.umlg.sqlg.structure.SqlgGraph.TRANSACTION_MUST_BE_IN;
import static org.umlg.sqlg.structure.topology.Topology.EDGE_PREFIX;
import static org.umlg.sqlg.structure.topology.Topology.VERTEX_PREFIX;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.umlg.sqlg.sql.dialect.PostgresDialect;
import org.umlg.sqlg.sql.dialect.SqlBulkDialect;
import org.umlg.sqlg.structure.BatchManager;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.RecordId;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgExceptions;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;
import org.umlg.sqlg.util.SqlgUtil;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

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

    @Override
    public void startBatchTx() {
        // (Re-)enable batch mode if it is supported
        if (supportsBatchMode) {
            graph().tx().normalBatchModeOn();
        }
    }

    @Override
    public void flushTx() {
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
    public GraphTraversal<?, Collection<Vertex>> groupByMultiple(String label, String groupByKey) {
        // This is much faster to compute in the database
        String whereClause = strategies()
                .flatMap(ofType(PartitionStrategy.class))
                .filter(ps -> !ps.getReadPartitions().isEmpty())
                .map(ps -> new StringBuilder()
                        .append(graph().getSqlDialect().maybeWrapInQoutes(ps.getPartitionKey()))
                        .append(ps.getReadPartitions().size() == 1 ? " = " : " IN (")
                        .append(ps.getReadPartitions().stream()
                                .map(rp -> graph().getSqlDialect().valueToValuesString(PropertyType.STRING, rp))
                                .collect(joining(", ")))
                        .append(ps.getReadPartitions().size() == 1 ? "" : ")"))
                .collect(joining(" ) AND ( "));

        return traversal().inject(SchemaTable.from(graph(), label))
                .flatMap(t -> {
                    SchemaTable schemaTable = t.get();
                    StringBuilder sql = new StringBuilder()
                            .append("SELECT array_agg(")
                            .append(graph().getSqlDialect().maybeWrapInQoutes(Topology.ID))
                            .append(") FROM ")
                            .append(graph().getSqlDialect().maybeWrapInQoutes(schemaTable.getSchema()))
                            .append('.')
                            .append(graph().getSqlDialect().maybeWrapInQoutes(schemaTable.withPrefix(Topology.VERTEX_PREFIX).getTable()));
                    if (!whereClause.isEmpty()) {
                        sql.append(" WHERE (").append(whereClause).append(" )");
                    }
                    sql.append(" GROUP BY ")
                            .append(graph().getSqlDialect().maybeWrapInQoutes(groupByKey))
                            .append(" HAVING count(1) > 1")
                            .append(graph().getSqlDialect().needsSemicolon() ? ";" : "");

                    Connection conn = graph().tx().getConnection();
                    try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                        try (ResultSet rs = preparedStatement.executeQuery()) {
                            List<Object[]> result = new ArrayList<>();
                            while (rs.next()) {
                                Object array = rs.getArray(1).getArray();
                                Object[] row = new Object[Array.getLength(array)];
                                for (int i = 0; i < row.length; ++i) {
                                    row[i] = RecordId.from(schemaTable, (Long) Array.get(array, i));
                                }
                                Arrays.sort(row);
                                result.add(row);
                            }
                            return result.iterator();
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(t -> Lists.newArrayList(graph().vertices(t.get())));
    }

    @Override
    public void commitTx() {
        // Sqlg issue #296 workaround
        synchronized (flushLock) {
            super.commitTx();
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

}
