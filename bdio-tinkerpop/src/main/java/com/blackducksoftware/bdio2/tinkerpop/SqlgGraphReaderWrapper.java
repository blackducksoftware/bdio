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
import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static com.google.common.collect.Iterables.getOnlyElement;
import static java.lang.Boolean.FALSE;
import static java.util.stream.Collectors.joining;
import static org.umlg.sqlg.structure.PropertyType.STRING;
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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.T;
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

    protected SqlgGraphReaderWrapper(SqlgGraph sqlgGraph, GraphMapper mapper, List<TraversalStrategy<?>> strategies,
            Optional<String> base, Optional<Object> expandContext, int batchSize) {
        super(sqlgGraph, mapper, strategies, base, expandContext, batchSize);
        this.supportsBatchMode = sqlgGraph.features().supportsBatchMode();
    }

    @Override
    public SqlgGraph graph() {
        return (SqlgGraph) super.graph();
    }

    /**
     * Schedules the specified table to vacuumed and analyzed after the next commit.
     */
    void vacuumAnalyze(SchemaTable table, int mutations) {
        if (mutations > 10_000) {
            vacuumAnalyzeTables.push(table);
        }
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
                if (!key.equals(Bdio.DataProperty.creationDateTime.name())
                        && !key.equals(Bdio.DataProperty.lastModifiedDateTime.name())) {
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
            SqlgGraph sqlgGraph = (SqlgGraph) wrapper().graph();
            SqlDialect dialect = sqlgGraph.getSqlDialect();
            SchemaTable file = SchemaTable.from(sqlgGraph, Bdio.Class.File.name()).withPrefix(VERTEX_PREFIX);

            sqlgGraph.getTopology().ensureVertexLabelPropertiesExist(file.getSchema(), file.withOutPrefix().getTable(), ImmutableMap.of(
                    Bdio.DataProperty.path.name(), PropertyType.STRING,
                    GraphMapper.FILE_PARENT_KEY, PropertyType.STRING));

            String whereClause = wrapper().strategies().count() == 0 ? ""
                    : wrapper().strategies()
                            .flatMap(ofType(PartitionStrategy.class))
                            // TODO Major hack
                            .filter(ps -> !ps.getPartitionKey().equals("document"))
                            .map(ps -> new StringBuilder()
                                    .append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(ps.getPartitionKey()))
                                    .append(ps.getReadPartitions().size() == 1 ? " = " : " IN (")
                                    .append(ps.getReadPartitions().stream()
                                            .map(rp -> sqlgGraph.getSqlDialect().valueToValuesString(STRING, rp))
                                            .collect(joining(", ")))
                                    .append(ps.getReadPartitions().size() == 1 ? "" : ")"))
                            .collect(joining(" ) AND ( ", " WHERE ( ", " )"));

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ")
                    .append(dialect.maybeWrapInQoutes(GraphMapper.FILE_PARENT_KEY))
                    .append(" FROM ")
                    .append(dialect.maybeWrapInQoutes(file.getTable()))
                    .append(whereClause)
                    .append(" EXCEPT SELECT ")
                    .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.path.name()))
                    .append(" FROM ")
                    .append(dialect.maybeWrapInQoutes(file.getTable()))
                    .append(whereClause)
                    .append(dialect.needsSemicolon() ? ";" : "");

            // We use this query to test for existing parents
            StringBuilder parentExistsSql = new StringBuilder();
            parentExistsSql.append("SELECT 1 FROM ")
                    .append(dialect.maybeWrapInQoutes(file.getTable()))
                    .append(" WHERE ")
                    .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.path.name()))
                    .append(" = ? ");
            wrapper().forEachReadPartition((k, r) -> parentExistsSql.append(" AND ")
                    .append(dialect.maybeWrapInQoutes(k))
                    .append(" IN ")
                    .append(r.stream().map(v -> dialect.valueToValuesString(STRING, v)).collect(joining(", ", "(", ")"))));
            parentExistsSql.append(dialect.needsSemicolon() ? ";" : "");

            Connection conn = sqlgGraph.tx().getConnection();
            try (PreparedStatement parentExistsStatement = conn.prepareStatement(parentExistsSql.toString())) {
                try (Statement statement = conn.createStatement()) {
                    // Execute the big LEFT JOIN once to find the "deepest" missing parents
                    try (ResultSet resultSet = statement.executeQuery(sql.toString())) {
                        wrapper().startBatchTx();
                        while (resultSet.next()) {
                            String path = resultSet.getString(1);
                            while (path != null) {
                                Optional<String> parentPath = HID.from(path).tryParent().map(HID::toUriString);
                                Stream.Builder<Object> properties = Stream.builder();
                                properties.add(T.label).add(Bdio.Class.File.name());
                                mapper.identifierKey().ifPresent(key -> {
                                    properties.add(key);
                                    properties.add(BdioObject.randomId());
                                });
                                wrapper().forEachPartition((k, v) -> {
                                    properties.add(k);
                                    properties.add(v);
                                });
                                properties.add(Bdio.DataProperty.path.name()).add(path);
                                properties.add(Bdio.DataProperty.fileSystemType.name()).add(Bdio.FileSystemType.DIRECTORY.toString());
                                parentPath.ifPresent(fileParent -> {
                                    properties.add(FILE_PARENT_KEY);
                                    properties.add(fileParent);
                                });
                                properties.add(mapper.implicitKey().get()).add(Boolean.TRUE);
                                sqlgGraph.addVertex(properties.build().toArray());
                                wrapper().batchFlushTx();

                                // Now use the computed parent to "walk" up the file hierarchy
                                path = parentPath.orElse(null);
                                if (path != null) {
                                    parentExistsStatement.setString(1, path);
                                    try (ResultSet parentExists = parentExistsStatement.executeQuery()) {
                                        if (parentExists.next()) {
                                            path = null;
                                        }
                                    }
                                }
                            }
                        }
                        wrapper().flushTx();
                    }
                }
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
                    .append(dialect.maybeWrapInQoutes(FILE_PARENT_KEY));
            wrapper().forEachReadPartition((k, r) -> {
                for (String t : Arrays.asList("p", "c")) {
                    sql.append(" AND ").append(t).append('.').append(dialect.maybeWrapInQoutes(k));
                    if (r.size() == 1) {
                        sql.append(" = ").append(dialect.valueToValuesString(STRING, getOnlyElement(r)));
                    } else {
                        sql.append(" IN ").append(r.stream().map(v -> dialect.valueToValuesString(STRING, v)).collect(joining(", ", "(", ")")));
                    }
                }
            });
            sql.append(dialect.needsSemicolon() ? ";" : "");

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

        private void updateFileSystemType(SqlgGraph sqlgGraph, Bdio.FileSystemType fileSystemType) {
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
            wrapper().forEachReadPartition((k, r) -> sql.append(" AND ")
                    .append(dialect.maybeWrapInQoutes(file.getTable()))
                    .append('.')
                    .append(dialect.maybeWrapInQoutes(k))
                    .append(" IN ")
                    .append(r.stream().map(v -> dialect.valueToValuesString(STRING, v)).collect(joining(", ", "(", ")"))));
            sql.append(" AND ")
                    .append(dialect.maybeWrapInQoutes(file.getTable()))
                    .append(".")
                    .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.fileSystemType.name()))
                    .append(" IS NULL");
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
            } else if (fileSystemType != Bdio.FileSystemType.DIRECTORY) {
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
