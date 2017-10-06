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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;

import com.google.common.collect.Iterators;

/**
 * List contents of files in a tree-like format.
 *
 * @author jgustie
 */
public class TreeTool extends AbstractFileTool {

    public static void main(String[] args) {
        new TreeTool(null).parseArgs(args).run();
    }

    /**
     * Turn off file/directory count at end of tree listing.
     */
    private boolean noReport;

    /**
     * Print the full path prefix for each file.
     */
    private boolean fullPath;

    /**
     * List directories only.
     */
    private boolean directoriesOnly;

    /**
     * Don't print indentation lines.
     */
    private boolean noIndent;

    /**
     * Print the size in bytes of each file.
     */
    private boolean showSize;

    /**
     * Quote filenames with double quotes.
     */
    private boolean quoteFilenames;

    public TreeTool(String name) {
        super(name);
    }

    public void setNoReport(boolean noReport) {
        this.noReport = noReport;
    }

    public void setFullPath(boolean fullPath) {
        this.fullPath = fullPath;
    }

    public void setDirectoriesOnly(boolean directoriesOnly) {
        this.directoriesOnly = directoriesOnly;
    }

    public void setNoIndent(boolean noIndent) {
        this.noIndent = noIndent;
    }

    public void setShowSize(boolean showSize) {
        this.showSize = showSize;
    }

    public void setQuoteFilenames(boolean quoteFilenames) {
        this.quoteFilenames = quoteFilenames;
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String arg : options(args)) {
            if (arg.equals("--noreport")) {
                setNoReport(true);
                args = removeFirst(arg, args);
            } else if (arg.equals("-f")) {
                setFullPath(true);
                args = removeFirst(arg, args);
            } else if (arg.equals("-d")) {
                setDirectoriesOnly(true);
                args = removeFirst(arg, args);
            } else if (arg.equals("-i")) {
                setNoIndent(true);
                args = removeFirst(arg, args);
            } else if (arg.equals("-s")) {
                setShowSize(true);
                args = removeFirst(arg, args);
            } else if (arg.equals("-Q")) {
                setQuoteFilenames(true);
                args = removeFirst(arg, args);
            }
        }
        return super.parseArguments(args);
    }

    /**
     * This will be invoked once the graph is loaded by the graph tool.
     */
    @Override
    protected void executeWithGraph(Graph graph) {
        GraphTraversalSource g = graph.traversal();
        Deque<Iterator<FileNode>> fileNodes = new ArrayDeque<>();
        Set<Integer> childrenAtDepths = new HashSet<>();

        // Find the base file in the graph
        fileNodes.addFirst(baseFile(g).map(Iterators::singletonIterator)
                .orElseThrow(illegalState("No base file found")));

        // Do our pre-order traversal, formatting each file node
        // TODO Cycle detection when following links?
        int directoryCount = 0;
        int fileCount = 0;
        while (!fileNodes.isEmpty()) {
            Iterator<FileNode> i = fileNodes.getLast();
            FileNode fn = i.next();
            if (i.hasNext()) {
                childrenAtDepths.add(fn.parentDepth());
            } else {
                childrenAtDepths.remove(fn.parentDepth());
                fileNodes.removeLast();
            }

            Iterator<FileNode> children = fn.children(g);
            if (children.hasNext()) {
                fileNodes.addLast(children);
                directoryCount++;
            } else {
                fileCount++;
                if (directoriesOnly) {
                    continue;
                }
            }

            formatFileNode(fn, childrenAtDepths, i.hasNext());
        }

        // Print a summary report
        printReport(directoryCount, fileCount);
    }

    /**
     * Performs a simple ASCII format of a row in the tree output.
     */
    private void formatFileNode(FileNode fileNode, Set<Integer> childrenAtDepths, boolean hasMoreSiblings) {
        StringBuilder rowFormat = new StringBuilder();
        List<Object> arguments = new ArrayList<>();

        if (!noIndent) {
            TreeFormat.appendAsciiIndent(rowFormat, fileNode.depth(), childrenAtDepths::contains, hasMoreSiblings);
        }

        if (showSize) {
            rowFormat.append('[');
            // TODO UID/GID are "%-8s"
            if (showSize) {
                // TODO Human sizes are "%4s" (big K or --si is little k)
                rowFormat.append("%11d");
                arguments.add(fileNode.size());
            }
            rowFormat.append("]  ");
        }

        if (quoteFilenames) {
            rowFormat.append("\"%s\"");
        } else {
            rowFormat.append("%s");
        }
        if (fullPath) {
            arguments.add(fileNode.path());
        } else {
            arguments.add(fileNode.name());
        }

        printOutput(rowFormat.append("%n").toString(), arguments.toArray());
    }

    /**
     * Display the directory and file counts.
     */
    private void printReport(int directoryCount, int fileCount) {
        if (!noReport) {
            printOutput("%n%d directories", directoryCount);
            if (!directoriesOnly) {
                printOutput(", %d files", fileCount);
            }
            printOutput("%n");
        }
    }

}
