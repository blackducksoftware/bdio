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

import static com.blackducksoftware.common.base.ExtraThrowables.illegalState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.has;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.or;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.property;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.sql.dialect.SqlDialect;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.SqlgVertex;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.strategy.SqlgGraphAddPropertyStrategy;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.strategy.SqlgGraphCountStrategy;
import com.blackducksoftware.common.value.HID;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Graph based operations to perform on BDIO data.
 *
 * @author jgustie
 */
public final class BlackDuckIoOperations {

    /**
     * Base class of an operation defined in the specification.
     */
    private static abstract class Operation implements Runnable {

        private final GraphReaderWrapper wrapper;

        private final Predicate<GraphMapper> precondition;

        protected Operation(GraphReaderWrapper wrapper, Predicate<GraphMapper> precondition) {
            this.wrapper = Objects.requireNonNull(wrapper);
            this.precondition = Objects.requireNonNull(precondition);
        }

        @Override
        public final void run() {
            // TODO Shouldn't we be tracking metrics internally on the timings of these steps?
            GraphMapper mapper = wrapper.mapper();
            if (precondition.test(mapper)) {
                try {
                    execute(wrapper.traversal(), mapper);
                    wrapper.commitTx();
                } catch (RuntimeException | Error e) {
                    wrapper.rollbackTx();
                    throw e;
                }
            }
        }

        /**
         * Execute this operation. Use the parameters instead of re-fetching them off the context.
         */
        protected abstract void execute(GraphTraversalSource g, GraphMapper mapper);

        /**
         * Provides direct access to the graph wrapper, generally for transaction management and access to strategy
         * configuration (e.g. partition schemes).
         */
        protected final GraphReaderWrapper wrapper() {
            return wrapper;
        }
    }

    /**
     * Admin graph initializers are package private and are invoked with a wrapper instead of the raw graph.
     */
    static abstract interface AdminGraphInitializer extends GraphInitializer {
        default void initialize(GraphReaderWrapper wrapper) {
            initialize(wrapper.graph());
        }

        @Override
        default void initialize(Graph graph) {
        }
    }

    private final Function<Graph, GraphReaderWrapper> graphWrapper;

    private BlackDuckIoOperations(Builder builder) {
        this.graphWrapper = builder.wrapperFactory::wrapReader;
    }

    /**
     * When possible, initializes the graph's schema and configures indexes. Note that for some implementations this
     * operation does nothing, however it is better to always call this at least once before attempting to perform BDIO
     * operations on a graph.
     */
    public void initializeSchema(Graph graph, GraphInitializer... initializers) {
        GraphReaderWrapper wrapper = graphWrapper.apply(graph);
        new InitializeSchemaOperation(wrapper, Arrays.asList(initializers)).run();
    }

