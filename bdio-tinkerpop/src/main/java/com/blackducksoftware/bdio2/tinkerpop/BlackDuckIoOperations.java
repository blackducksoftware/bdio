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
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.has;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.or;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.property;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.tinkerpop.GraphContextFactory.AbstractContextBuilder;
import com.blackducksoftware.common.value.HID;
import com.google.common.annotations.VisibleForTesting;
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

        private final ReadGraphContext context;

        private final Predicate<GraphTopology> precondition;

        protected Operation(ReadGraphContext context, Predicate<GraphTopology> precondition) {
            this.context = Objects.requireNonNull(context);
            this.precondition = precondition.and(t -> t.implicitKey().isPresent());
        }

        @Override
        public final void run() {
            // TODO Shouldn't we be tracking metrics internally on the timings of these steps?
            GraphTopology topology = context.topology();
            if (precondition.test(topology)) {
                try {
                    execute(context.traversal(), topology);
                    context.commitTx();
                } catch (RuntimeException | Error e) {
                    context.rollbackTx();
                    throw e;
                }
            }
        }

        /**
         * Execute this operation. Use the parameters instead of re-fetching them off the context.
         */
        protected abstract void execute(GraphTraversalSource g, GraphTopology topology);

        /**
         * Hopefully this isn't needed too often.
         */
        protected final ReadGraphContext context() {
            return context;
        }
    }

    /**
     * The graph context factory used to manipulate the graph.
     */
    private final GraphContextFactory contextFactory;

    private BlackDuckIoOperations(Builder builder) {
        contextFactory = builder.contextFactory();
    }

    /**
     * Performs all of the initializations as described in the "Semantic Rules" section of the specification.
     */
    public void applySemanticRules(Graph graph) {
        ReadGraphContext context = contextFactory.forBdioReadingInto(graph);
        new IdentifyRootOperation(context).run();
        new AddMissingFileParentsOperation(context).run();
        new AddMissingProjectDependenciesOperation(context).run();
        new ImplyFileSystemTypeOperation(context).run();
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractContextBuilder<BlackDuckIoOperations, Builder> {
        private Builder() {
            super(BlackDuckIoOperations::new);
        }
    }

    /**
     * Identifies the BDIO root and creates an edge from the metadata vertex.
     * <p>
     * Note that is not currently an error to provide multiple roots.
     */
    @VisibleForTesting
    protected static class IdentifyRootOperation extends Operation {
        public IdentifyRootOperation(ReadGraphContext context) {
            super(context, t -> t.rootLabel().isPresent() && t.metadataLabel().isPresent());
        }

        @Override
        protected void execute(GraphTraversalSource g, GraphTopology topology) {
            // Drop any existing root edges
            g.E().hasLabel(topology.rootLabel().get()).drop().iterate();

            // Recreate root edges between the root vertices and metadata vertices
            g.V().hasLabel(Bdio.Class.Project.name(),
                    Bdio.Class.Container.name(),
                    Bdio.Class.Repository.name(),
                    Bdio.Class.FileCollection.name())
                    .not(inE(Bdio.ObjectProperty.subproject.name()))
                    .not(inE(Bdio.ObjectProperty.previousVersion.name()))
                    .as("root")
                    .V().hasLabel(topology.metadataLabel().get())
                    .addE(topology.rootLabel().get()).to("root")
                    .property(topology.implicitKey().get(), Boolean.TRUE)
                    .iterate();
        }

    }

    /**
     * Adds parent edges between files based on their path values.
     */
    @VisibleForTesting
    protected static class AddMissingFileParentsOperation extends Operation {
        public AddMissingFileParentsOperation(ReadGraphContext context) {
            super(context, m -> true);
        }

        @Override
        protected void execute(GraphTraversalSource g, GraphTopology topology) {
            // With a database like Sqlg, it's faster to assume we are starting from scratch
            g.E().hasLabel(Bdio.ObjectProperty.parent.name()).drop().iterate();
            context().commitTx();

            // Get the list of base paths
            Set<String> basePaths = g.V()
                    .hasLabel(Bdio.Class.Container.name(),
                            Bdio.Class.FileCollection.name(),
                            Bdio.Class.Project.name(),
                            Bdio.Class.Repository.name())
                    .out(Bdio.ObjectProperty.base.name())
                    .<String> values(Bdio.DataProperty.path.name())
                    .toSet();

            // Index all of the files by path
            // NOTE: This potentially takes a lot of memory as we are loading full files
            // TODO Can we reduce the footprint somehow? We only need ID and path...
            int fileCount = Math.toIntExact(context().countVerticesByLabel(Bdio.Class.File.name()));
            Map<String, Vertex> files = Maps.newHashMapWithExpectedSize(fileCount);
            g.V().hasLabel(Bdio.Class.File.name()).has(Bdio.DataProperty.path.name()).forEachRemaining(v -> {
                files.put(v.value(Bdio.DataProperty.path.name()), v);
            });

            // Update the graph
            createMissingFileParentVertices(topology, files, basePaths);
            createFileParentEdges(topology, files, basePaths);
        }

        private void createMissingFileParentVertices(GraphTopology topology, Map<String, Vertex> files, Set<String> basePaths) {
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
            context().startBatchTx();
            for (String path : missingFilePaths) {
                Stream.Builder<Object> properties = Stream.builder()
                        .add(T.label).add(Bdio.Class.File.name())
                        .add(Bdio.DataProperty.path.name()).add(path)
                        .add(Bdio.DataProperty.fileSystemType.name()).add(Bdio.FileSystemType.DIRECTORY.toString())
                        .add(topology.implicitKey().get()).add(Boolean.TRUE);
                topology.identifierKey().ifPresent(key -> properties.add(key).add(BdioObject.randomId()));
                topology.partitionStrategy().ifPresent(p -> properties.add(p.getPartitionKey()).add(p.getWritePartition()));
                files.put(path, context().graph().addVertex(properties.build().toArray()));
                context().batchCommitTx();
            }
            context().commitTx();
        }

        private void createFileParentEdges(GraphTopology topology, Map<String, Vertex> files, Set<String> basePaths) {
            // Create parent edges
            context().startBatchTx();
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
                            .add(topology.implicitKey().get()).add(Boolean.TRUE);
                    topology.partitionStrategy().ifPresent(p -> properties.add(p.getPartitionKey()).add(p.getWritePartition()));
                    e.getValue().addEdge(Bdio.ObjectProperty.parent.name(), parent, properties.build().toArray());
                    context().batchCommitTx();
                }
            }
            context().commitTx();
        }

    }

    /**
     * Creates dependencies for components which are not otherwise part of the dependency graph.
     */
    @VisibleForTesting
    protected static class AddMissingProjectDependenciesOperation extends Operation {
        public AddMissingProjectDependenciesOperation(ReadGraphContext context) {
            super(context, m -> m.rootLabel().isPresent());
        }

        @Override
        protected void execute(GraphTraversalSource g, GraphTopology topology) {
            g.E().hasLabel(topology.rootLabel().get())
                    .inV().as("roots")
                    .V().hasLabel(Bdio.Class.Component.name())
                    .not(inE(Bdio.ObjectProperty.dependsOn.name()))
                    .as("directDependencies")
                    .addV(Bdio.Class.Dependency.name())
                    .property(topology.implicitKey().get(), Boolean.TRUE)
                    .addE(Bdio.ObjectProperty.dependsOn.name()).to("directDependencies")
                    .property(topology.implicitKey().get(), Boolean.TRUE)
                    .outV()
                    .addE(Bdio.ObjectProperty.dependency.name()).from("roots")
                    .property(topology.implicitKey().get(), Boolean.TRUE)
                    .iterate();
        }
    }

    /**
     * Implies the file system type based on the available vertex data.
     */
    @VisibleForTesting
    protected static class ImplyFileSystemTypeOperation extends Operation {
        public ImplyFileSystemTypeOperation(ReadGraphContext context) {
            super(context, m -> true);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void execute(GraphTraversalSource g, GraphTopology topology) {
            context().startBatchTx();

            g.V().out(Bdio.ObjectProperty.parent.name())
                    .hasNot(Bdio.DataProperty.fileSystemType.name())
                    .coalesce(
                            or(has(Bdio.DataProperty.byteCount.name()), has(Bdio.DataProperty.contentType.name()))
                                    .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.DIRECTORY_ARCHIVE.toString()),
                            property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.DIRECTORY.toString()))
                    .sideEffect(t -> context().batchCommitTx())
                    .iterate();

            g.V().hasLabel(Bdio.Class.File.name())
                    .hasNot(Bdio.DataProperty.fileSystemType.name())
                    .coalesce(
                            has(Bdio.DataProperty.linkPath.name())
                                    .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.SYMLINK.toString()),
                            has(Bdio.DataProperty.encoding.name())
                                    .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.REGULAR_TEXT.toString()),
                            property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.REGULAR.toString()))
                    .sideEffect(t -> context().batchCommitTx())
                    .iterate();
        }

    }

}
