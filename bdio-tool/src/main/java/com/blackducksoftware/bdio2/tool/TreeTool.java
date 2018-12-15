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

import static com.blackducksoftware.common.base.ExtraStrings.ensureSuffix;
import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.joining;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.as;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.in;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tinkerpop.gremlin.process.traversal.Scope;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.blackducksoftware.bdio2.Bdio;
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

    // TODO We should support `ls` like output
    // TODO We should allow starting from different points in the tree
    // TODO We should support `find` like syntax for limiting the output
    // TODO dircolors support? e.g. look at $LS_COLORS env...
    // TODO Remember Magpie has fnmatch exclude file matching (not accessible, but...)

    // TODO -p Print the protections for each file.
    // TODO -u Displays file owner or UID number.
    // TODO -g Displays file group owner or GID number.
    // TODO -h Print the size in a more human readable way.
    // TODO --si Like -h, but use in SI units (powers of 1000).

    /**
     * Descend only level directories deep.
     */
    private int level = Integer.MAX_VALUE;

    /**
     * Turn off file/directory count at end of tree listing.
     */
    private boolean noReport;

    /**
     * Print the full path prefix for each file.
     */
    private boolean fullPath;

    /**
     * Classify path names with a suffix indicating their file type.
     */
    private boolean classifiy;

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
     * Print the last modified time of each file;
     */
    private boolean showLastModified;

    /**
     * Quote filenames with double quotes.
     */
    private boolean quoteFilenames;

    public TreeTool(String name) {
        super(name);
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setNoReport(boolean noReport) {
        this.noReport = noReport;
    }

    public void setFullPath(boolean fullPath) {
        this.fullPath = fullPath;
    }

    public void setClassify(boolean classifiy) {
        this.classifiy = classifiy;
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

    public void setShowLastModified(boolean showLastModified) {
        this.showLastModified = showLastModified;
    }

    public void setQuoteFilenames(boolean quoteFilenames) {
        this.quoteFilenames = quoteFilenames;
    }

    @Override
    protected void printUsage() {
        printOutput("usage: %s [-dfisDFQ] [-L level] [--noreport] [file ...]%n", name());
    }

    @Override
    protected void printHelp() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("Listing options", null);
        options.put("-d", "List directories only.");
        options.put("-f", "Print the full path for each file.");
        options.put("-L level", "Descend only level directories deep.");
        options.put("--noreport", "Turn off file/directory count at end of tree listing.");
        options.put("File options", null);
        options.put("-s", "Print the size in bytes of each file.");
        options.put("-D", "Print the date of last modification.");
        options.put("-F", "Appends '/', '=', '@', '%', '|' or '>' as per ls -F.");
        options.put("-Q", "Quote filenames with double quotes.");
        options.put("Graphics options", null);
        options.put("-i", "Don't print indentation lines.");
        printOptionHelp(options);
    }

    @Override
    protected boolean isOptionWithArgs(String option) {
        return super.isOptionWithArgs(option) || option.equals("-L");
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String arg : options(args)) {
            if (arg.startsWith("-L=")) {
                optionValue(arg).map(Integer::valueOf).ifPresent(this::setLevel);
                args = removeFirst(arg, args);
            } else if (arg.equals("--noreport")) {
                setNoReport(true);
                args = removeFirst(arg, args);
            } else if (arg.equals("-f")) {
                setFullPath(true);
                args = removeFirst(arg, args);
            } else if (arg.equals("-F")) {
                setClassify(true);
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
            } else if (arg.equals("-D")) {
                setShowLastModified(true);
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

        // Find the base file(s) in the graph
        baseFiles(g).map(Iterators::singletonIterator).forEach(fileNodes::addFirst);
        checkState(!fileNodes.isEmpty(), "No base file found");
        checkUndeclaredRoot(g);

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
            if (fn.depth() == 0) {
                if (fileCount > 0 || directoryCount > 0) {
                    printOutput("%n");
                }
            } else if (fn.depth() > level) {
                continue;
            }

            Iterator<FileNode> children = fn.children(g, this::sort);
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
     * Compares two file nodes for display in the tree.
     */
    private int sort(FileNode f1, FileNode f2) {
        // TODO Alternate sort orders?
        // -v : Sort the output by version (?)
        // -r : Sort the output in reverse alphabetic order
        // -t : Sort the output by last modification time instead of alphabetically
        // --dirsfirst : List direcotries before files
        return f1.name().compareTo(f2.name());
    }

    /**
     * Performs a simple ASCII format of a row in the tree output.
     */
    @SuppressWarnings("OrphanedFormatString")
    private void formatFileNode(FileNode fileNode, Set<Integer> childrenAtDepths, boolean hasMoreSiblings) {
        StringBuilder rowFormat = new StringBuilder();
        List<Object> arguments = new ArrayList<>();

        if (!noIndent) {
            TreeFormat.appendAsciiIndent(rowFormat, fileNode.depth(), childrenAtDepths::contains, hasMoreSiblings);
        }

        List<String> details = new ArrayList<>();
        // TODO if (showProtections) details.add("%s") // e.g. lrwxrwxrwx
        // TODO if (showUser) details.add("%-8s")
        // TODO if (showGroup) details.add("%-8s")
        if (showSize) {
            // TODO Human sizes are "%4s" (big K or --si is little k)
            details.add("%11d");
            arguments.add(fileNode.size());
        }
        if (showLastModified) {
            ZonedDateTime lastModified = fileNode.lastModified().orElse(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC));
            details.add("%tb %2te");
            arguments.add(lastModified);
            arguments.add(lastModified);
            if (lastModified.getYear() == ZonedDateTime.now().getYear()) {
                details.add("%2tk:%tM");
                arguments.add(lastModified);
                arguments.add(lastModified);
            } else {
                details.add(" %tY");
                arguments.add(lastModified);
            }
        }
        if (!details.isEmpty()) {
            rowFormat.append(details.stream().collect(joining(" ", "[", "]  ")));
        }

        if (quoteFilenames) {
            rowFormat.append("\"%s\"");
        } else {
            rowFormat.append("%s");
        }
        String pathname = fullPath ? fileNode.path() : fileNode.name();
        if (classifiy) {
            arguments.add(classify(pathname, fileNode.type()));
        } else {
            arguments.add(pathname);
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

    /**
     * Print a warning if there are files that do not have parents and aren't declared as a base file.
     */
    private void checkUndeclaredRoot(GraphTraversalSource g) {
        g.V()
                .hasLabel(Bdio.Class.File.name())
                .has(Bdio.DataProperty.path.name())
                .match(as("file").outE(Bdio.ObjectProperty.parent.name()).count().is(0),
                        as("file").inE(Bdio.ObjectProperty.base.name()).count().is(0))
                .<Vertex> select("file")
                .emit().repeat(in(Bdio.ObjectProperty.parent.name()))
                .hasNot(GraphTool.DEFAULT_IMPLICIT_KEY)
                .limit(Scope.local, 1)
                .sideEffect(t -> {
                    String basePath = ((Vertex) t.path("file")).value(Bdio.DataProperty.path.name());
                    String viaPath = ((Vertex) t.get()).value(Bdio.DataProperty.path.name());
                    printMessage("WARNING: found undeclared base '%s' (via. path '%s')%n", basePath, viaPath);
                })
                .iterate();
    }

    /**
     * Returns the classified pathname given a file system type.
     */
    private static String classify(String pathname, Bdio.FileSystemType fileSystemType) {
        // TODO How do we do "*" for executable?
        switch (fileSystemType) {
        case DIRECTORY:
            return ensureSuffix(pathname, "/");
        case SYMLINK:
            return pathname + '@';
        case OTHER_SOCKET:
            return pathname + '=';
        case OTHER_WHITEOUT:
            return pathname + '%';
        case OTHER_PIPE:
            return pathname + '|';
        case OTHER_DOOR:
            return pathname + '>';
        default:
            return pathname;
        }
    }

}