    /**
     * Performs all of the initializations as described in the "Semantic Rules" section of the specification.
     */
    public void applySemanticRules(Graph graph) {
        GraphReaderWrapper wrapper = graphWrapper.apply(graph);
        new IdentifyRootOperation(wrapper).run();
        new AddMissingFileParentsOperation(wrapper).run();
        new AddMissingProjectDependenciesOperation(wrapper).run();
        new ImplyFileSystemTypeOperation(wrapper).run();
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {
        private final GraphIoWrapperFactory wrapperFactory;

        private Builder() {
            wrapperFactory = new GraphIoWrapperFactory();
        }

        public Builder mapper(Mapper<GraphMapper> mapper) {
            wrapperFactory.mapper(mapper::createMapper);
            return this;
        }

        public Builder batchSize(int batchSize) {
            wrapperFactory.batchSize(batchSize);
            return this;
        }

        public Builder addStrategies(Collection<TraversalStrategy<?>> strategies) {
            wrapperFactory.addStrategies(strategies);
            return this;
        }

        public BlackDuckIoOperations create() {
            return new BlackDuckIoOperations(this);
        }
    }

    /**
     * Performs implementation specific schema initialization for BDIO (but <em>not</em> user defined extensions!).
     */
    private static class InitializeSchemaOperation extends Operation {

        private final ImmutableList<GraphInitializer> initializers;

        public InitializeSchemaOperation(GraphReaderWrapper wrapper, List<GraphInitializer> initializers) {
            super(wrapper, m -> true);
            this.initializers = Stream.concat(new SqlgGraphInitializer().stream(), initializers.stream())
                    .sorted((a, b) -> ComparisonChain.start()
                            .compare(a.initializationStep(), b.initializationStep())
                            .compareTrueFirst(a instanceof AdminGraphInitializer, b instanceof AdminGraphInitializer)
                            .result())
                    .collect(toImmutableList());
        }

        @Override
        protected void execute(GraphTraversalSource g, GraphMapper mapper) {
            for (GraphInitializer i : initializers) {
                if (i instanceof AdminGraphInitializer) {
                    ((AdminGraphInitializer) i).initialize(wrapper());
                } else {
                    i.initialize(wrapper().graph());
                }
            }
        }
    }

    /**
     * Identifies the BDIO root and creates an edge from the metadata vertex.
     * <p>
     * Note that is not currently an error to provide multiple roots.
     */
    @VisibleForTesting
    protected static class IdentifyRootOperation extends Operation {
        public IdentifyRootOperation(GraphReaderWrapper wrapper) {
            super(wrapper, m -> m.rootLabel().isPresent() && m.metadataLabel().isPresent() && m.implicitKey().isPresent());
        }

        @Override
        protected void execute(GraphTraversalSource g, GraphMapper mapper) {
            // Drop any existing root edges
            g.E().hasLabel(mapper.rootLabel().get()).drop().iterate();

            // Recreate root edges between the root vertices and metadata vertices
            g.V().hasLabel(Bdio.Class.Project.name(),
                    Bdio.Class.Container.name(),
                    Bdio.Class.Repository.name(),
                    Bdio.Class.FileCollection.name())
                    .not(inE(Bdio.ObjectProperty.subproject.name()))
                    .not(inE(Bdio.ObjectProperty.previousVersion.name()))
                    .as("root")
                    .V().hasLabel(mapper.metadataLabel().get())
                    .addE(mapper.rootLabel().get()).to("root")
                    .property(mapper.implicitKey().get(), Boolean.TRUE)
                    .iterate();
        }

    }

    /**
     * Adds parent edges between files based on their path values.
     */
    @VisibleForTesting
    protected static class AddMissingFileParentsOperation extends Operation {
        public AddMissingFileParentsOperation(GraphReaderWrapper wrapper) {
            super(wrapper, m -> m.implicitKey().isPresent());
        }

        @Override
        protected void execute(GraphTraversalSource g, GraphMapper mapper) {
            // With a database like Sqlg, it's faster to assume we are starting from scratch
            g.E().hasLabel(Bdio.ObjectProperty.parent.name()).drop().iterate();
            wrapper().commitTx();

            // Get the list of base paths
            Set<String> basePaths = g.V()
                    .out(Bdio.ObjectProperty.base.name())
                    .<String> values(Bdio.DataProperty.path.name())
                    .toSet();

            // Index all of the files by path
            // NOTE: This potentially takes a lot of memory as we are loading full files
            // TODO Can we reduce the footprint somehow? We only need ID and path...
            long fileCount = g.withStrategies(SqlgGraphCountStrategy.instance()).V().hasLabel(Bdio.Class.File.name()).count().next();
            Map<String, Vertex> files = Maps.newHashMapWithExpectedSize(Math.toIntExact(fileCount));
            g.V().hasLabel(Bdio.Class.File.name()).has(Bdio.DataProperty.path.name()).forEachRemaining(v -> {
                files.put(v.value(Bdio.DataProperty.path.name()), v);
            });

            // Update the graph
            createMissingFileParentVertices(mapper, files, basePaths);
            createFileParentEdges(mapper, files, basePaths);
        }

        private void createMissingFileParentVertices(GraphMapper mapper, Map<String, Vertex> files, Set<String> basePaths) {
            // Find the missing files using the HID
            Set<String> missingFilePaths = new HashSet<>();
            for (String path : files.keySet()) {
                String parentPath = path;
                while (parentPath != null && !basePaths.contains(parentPath)) {
                    parentPath = HID.from(parentPath)
                            .tryParent()
                            .map(HID::toUriString)
                            .filter(p -> !files.containsKey(p))
                            .filter(missingFilePaths::add)
                            .orElse(null);
                }
            }

            // Create File vertices for all the missing paths
            wrapper().startBatchTx();
            for (String path : missingFilePaths) {
                Stream.Builder<Object> properties = Stream.builder()
                        .add(T.label).add(Bdio.Class.File.name())
                        .add(Bdio.DataProperty.path.name()).add(path)
                        .add(Bdio.DataProperty.fileSystemType.name()).add(Bdio.FileSystemType.DIRECTORY.toString())
                        .add(mapper.implicitKey().get()).add(Boolean.TRUE);
                mapper.identifierKey().ifPresent(key -> properties.add(key).add(BdioObject.randomId()));
                wrapper().forEachPartition((k, v) -> {
                    properties.add(k);
                    properties.add(v);
                });
                files.put(path, wrapper().graph().addVertex(properties.build().toArray()));
                wrapper().batchFlushTx();
            }
            wrapper().commitTx();
        }

        private void createFileParentEdges(GraphMapper mapper, Map<String, Vertex> files, Set<String> basePaths) {
            // Create parent edges
            wrapper().startBatchTx();
            for (Map.Entry<String, Vertex> e : files.entrySet()) {
                Optional<String> parentPath = Optional.of(e.getKey())
                        .filter(p -> !basePaths.contains(p))
                        .map(HID::from)
                        .flatMap(HID::tryParent)
                        .map(HID::toUriString);
                if (parentPath.isPresent()) {
                    Vertex parent = parentPath.map(files::get)
                            .orElseThrow(illegalState("missing parent: %s", e.getKey()));
                    Stream.Builder<Object> properties = Stream.builder()
                            .add(mapper.implicitKey().get()).add(Boolean.TRUE);
                    wrapper().forEachPartition((k, v) -> {
                        properties.add(k);
                        properties.add(v);
                    });
                    e.getValue().addEdge(Bdio.ObjectProperty.parent.name(), parent, properties.build().toArray());
                    wrapper().batchFlushTx();
                }
            }
            wrapper().commitTx();
        }

    }

    /**
     * Creates dependencies for components which are not otherwise part of the dependency graph.
     */
    @VisibleForTesting
    protected static class AddMissingProjectDependenciesOperation extends Operation {
        public AddMissingProjectDependenciesOperation(GraphReaderWrapper wrapper) {
            super(wrapper, m -> m.rootLabel().isPresent() && m.implicitKey().isPresent());
        }

        @Override
        protected void execute(GraphTraversalSource g, GraphMapper mapper) {
            g.E().hasLabel(mapper.rootLabel().get())
                    .inV().as("roots")
                    .V().hasLabel(Bdio.Class.Component.name())
                    .not(inE(Bdio.ObjectProperty.dependsOn.name()))
                    .as("directDependencies")
                    .addV(Bdio.Class.Dependency.name())
                    .property(mapper.implicitKey().get(), Boolean.TRUE)
                    .addE(Bdio.ObjectProperty.dependsOn.name()).to("directDependencies")
                    .property(mapper.implicitKey().get(), Boolean.TRUE)
                    .outV()
                    .addE(Bdio.ObjectProperty.dependency.name()).from("roots")
                    .property(mapper.implicitKey().get(), Boolean.TRUE)
                    .iterate();
        }
    }

    /**
     * Implies the file system type based on the available vertex data.
     */
    @VisibleForTesting
    protected static class ImplyFileSystemTypeOperation extends Operation {
        public ImplyFileSystemTypeOperation(GraphReaderWrapper wrapper) {
            super(wrapper, m -> true);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void execute(GraphTraversalSource g, GraphMapper mapper) {
            if (g.getGraph() instanceof SqlgGraph) {
                SqlgGraph sqlgGraph = (SqlgGraph) g.getGraph();
                updateFileSystemType(sqlgGraph, Bdio.FileSystemType.DIRECTORY_ARCHIVE);
                updateFileSystemType(sqlgGraph, Bdio.FileSystemType.DIRECTORY);
            } else {
                wrapper().startBatchTx();
                g.V().out(Bdio.ObjectProperty.parent.name())
                        .hasNot(Bdio.DataProperty.fileSystemType.name())
                        .coalesce(
                                or(has(Bdio.DataProperty.byteCount.name()), has(Bdio.DataProperty.contentType.name()))
                                        .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.DIRECTORY_ARCHIVE.toString()),
                                property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.DIRECTORY.toString()))
                        .sideEffect(t -> wrapper().batchFlushTx())
                        .iterate();
            }

            g.withStrategies(SqlgGraphAddPropertyStrategy.instance()).V().hasLabel(Bdio.Class.File.name())
                    .hasNot(Bdio.DataProperty.fileSystemType.name())
                    .has(Bdio.DataProperty.linkPath.name())
                    .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.SYMLINK.toString())
                    .iterate();

            g.withStrategies(SqlgGraphAddPropertyStrategy.instance()).V().hasLabel(Bdio.Class.File.name())
                    .hasNot(Bdio.DataProperty.fileSystemType.name())
                    .has(Bdio.DataProperty.encoding.name())
                    .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.REGULAR_TEXT.toString())
                    .iterate();

