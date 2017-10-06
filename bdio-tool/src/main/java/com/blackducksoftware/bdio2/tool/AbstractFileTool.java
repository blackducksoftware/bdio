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
package com.blackducksoftware.bdio2.tool;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.common.base.HID;

/**
 * Base class for tools that want to display file information.
 *
 * @author jgustie
 */
abstract class AbstractFileTool extends AbstractGraphTool {

    /**
     * A file in the tree.
     */
    protected static class FileNode {

        private final Vertex vertex;

        private final int depth;

        public FileNode(Vertex vertex, int depth) {
            this.vertex = Objects.requireNonNull(vertex);
            this.depth = depth;
        }

        public FileNode(Vertex vertex) {
            this(vertex, 0);
        }

        public int depth() {
            return depth;
        }

        public int parentDepth() {
            return depth - 1;
        }

        public String name() {
            return HID.from(vertex.<String> value(Bdio.DataProperty.path.name())).getName();
        }

        public String path() {
            return HID.from(vertex.<String> value(Bdio.DataProperty.path.name())).getPath();
        }

        public long size() {
            return vertex.<Number> property(Bdio.DataProperty.byteCount.name()).orElse(0).longValue();
        }

        public Iterator<FileNode> children(GraphTraversalSource g) {
            return g.V(vertex)
                    .in(Bdio.ObjectProperty.parent.name())
                    .map(t -> new FileNode(t.get(), depth + 1));
        }
    }

    /**
     * (F)ile (T)ool (T)okens.
     */
    private enum FTT {
        _Metadata(),
        _root(),
    }

    public AbstractFileTool(String name) {
        super(name);
        graphTool().setProperty("bdio.metadataLabel", FTT._Metadata.name());
        graphTool().setProperty("bdio.rootLabel", FTT._root.name());
    }

    protected Optional<FileNode> baseFile(GraphTraversalSource g) {
        return g.V().hasLabel(FTT._Metadata.name())
                .out(FTT._root.name())
                .out(Bdio.ObjectProperty.base.name())
                .tryNext()
                .map(FileNode::new);
    }

}
