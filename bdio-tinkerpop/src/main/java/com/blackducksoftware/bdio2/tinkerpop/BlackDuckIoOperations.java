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
import static com.blackducksoftware.common.base.ExtraCollectors.enumNames;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.apache.tinkerpop.gremlin.process.traversal.P.eq;
import static org.apache.tinkerpop.gremlin.process.traversal.P.without;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.V;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.has;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.or;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.property;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.select;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.tinkerpop.SqlgGraphReaderWrapper.SqlgAddMissingFileParentsOperation;
import com.blackducksoftware.bdio2.tinkerpop.SqlgGraphReaderWrapper.SqlgImplyFileSystemTypeOperation;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.strategy.SqlgGraphAddPropertyStrategy;
import com.blackducksoftware.common.base.ExtraStreams;
import com.blackducksoftware.common.value.HID;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;

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
        List<Operation> operations = new ArrayList<>();

        // If we have an optimized implementation use that instead
        operations.add(new IdentifyRootOperation(wrapper));
        operations.add(graph instanceof SqlgGraph
                ? new SqlgAddMissingFileParentsOperation(wrapper)
                : new AddMissingFileParentsOperation(wrapper));
        operations.add(new AddMissingProjectDependenciesOperation(wrapper));
        operations.add(graph instanceof SqlgGraph
                ? new SqlgImplyFileSystemTypeOperation(wrapper)
                : new ImplyFileSystemTypeOperation(wrapper));

        operations.forEach(Operation::run);
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
    protected static class IdentifyRootOperation extends Operation {
        public IdentifyRootOperation(GraphReaderWrapper wrapper) {
            super(wrapper, m -> m.rootLabel().isPresent() && m.metadataLabel().isPresent() && m.implicitKey().isPresent());
        }

        @Override
        protected void execute(GraphTraversalSource g, GraphMapper mapper) {
            // Drop any existing root edges
            g.E().hasLabel(mapper.rootLabel().get()).drop().iterate();

            // Get the identifier (technically there should only be one) of the metadata vertex
            Object[] metadataId = g.V().hasLabel(mapper.metadataLabel().get()).id().toList().toArray();

            // Recreate root edges between the root vertices and metadata vertices
            g.V().hasLabel(P.within(ExtraStreams.stream(Bdio.Class.class).filter(Bdio.Class::root).collect(enumNames())))
                    .where(inE(Bdio.ObjectProperty.subproject.name(), Bdio.ObjectProperty.previousVersion.name()).count().is(0))
                    .as("root")
                    .V(metadataId).addE(mapper.rootLabel().get()).to("root")
                    .property(mapper.implicitKey().get(), Boolean.TRUE)
                    .iterate();
        }

    }

    /**
     * Adds parent edges between files based on their path values.
     */
    protected static class AddMissingFileParentsOperation extends Operation {
        public AddMissingFileParentsOperation(GraphReaderWrapper wrapper) {
            super(wrapper, m -> m.implicitKey().isPresent());
        }

        @Override
        protected void execute(GraphTraversalSource g, GraphMapper mapper) {
            // TODO At some point we need to support explicit parent edges...
            g.E().hasLabel(Bdio.ObjectProperty.parent.name()).drop().iterate();

            removeBaseFileParents(g);
            wrapper().commitTx();

            createMissingFiles(g, mapper);
            wrapper().commitTx();

            createParentEdges(g, mapper);
        }

        protected void removeBaseFileParents(GraphTraversalSource g) {
            // Base files must not have a parent property or we will walk right past them to the root
            g.V().out(Bdio.ObjectProperty.base.name()).properties(FILE_PARENT_KEY).drop().iterate();
        }

        protected void createMissingFiles(GraphTraversalSource g, GraphMapper mapper) {
            wrapper().startBatchTx();

            // Loop until we can't find anymore missing files
            long implicitCreation = 1;
            while (implicitCreation > 0) {
                implicitCreation = g.V().hasLabel(Bdio.Class.File.name())
                        .as("f").values(Bdio.DataProperty.path.name()).dedup().aggregate("paths")
                        .select("f").values(FILE_PARENT_KEY).where(without("paths"))
                        .dedup().as("path")
                        .addV(Bdio.Class.File.name())
                        .property(Bdio.DataProperty.path.name(), select("path"))
                        .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.DIRECTORY.toString())
                        .property(mapper.implicitKey().get(), Boolean.TRUE)
                        .sideEffect(t -> {
                            HID.from(t.path("path")).tryParent().map(HID::toUriString).ifPresent(v -> t.get().property(FILE_PARENT_KEY, v));
                            mapper.identifierKey().ifPresent(key -> t.get().property(key, BdioObject.randomId()));
                            wrapper().batchFlushTx();
                        })
                        .count().next();
            }
        }

        protected void createParentEdges(GraphTraversalSource g, GraphMapper mapper) {
            wrapper().startBatchTx();

            // Iterate over all the files with a "_parent" property and create the edge back up to the parent
            g.V()
                    .hasLabel(Bdio.Class.File.name())
                    .as("child").values(FILE_PARENT_KEY).as("pp").select("child")
                    .addE(Bdio.ObjectProperty.parent.name())
                    .to(V().hasLabel(Bdio.Class.File.name())
                            .as("parent").values(Bdio.DataProperty.path.name()).where(eq("pp")).<Vertex> select("parent"))
                    .property(mapper.implicitKey().get(), Boolean.TRUE)
                    .iterate();
        }
    }

    /**
     * Creates dependencies for components which are not otherwise part of the dependency graph.
     */
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
    protected static class ImplyFileSystemTypeOperation extends Operation {
        public ImplyFileSystemTypeOperation(GraphReaderWrapper wrapper) {
            super(wrapper, m -> true);
        }

        @Override
        protected void execute(GraphTraversalSource g, GraphMapper mapper) {
            updateDirectoryTypes(g);
            updateSymlinkTypes(g);
            updateRegularTypes(g);
        }

        @SuppressWarnings("unchecked")
        protected void updateDirectoryTypes(GraphTraversalSource g) {
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

        protected void updateSymlinkTypes(GraphTraversalSource g) {
            g.withStrategies(SqlgGraphAddPropertyStrategy.instance()).V().hasLabel(Bdio.Class.File.name())
                    .hasNot(Bdio.DataProperty.fileSystemType.name())
                    .has(Bdio.DataProperty.linkPath.name())
                    .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.SYMLINK.toString())
                    .iterate();
        }

        protected void updateRegularTypes(GraphTraversalSource g) {
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
    }

}
