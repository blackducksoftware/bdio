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

import static com.blackducksoftware.bdio2.Bdio.Class.File;
import static com.blackducksoftware.bdio2.Bdio.DataProperty.path;
import static com.blackducksoftware.bdio2.Bdio.ObjectProperty.base;
import static org.apache.tinkerpop.gremlin.process.traversal.P.eq;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.V;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.addV;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.outE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.select;

import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.common.base.HID;
import com.google.common.collect.Iterators;

/**
 * Graph based operations to perform on BDIO data.
 *
 * @author jgustie
 */
public final class BdioOperations {

    /**
     * Internal graph context class.
     */
    private static class OperationsContext extends GraphContext {

        /**
         * Current number of operations performed.
         */
        private final AtomicLong count = new AtomicLong();

        /**
         * The number of operations to perform between commits.
         */
        private final int batchSize = 5000;

        /**
         * Flag indicating that the current graph supports batch mode. Currently implies the graph is an instance of
         * {@code SqlgGraph}.
         */
        private final boolean supportsBatchMode;

        private OperationsContext(BlackDuckIoConfig config, Graph graph) {
            super(config, graph);
            supportsBatchMode = graph instanceof SqlgGraph && ((SqlgGraph) graph).features().supportsBatchMode();
        }

        public void batchModeOn() {
            if (supportsBatchMode) {
                ((SqlgGraph) graph()).tx().normalBatchModeOn();
            }
        }

        public void batchCommitTx() {
            if (supportsBatchMode) {
                if (count.incrementAndGet() % batchSize == 0) {
                    commitTx();
                    batchModeOn();
                }
            } else {
                commitTx();
            }
        }
    }

    /**
     * Property key and edge label used to identify the root project.
     */
    public static final String ROOT_PROJECT = "_rootProject";

    private final OperationsContext context;

    private BdioOperations(OperationsContext context) {
        this.context = Objects.requireNonNull(context);
    }

    public static BdioOperations create(Graph graph, Consumer<BlackDuckIoConfig.Builder> onConfig) {
        BlackDuckIoConfig.Builder builder = BlackDuckIoConfig.build();
        onConfig.accept(builder);
        return new BdioOperations(new OperationsContext(builder.create(), graph));
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
        if (context.config().implicitKey().isPresent()) {
            // Turn on batch mode so we don't try to do everything directly
            context.batchModeOn();

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
    @SuppressWarnings("unchecked") // `coalesce` uses generic varargs
    private void addMissingFileParents() {
        GraphTraversalSource g = context.traversal();

        // TODO Never did figure out how make this recurse properly in the traversal
        boolean hasNewEdges;
        do {
            hasNewEdges = g.V()
                    // Files that missing their parent and are not a project base directory are "orphans"
                    .hasLabel(File.name()).not(outE(Bdio.ObjectProperty.parent.name()).or().inE(base.name())).as("orphanFiles")

                    // Flat map to the file's path to it's parent's path
                    .<String> values(path.name()).flatMap(BdioOperations::parentPath).as("parentPath")

                    // If multiple orphans have the same parent we can only process one at a time
                    // (the dropped orphans will be picked up on the next pass)
                    .dedup()

                    // Find the parent vertex by path, creating it if it does not exist
                    .coalesce(
                            V().hasLabel(File.name()).as("f").values(path.name()).where(eq("parentPath")).select("f"),
                            addMissingParentVertex(select("parentPath")))

                    // Periodically commit
                    .sideEffect(t -> context.batchCommitTx())

                    // Create the parent edge
                    .addE(Bdio.ObjectProperty.parent.name())
                    .from("orphanFiles")
                    .property(context.config().implicitKey().get(), Boolean.TRUE)

                    // Periodically commit
                    .sideEffect(t -> context.batchCommitTx())

                    // If we created any edges, we might need to continue looping
                    .hasNext();
        } while (hasNewEdges);
    }

    /**
     * An anonymous traversal that adds a missing parent vertex.
     */
    private Traversal<?, Vertex> addMissingParentVertex(Object parentPath) {
        GraphTraversal<Object, Vertex> t = addV(File.name())
                .property(path.name(), parentPath)
                .property(context.config().implicitKey().get(), Boolean.TRUE);

        if (context.config().identifierKey().isPresent()) {
            t = t.property(context.config().identifierKey().get(), BdioObject.randomId());
        }

        return t;
    }

    /**
     * This method adds the missing dependency edges between components and the top level project.
     */
    private void addMissingProjectDependencies() {
        GraphTraversalSource g = context.traversal();

        // First we need to find the root project
        Vertex rootProject = g.V()
                .hasLabel(Bdio.Class.Project.name())
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
        context.config().metadataLabel().ifPresent(label -> {
            g.V(rootProject).as("rootProject")
                    .V().hasLabel(label)
                    .addE(ROOT_PROJECT).to("rootProject")
                    .property(context.config().implicitKey().get(), Boolean.TRUE)
                    .iterate();
        });

        // TODO "dependsOn" isn't a real edge yet...
        // g.V(rootProject).as("rootProject")
        // .V().hasLabel(Bdio.Class.Component).not(inE("dependsOn"))
        // .addE("dependsOn").from("rootProject");
    }

    /**
     * Maps a string traverser representing a HID to the parent path. If the HID does not have a parent, an empty
     * iterator is returned; the iterator will never contain more then a single element.
     */
    private static Iterator<String> parentPath(Traverser<String> t) {
        HID parent = HID.from(URI.create(t.get())).getParent();
        if (parent != null) {
            return Iterators.singletonIterator(parent.toUri().toString());
        } else {
            return Collections.emptyIterator();
        }
    }

}
