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
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.common.base.HID;
import com.google.common.collect.Maps;

/**
 * Graph based operations to perform on BDIO data.
 *
 * @author jgustie
 */
public final class BlackDuckIoOperations {

    /**
     * Property key and edge label used to identify the root project.
     */
    // TODO This should be on the GraphMapper
    public static final String ROOT_PROJECT = "_rootProject";

    private final ReadGraphContext context;

    private BlackDuckIoOperations(ReadGraphContext context) {
        this.context = Objects.requireNonNull(context);
    }

    public static BlackDuckIoOperations create(Graph graph, @Nullable Consumer<GraphMapper.Builder> onGraphMapper) {
        BlackDuckIoMapper mapper = graph.io(BlackDuckIo.build().onGraphMapper(onGraphMapper)).mapper().create();
        ReadGraphContext context;
        if (graph instanceof SqlgGraph) {
            context = new SqlgReadGraphContext((SqlgGraph) graph, mapper.createMapper(), 5000);
        } else {
            context = new ReadGraphContext(graph, mapper.createMapper(), 5000);
        }
        return new BlackDuckIoOperations(context);
    }

    public static BlackDuckIoOperations create(Graph graph) {
        return create(graph, null);
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
    public void addImplicitEdges() {
        if (context.mapper().implicitKey().isPresent()) {
            // Add the implicit edges
            addMissingFileParents();
            addMissingProjectDependencies();

            // Use the normal commit, this ensures everything gets flushed and we don't re-enable batch mode
            context.commitTx();
        }
    }

    /**
     * This method adds the missing file parent vertices and edges.
     */
    private void addMissingFileParents() {
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
        createMissingFileParentVertices(files, basePaths);
        createFileParentEdges(files, basePaths);
    }

    private void createMissingFileParentVertices(Map<String, Vertex> files, Set<String> basePaths) {
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

    private void createFileParentEdges(Map<String, Vertex> files, Set<String> basePaths) {
        // Create parent edges
        context.startBatchTx();
        for (Map.Entry<String, Vertex> e : files.entrySet()) {
            if (!basePaths.contains(e.getKey())) {
                Vertex parent = HID.from(e.getKey()).tryParent()
                        .map(HID::toUriString)
                        .map(files::get)
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
     * This method adds the missing dependency edges between components and the top level project.
     */
    private void addMissingProjectDependencies() {
        GraphTraversalSource g = context.traversal();

        // TODO How does a repository impact the root project calculation?
        // Does the repository itself become the root? And we have "contains" instead of sub-project?

        // First we need to find the root project
        Vertex rootProject = g.V()
                .hasLabel(Bdio.Class.Project.name())
                // TODO What about "previous version" relationships? Need to go to the newest...
                .not(inE(Bdio.ObjectProperty.subproject.name()))
                .limit(1L)
                .as("rootProject")
                // WARNING: This is an arbitrary selection!
                // TODO Can we have a side effect that logs a warning?
                // TODO Should we just bail and not have a root project?

                // Always mark the root project with a property
                .property(ROOT_PROJECT, Boolean.TRUE)
                .next();

        // Create an edge for the root project as well
        context.mapper().metadataLabel().ifPresent(label -> {
            g.V(rootProject).as("rootProject")
                    .V().hasLabel(label)
                    .addE(ROOT_PROJECT).to("rootProject")
                    .property(context.mapper().implicitKey().get(), Boolean.TRUE)
                    .iterate();
        });

        // TODO "dependsOn" isn't a real edge yet...
        // g.V(rootProject).as("rootProject")
        // .V().hasLabel(Bdio.Class.Component).not(inE("dependsOn"))
        // .addE("dependsOn").from("rootProject");
    }

}
