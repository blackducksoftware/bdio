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
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.outE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.select;

import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.common.base.HID;
import com.google.common.collect.Iterators;

/**
 * Graph based operations to perform on BDIO data.
 *
 * @author jgustie
 */
public final class BdioOperations {

    // !!! THESE OPERATIONS MUST BE IDEMPOTENT !!!

    private final GraphTraversalSource g;

    private BdioOperations(GraphTraversalSource g) {
        this.g = Objects.requireNonNull(g);
    }

    public static BdioOperations create(GraphTraversalSource g) {
        return new BdioOperations(g);
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
        addMissingFileParents();
    }

    /**
     * This method adds the missing file parent vertices and edges.
     */
    @SuppressWarnings("unchecked") // `coalesce` uses generic varargs
    private void addMissingFileParents() {
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
                            // TODO We need a BDIO compatible identifier for export
                            addV(File.name()).property(path.name(), select("parentPath")))

                    // Create the parent edge
                    .addE(Bdio.ObjectProperty.parent.name()).from("orphanFiles")

                    // If we created any edges, we might need to continue looping
                    .hasNext();
        } while (hasNewEdges);
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
