/*
 * Copyright 2018 Synopsys, Inc.
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
package com.blackducksoftware.bdio2.tinkerpop.sqlg;

import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static com.google.common.base.Preconditions.checkArgument;
import static org.umlg.sqlg.structure.topology.Topology.EDGE_PREFIX;
import static org.umlg.sqlg.structure.topology.Topology.IN_VERTEX_COLUMN_END;
import static org.umlg.sqlg.structure.topology.Topology.OUT_VERTEX_COLUMN_END;
import static org.umlg.sqlg.structure.topology.Topology.VERTEX_PREFIX;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoNormalizationSpi;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.strategy.SqlgSimpleQueryStrategy;
import com.blackducksoftware.common.value.HID;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

final class SqlgBlackDuckIoNormalization extends BlackDuckIoNormalizationSpi {

    public SqlgBlackDuckIoNormalization(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        super(traversal, options, frame);
        checkArgument(traversal.getGraph() instanceof SqlgGraph, "expected SqlgGraph");
    }

    @Override
    protected SqlgGraph graph() {
        return (SqlgGraph) super.graph();
    }

    @Override
    public void identifyRoot() {
        tx(() -> super.identifyRoot());
    }

    @Override
    public void addMissingFileParents() {
        tx(() -> super.addMissingFileParents());
    }

    @Override
    public void addMissingProjectDependencies() {
        tx(() -> super.addMissingProjectDependencies());
    }

    @Override
    public void implyFileSystemTypes() {
        tx(() -> super.implyFileSystemTypes());
    }

    @Override
    protected void updateDirectoryFileSystemTypes() {
        updateFileSystemType(Bdio.FileSystemType.DIRECTORY_ARCHIVE);
        updateFileSystemType(Bdio.FileSystemType.DIRECTORY);
    }

    @Override
    protected void updateSymlinkFileSystemTypes(GraphTraversalSource g) {
        super.updateSymlinkFileSystemTypes(g.withStrategies(SqlgSimpleQueryStrategy.instance()));
    }

    @Override
    protected void updateRegularFileSystemTypes(GraphTraversalSource g) {
        super.updateRegularFileSystemTypes(g.withStrategies(SqlgSimpleQueryStrategy.instance()));
    }

    private void inReadPartitions(PartitionStrategy p, SqlgQueryBuilder sql) {
        sql.in(p.getPartitionKey(), p.getReadPartitions(), PropertyType.STRING);
    }

    @Override
    protected void createMissingFiles() {
        // TODO Context
        SchemaTable file = SchemaTable.from(graph(), Bdio.Class.File.name()).withPrefix(Topology.VERTEX_PREFIX);
        List<TraversalStrategy<?>> strategies = traversal().getStrategies().toList();

        // This is the query that finds at least the top level missing files
        SqlgQueryBuilder sql = new SqlgQueryBuilder(graph().getSqlDialect())
                .append("SELECT ")
                .maybeWrapInQuotes(options().fileParentKey().get())
                .append(" FROM ")
                .schemaTable(file)
                .forEachAppend(strategies.stream().flatMap(ofType(PartitionStrategy.class)),
                        this::inReadPartitions, ") AND (", " WHERE (", ")")
                .append(" EXCEPT SELECT ")
                .maybeWrapInQuotes(Bdio.DataProperty.path.name()) // TODO Context
                .append("PATH")
                .append(" FROM ")
                .schemaTable(file)
                .forEachAppend(strategies.stream().flatMap(ofType(PartitionStrategy.class)),
                        this::inReadPartitions, ") AND (", " WHERE (", ")")
                .semicolon();

        // We use this query to test for existing parents
        SqlgQueryBuilder parentExistsSql = new SqlgQueryBuilder(graph().getSqlDialect())
                .append("SELECT 1 FROM ")
                .schemaTable(file)
                .append(" WHERE ")
                .maybeWrapInQuotes(Bdio.DataProperty.path.name()) // TODO Context
                .append(" = ?")
                .forEachAppend(strategies.stream().flatMap(ofType(PartitionStrategy.class)),
                        this::inReadPartitions, ") AND (", " AND (", ")")
                .semicolon();

        // Ensure that the properties we will be querying exist
        graph().getTopology().ensureVertexLabelPropertiesExist(file.getSchema(), file.withOutPrefix().getTable(), ImmutableMap.of(
                // TODO Context
                Bdio.DataProperty.path.name(), PropertyType.STRING,
                options().fileParentKey().get(), PropertyType.STRING));

        GraphTraversalSource g = traversal();
        Connection conn = graph().tx().getConnection();
        try (PreparedStatement parentExistsStatement = conn.prepareStatement(parentExistsSql.toString())) {
            try (Statement statement = conn.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery(sql.toString())) {
                    while (resultSet.next()) {
                        String path = resultSet.getString(1);
                        while (path != null) {
                            Optional<String> parentPath = HID.from(path).tryParent().map(HID::toUriString);

                            // TODO Context
                            GraphTraversal<Vertex, Vertex> t = g.addV(Bdio.Class.File.name())
                                    .property(Bdio.DataProperty.path.name(), path)
                                    .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.DIRECTORY.toString());
                            if (parentPath.isPresent()) {
                                t = t.property(options().fileParentKey().get(), parentPath.get());
                            }
                            if (options().identifierKey().isPresent()) {
                                t = t.property(options().identifierKey().get(), BdioObject.randomId());
                            }
                            t.iterate();

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
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void createParentEdges() {
        // TODO Context
        SchemaTable file = SchemaTable.from(graph(), Bdio.Class.File.name()).withPrefix(VERTEX_PREFIX);
        SchemaTable parent = SchemaTable.from(graph(), Bdio.ObjectProperty.parent.name()).withPrefix(EDGE_PREFIX);
        List<TraversalStrategy<?>> strategies = traversal().getStrategies().toList();
        Map<String, Object> traversalProperties = new LinkedHashMap<>();
        getTraversalProperties((k, v) -> traversalProperties.put(k.toString(), v), true);

        // Make sure everything we are about to query is properly constructed
        // TODO Context
        graph().getTopology().ensureEdgeLabelExist(Bdio.ObjectProperty.parent.name(), file.withOutPrefix(), file.withOutPrefix(),
                Maps.transformValues(traversalProperties, PropertyType::from));

        SqlgQueryBuilder sql = new SqlgQueryBuilder(graph().getSqlDialect())
                .append("INSERT INTO ")
                .schemaTable(parent)
                .append(" (")
                .forEachAppend(traversalProperties.keySet().stream(), (k, s) -> s.maybeWrapInQuotes(k), ", ", "", ", ")
                .maybeWrapInQuotes(file.withOutPrefix() + IN_VERTEX_COLUMN_END)
                .append(", ")
                .maybeWrapInQuotes(file.withOutPrefix() + OUT_VERTEX_COLUMN_END)
                .append(")\n  SELECT ")
                .forEachAppend(traversalProperties.values().stream(), (v, s) -> s.valueToValuesString(v), ", ", "", ", ")
                .append(" p.")
                .maybeWrapInQuotes(Topology.ID)
                .append(", c.")
                .maybeWrapInQuotes(Topology.ID)
                .append(" FROM ")
                .schemaTable(file)
                .append(" p JOIN ")
                .schemaTable(file)
                .append(" c ON p.")
                .maybeWrapInQuotes(Bdio.DataProperty.path.name()) // TODO Context
                .append(" = c.")
                .maybeWrapInQuotes(options().fileParentKey().get())
                .forEachAppend(strategies.stream().flatMap(ofType(PartitionStrategy.class)),
                        this::inReadPartitions, ") AND (p.", " AND (p.", ")")
                .forEachAppend(strategies.stream().flatMap(ofType(PartitionStrategy.class)),
                        this::inReadPartitions, ") AND (c.", " AND (c.", ")")
                .semicolon();

        Connection conn = graph().tx().getConnection();
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateFileSystemType(Bdio.FileSystemType fileSystemType) {
        SchemaTable file = SchemaTable.from(graph(), Bdio.Class.File.name()).withPrefix(VERTEX_PREFIX);
        SchemaTable parent = SchemaTable.from(graph(), Bdio.ObjectProperty.parent.name()).withPrefix(EDGE_PREFIX);
        List<TraversalStrategy<?>> strategies = traversal().getStrategies().toList();

        SqlgQueryBuilder sql = new SqlgQueryBuilder(graph().getSqlDialect())
                .append("\nUPDATE\n\t")
                .schemaTable(file)
                .append("\nSET\n\t")
                .maybeWrapInQuotes(Bdio.DataProperty.fileSystemType.name())// TODO Context
                .append(" = ")
                .valueToValuesString(PropertyType.STRING, fileSystemType.toString())
                .append("\nFROM\n\t")
                .schemaTable(parent)
                .append("\nWHERE\n\t")
                .qualify(file.getTable(), Topology.ID)
                .append(" = ")
                .qualify(parent.getTable(), file.withOutPrefix() + IN_VERTEX_COLUMN_END)
                .forEachAppend(strategies.stream().flatMap(ofType(PartitionStrategy.class)),
                        (p, s) -> s.maybeWrapInQuotes(file.getTable()).append('.').in(p.getPartitionKey(), p.getReadPartitions(), PropertyType.STRING),
                        " AND ", " AND ", "")
                .append(" AND (")
                .qualify(file.getTable(), Bdio.DataProperty.fileSystemType.name()) // TODO Context
                .append(" IS NULL OR ")
                .qualify(file.getTable(), Bdio.DataProperty.fileSystemType.name())// TODO Context
                .append(" = ")
                // TODO We should be checking for other types as well
                .valueToValuesString(PropertyType.STRING, Bdio.FileSystemType.REGULAR.toString())
                .append(")");

        if (fileSystemType == Bdio.FileSystemType.DIRECTORY_ARCHIVE) {
            sql.append(" AND (")
                    .qualify(file.getTable(), Bdio.DataProperty.byteCount.name()) // TODO Context
                    .append(" IS NOT NULL OR ")
                    .qualify(file.getTable(), Bdio.DataProperty.contentType.name()) // TODO Context
                    .append(" IS NOT NULL)");
        } else if (fileSystemType != Bdio.FileSystemType.DIRECTORY) {
            throw new IllegalArgumentException("file system type must be DIRECTORY or DIRECTORY_ARCHIVE");
        }
        sql.semicolon();

        // Make sure everything we are about to query is properly constructed
        VertexLabel fileLabel = graph().getTopology().ensureVertexLabelExist(file.getSchema(), file.withOutPrefix().getTable());
        graph().getTopology().ensureEdgeLabelExist(parent.withOutPrefix().getTable(), fileLabel, fileLabel, ImmutableMap.of());
        // TODO Context
        graph().getTopology().ensureVertexLabelPropertiesExist(file.getSchema(), file.withOutPrefix().getTable(), ImmutableMap.of(
                Bdio.DataProperty.fileSystemType.name(), PropertyType.STRING,
                Bdio.DataProperty.byteCount.name(), PropertyType.LONG,
                Bdio.DataProperty.contentType.name(), PropertyType.STRING));

        Connection conn = graph().tx().getConnection();
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void tx(Runnable task) {
        graph().tx().open();
        try {
            task.run();
            graph().tx().commit();
        } catch (RuntimeException | Error e) {
            graph().tx().rollback();
            throw e;
        }
    }

}
