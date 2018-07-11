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
import static org.umlg.sqlg.structure.topology.Topology.EDGE_PREFIX;
import static org.umlg.sqlg.structure.topology.Topology.ID;
import static org.umlg.sqlg.structure.topology.Topology.IN_VERTEX_COLUMN_END;
import static org.umlg.sqlg.structure.topology.Topology.OUT_VERTEX_COLUMN_END;
import static org.umlg.sqlg.structure.topology.Topology.VERTEX_PREFIX;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.umlg.sqlg.sql.dialect.SqlDialect;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.common.value.HID;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableMap;

/**
 * Specialization of the read graph context to use when the underlying graph is a Sqlg graph.
 *
 * @author jgustie
 */
class SqlgGraphReaderWrapper extends GraphReaderWrapper {

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
        super(sqlgGraph, mapper, strategies, expandContext, batchSize);
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
            graph().tx().flush();
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

    protected static class SqlgAddMissingFileParentsOperation extends BlackDuckIoOperations.AddMissingFileParentsOperation {
        public SqlgAddMissingFileParentsOperation(GraphReaderWrapper wrapper) {
            super(wrapper);
        }

        @Override
        protected void createMissingFiles(GraphTraversalSource g, GraphMapper mapper) {
            // Same approach as the super: iterate over the list of missing parents until we stop finding them
            SqlgGraph sqlgGraph = (SqlgGraph) wrapper().graph();
            SqlDialect dialect = sqlgGraph.getSqlDialect();
            SchemaTable file = SchemaTable.from(sqlgGraph, Bdio.Class.File.name()).withPrefix(VERTEX_PREFIX);

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT DISTINCT m.")
                    .append(dialect.maybeWrapInQoutes(GraphMapper.FILE_PARENT_KEY))
                    .append(" FROM ")
                    .append(dialect.maybeWrapInQoutes(file.getTable()))
                    .append(" m LEFT JOIN ")
                    .append(dialect.maybeWrapInQoutes(file.getTable()))
                    .append(" f ON m.")
                    .append(dialect.maybeWrapInQoutes(GraphMapper.FILE_PARENT_KEY))
                    .append(" = f.")
                    .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.path.name()))
                    .append(" WHERE m.")
                    .append(dialect.maybeWrapInQoutes(GraphMapper.FILE_PARENT_KEY))
                    .append(" IS NOT NULL AND f.")
                    .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.path.name()))
                    .append(" IS NULL")
                    .append(dialect.needsSemicolon() ? ";" : "");

            wrapper().startBatchTx();
            Connection conn = sqlgGraph.tx().getConnection();
            try (PreparedStatement statement = conn.prepareStatement(sql.toString())) {
                int created;
                do {
                    created = 0;
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            created++;
                            String path = resultSet.getString(1);
                            String parentPath = HID.from(path).tryParent().map(HID::toUriString).orElse(null);
                            g.V().addV(Bdio.Class.File.name())
                                    .property(Bdio.DataProperty.path.name(), path)
                                    .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.DIRECTORY.toString())
                                    .property(mapper.implicitKey().get(), Boolean.TRUE)
                                    .property(FILE_PARENT_KEY, parentPath)
                                    .sideEffect(t -> {
                                        mapper.identifierKey().ifPresent(key -> t.get().property(key, BdioObject.randomId()));
                                        wrapper().batchFlushTx();
                                    })
                                    .iterate();
                        }
                        wrapper().flushTx();
                    }
                } while (created > 0);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
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