            g.withStrategies(SqlgGraphAddPropertyStrategy.instance()).V().hasLabel(Bdio.Class.File.name())
                    .hasNot(Bdio.DataProperty.fileSystemType.name())
                    .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.REGULAR.toString())
                    .iterate();
        }

        /**
         * This is a very ugly optimization for a specific case that was extremely expensive to compute in Sqlg.
         */
        private static void updateFileSystemType(SqlgGraph sqlgGraph, Bdio.FileSystemType fileSystemType) {
            SqlDialect dialect = sqlgGraph.getSqlDialect();
            SchemaTable fileTable = SchemaTable.of(dialect.getPublicSchema(), Bdio.Class.File.name()).withPrefix(Topology.VERTEX_PREFIX);
            SchemaTable parentTable = SchemaTable.of(dialect.getPublicSchema(), Bdio.ObjectProperty.parent.name()).withPrefix(Topology.EDGE_PREFIX);

            // Make sure everything we are about to query is properly constructed
            VertexLabel fileLabel = sqlgGraph.getTopology().ensureVertexLabelExist(fileTable.getSchema(), fileTable.withOutPrefix().getTable());
            sqlgGraph.getTopology().ensureEdgeLabelExist(parentTable.withOutPrefix().getTable(), fileLabel, fileLabel, ImmutableMap.of());
            sqlgGraph.getTopology().ensureVertexLabelPropertiesExist(fileTable.getSchema(), fileTable.withOutPrefix().getTable(), ImmutableMap.of(
                    Bdio.DataProperty.fileSystemType.name(), PropertyType.STRING,
                    Bdio.DataProperty.byteCount.name(), PropertyType.LONG,
                    Bdio.DataProperty.contentType.name(), PropertyType.STRING));

            StringBuilder sql = new StringBuilder();
            sql.append("\nUPDATE\n\t")
                    .append(dialect.maybeWrapInQoutes(fileTable.getSchema()))
                    .append('.')
                    .append(dialect.maybeWrapInQoutes(fileTable.getTable()))
                    .append("\nSET\n\t")
                    .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.fileSystemType.name()))
                    .append(" = ")
                    .append(dialect.valueToValuesString(PropertyType.STRING, fileSystemType.toString()))
                    .append("\nFROM\n\t")
                    .append(dialect.maybeWrapInQoutes(parentTable.getSchema()))
                    .append('.')
                    .append(dialect.maybeWrapInQoutes(parentTable.getTable()))
                    .append("\nWHERE\n\t")
                    .append(dialect.maybeWrapInQoutes(fileTable.getTable()))
                    .append('.')
                    .append(dialect.maybeWrapInQoutes(Topology.ID))
                    .append(" = ")
                    .append(dialect.maybeWrapInQoutes(parentTable.getTable()))
                    .append('.')
                    .append(dialect.maybeWrapInQoutes(fileTable.withOutPrefix() + Topology.IN_VERTEX_COLUMN_END));
            if (fileSystemType == Bdio.FileSystemType.DIRECTORY_ARCHIVE) {
                sql.append(" AND (")
                        .append(dialect.maybeWrapInQoutes(fileTable.getTable()))
                        .append(".")
                        .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.byteCount.name()))
                        .append(" IS NOT NULL OR ")
                        .append(dialect.maybeWrapInQoutes(fileTable.getTable()))
                        .append(".")
                        .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.contentType.name()))
                        .append(" IS NOT NULL)");
            } else if (fileSystemType == Bdio.FileSystemType.DIRECTORY) {
                sql.append(" AND ")
                        .append(dialect.maybeWrapInQoutes(fileTable.getTable()))
                        .append(".")
                        .append(dialect.maybeWrapInQoutes(Bdio.DataProperty.fileSystemType.name()))
                        .append(" IS NULL");
            } else {
                throw new IllegalArgumentException("file system type must be DIRECTORY or DIRECTORY_ARCHIVE");
            }
            if (dialect.needsSemicolon()) {
                sql.append(";");
            }

            LoggerFactory.getLogger(SqlgVertex.class).debug("{}", sql);
            Connection conn = sqlgGraph.tx().getConnection();
            try (Statement statement = conn.createStatement()) {
                statement.executeUpdate(sql.toString());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

    }

}
