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
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inE;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.tinkerpop.GraphContextFactory.AbstractContextBuilder;
import com.blackducksoftware.common.base.HID;
import com.google.common.collect.Maps;

/**
 * Graph based operations to perform on BDIO data.
 *
 * @author jgustie
 */
public final class BlackDuckIoOperations {

    private final GraphContextFactory contextFactory;

    private BlackDuckIoOperations(Builder builder) {
        contextFactory = builder.contextFactory();
    }

    /**
     * Adds implicit edges to the BDIO graph. Implicit edges are those relationships which are not required to be
     * serialized in the BDIO data and include:
     * <ul>
     * <li>File hierarchy parent/child relationships (inferred by the file paths)</li>
     * <li>Project component dependencies (unused components are assumed to be dependencies of the top level
     * project)</li>
     * </ul>
     */
    public void addImplicitEdges(Graph graph) {
        ReadGraphContext context = contextFactory.forBdioReadingInto(graph);
        if (context.mapper().implicitKey().isPresent()) {
            if (context.mapper().rootLabel().isPresent()) {
                addMissingRoot(context);
            }

            addMissingFileParents(context);

            addMissingProjectDependencies(context);

            // Use the normal commit, this ensures everything gets flushed and we don't re-enable batch mode
            context.commitTx();
        }
    }

    /**
     * This method adds the missing file parent vertices and edges.
     */
    private void addMissingFileParents(ReadGraphContext context) {
        GraphTraversalSource g = context.traversal();

        // With a database like Sqlg, it's faster to assume we are starting from scratch
        g.E().hasLabel(Bdio.ObjectProperty.parent.name()).drop().iterate();
        context.commitTx();

        // Get the list of base paths
        Set<String> basePaths = g.V()
                .hasLabel(Bdio.Class.Project.name())
                .out(Bdio.ObjectProperty.base.name())
                .<String> values(Bdio.DataProperty.path.name())
                .toSet();

        // Index all of the files by path
        // NOTE: This potentially takes a lot of memory as we are loading full files
        // TODO Can we reduce the footprint somehow? We only need ID and path...
        int fileCount = Math.toIntExact(context.countVerticesByLabel(Bdio.Class.File.name()));
        Map<String, Vertex> files = Maps.newHashMapWithExpectedSize(fileCount);
        g.V().hasLabel(Bdio.Class.File.name()).forEachRemaining(v -> {
            files.put(v.value(Bdio.DataProperty.path.name()), v);
        });

        // Update the graph
        createMissingFileParentVertices(context, files, basePaths);
        createFileParentEdges(context, files, basePaths);
    }

    private void createMissingFileParentVertices(ReadGraphContext context, Map<String, Vertex> files, Set<String> basePaths) {
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
        context.startBatchTx();
        for (String path : missingFilePaths) {
            Stream.Builder<Object> properties = Stream.builder()
                    .add(T.label).add(Bdio.Class.File.name())
                    .add(Bdio.DataProperty.path.name()).add(path)
                    .add(context.mapper().implicitKey().get()).add(Boolean.TRUE);
            context.mapper().identifierKey().ifPresent(key -> properties.add(key).add(BdioObject.randomId()));
            context.mapper().partitionStrategy().ifPresent(p -> properties.add(p.getPartitionKey()).add(p.getWritePartition()));
            files.put(path, context.graph().addVertex(properties.build().toArray()));
            context.batchCommitTx();
        }
        context.commitTx();
    }

    private void createFileParentEdges(ReadGraphContext context, Map<String, Vertex> files, Set<String> basePaths) {
        // Create parent edges
        context.startBatchTx();
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
                        .add(context.mapper().implicitKey().get()).add(Boolean.TRUE);
                context.mapper().partitionStrategy().ifPresent(p -> properties.add(p.getPartitionKey()).add(p.getWritePartition()));
                e.getValue().addEdge(Bdio.ObjectProperty.parent.name(), parent, properties.build().toArray());
                context.batchCommitTx();
            }
        }
        context.commitTx();
    }

    /**
     * This method adds the missing edge between metadata and the root project.
     */
    private void addMissingRoot(ReadGraphContext context) {
        GraphTraversalSource g = context.traversal();

        // Drop any existing root edges
        g.E().hasLabel(context.mapper().rootLabel().get()).drop().iterate();

        // Try to find the root project
        Optional<Vertex> root = g.V()
                .hasLabel(Bdio.Class.Project.name())
                .not(inE(Bdio.ObjectProperty.subproject.name()))
                .not(inE(Bdio.ObjectProperty.previousVersion.name()))

                // WARNING: This is an arbitrary selection!
                // TODO Can we have a side effect that logs a warning?
                // TODO Should we just bail?
                .limit(1L)
                .tryNext();

        // Try to find the root Repository
        if (!root.isPresent()) {
            root = g.V()
                    .hasLabel(Bdio.Class.Repository.name())

                    // WARNING: This is an arbitrary selection!
                    // TODO Can we have a side effect that logs a warning?
                    // TODO Should we just bail?
                    .limit(1L)
                    .tryNext();
        }

        // Create the edge between metadata and the root (creating a root if one does not exist)
        g.V(root.orElseGet(() -> createImplicitRootVertex(context))).as("root")
                .V().hasLabel(context.mapper().metadataLabel().get())
                .addE(context.mapper().rootLabel().get()).to("root")
                .property(context.mapper().implicitKey().get(), Boolean.TRUE)
                .iterate();

        context.commitTx();
    }

    /**
     * Creates a new root vertex to use in the case that one could not be found.
     */
    private Vertex createImplicitRootVertex(ReadGraphContext context) {
        Vertex implicitRoot = context.graph().addVertex(Bdio.Class.Project.name());

        implicitRoot.property(context.mapper().implicitKey().get(), Boolean.TRUE);

        return implicitRoot;
    }

    /**
     * This method adds the missing dependency edges between components and the top level project.
     */
    private void addMissingProjectDependencies(ReadGraphContext context) {
        // TODO "dependsOn" isn't a real edge yet...
        // g.V(rootProject).as("rootProject")
        // .V().hasLabel(Bdio.Class.Component).not(inE("dependsOn"))
        // .addE("dependsOn").from("rootProject");
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

}
