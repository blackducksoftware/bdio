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

import static com.blackducksoftware.common.base.ExtraThrowables.illegalState;

import java.io.File;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.common.base.HID;
import com.google.common.collect.Iterators;
import com.google.common.io.ByteSource;

/**
 * List contents of files in a tree-like format.
 *
 * @author jgustie
 */
public class TreeTool extends Tool {

    public static void main(String[] args) {
        new TreeTool(null).parseArgs(args).run();
    }

    /**
     * A file in the tree.
     */
    private static class FileNode {

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

        public Iterator<FileNode> children(GraphTraversalSource g) {
            return g.V(vertex)
                    .in(Bdio.ObjectProperty.parent.name())
                    .map(t -> new FileNode(t.get(), depth + 1));
        }
    }

    /**
     * (T)ree (T)ool (T)okens.
     */
    private enum TTT {
        _Metadata(),
        _root(),
    }

    /**
     * The graph tool is used internally to handle loading BDIO into a graph.
     */
    private final GraphTool graphTool;

    public TreeTool(String name) {
        super(name);
        graphTool = new GraphTool(name);
        graphTool.setGraph(TinkerGraph.class.getName());
        graphTool.setProperty("bdio.metadataLabel", TTT._Metadata.name());
        graphTool.setProperty("bdio.rootLabel", TTT._root.name());
        graphTool.onGraphComplete(this::executeWithGraph);
    }

    public void addInput(@Nullable URI id, ByteSource input) {
        graphTool.addInput(id, input);
    }

    public void addInput(File file) {
        graphTool.addInput(file);
    }

    @Override
    public void setVerbosity(Level verbosity) {
        super.setVerbosity(verbosity);
        graphTool.setVerbosity(verbosity);
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        boolean hasInput = false;
        for (String name : arguments(args)) {
            addInput(new File(name).toURI(), getInput(name));
            hasInput = true;
        }
        if (!hasInput) {
            addInput(null, getInput("-"));
        }

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        graphTool.execute();
    }

    /**
     * This will be invoked once the graph is loaded by the graph tool.
     */
    private void executeWithGraph(Graph graph) {
        GraphTraversalSource g = graph.traversal();
        Deque<Iterator<FileNode>> fileNodes = new ArrayDeque<>();
        Set<Integer> childrenAt = new HashSet<>();

        // Find the base file in the graph
        fileNodes.addFirst(g.V().hasLabel(TTT._Metadata.name())
                .out(TTT._root.name())
                .out(Bdio.ObjectProperty.base.name())
                .tryNext()
                .map(FileNode::new)
                .map(Iterators::singletonIterator)
                .orElseThrow(illegalState("No base file found")));

        // Do our pre-order traversal, formatting each file node
        int directoryCount = 0;
        int fileCount = 0;
        while (!fileNodes.isEmpty()) {
            Iterator<FileNode> i = fileNodes.getLast();
            FileNode fn = i.next();
            if (i.hasNext()) {
                childrenAt.add(fn.parentDepth());
            } else {
                childrenAt.remove(fn.parentDepth());
                fileNodes.removeLast();
            }

            Iterator<FileNode> children = fn.children(g);
            if (children.hasNext()) {
                fileNodes.addLast(children);
                directoryCount++;
            } else {
                fileCount++;
            }

            formatFileNode(fn, childrenAt, i.hasNext());
        }

        printOutput("%n%d directories, %d files%n", directoryCount, fileCount);
    }

    /**
     * Performs a simple ASCII format of a row in the tree output.
     */
    private void formatFileNode(FileNode fileNode, Set<Integer> childrenAt, boolean hasNext) {
        StringBuilder rowFormat = new StringBuilder();
        if (fileNode.depth() > 0) {
            for (int i = 0; i < fileNode.depth() - 1; ++i) {
                if (childrenAt.contains(i)) {
                    rowFormat.append('|');
                } else {
                    rowFormat.append(' ');
                }
                rowFormat.append("   ");
            }
            rowFormat.append(hasNext ? '|' : '`').append("-- ");
        }
        printOutput(rowFormat.append("%s%n").toString(), fileNode.name());
    }

}
