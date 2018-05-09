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

import static com.blackducksoftware.bdio2.tinkerpop.util.VertexProperties.stringValue;
import static com.blackducksoftware.common.base.ExtraThrowables.illegalState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.common.base.ExtraOptionals;
import com.google.common.collect.Iterators;

/**
 * Prints a dependency tree.
 *
 * @author jgustie
 */
public class DependenciesTool extends AbstractGraphTool {

    public static void main(String[] args) {
        new DependenciesTool(null).parseArgs(args).run();
    }

    /**
     * A dependency in the tree.
     */
    protected static class Dependency {

        private final Vertex component;

        private final Optional<Vertex> dependency;

        private final int depth;

        public Dependency(Vertex component, @Nullable Vertex dependency, int depth) {
            this.component = Objects.requireNonNull(component);
            this.dependency = Optional.ofNullable(dependency);
            this.depth = depth;
        }

        public Dependency(Vertex component) {
            this(component, null, 0);
        }

        public int depth() {
            return depth;
        }

        public int parentDepth() {
            return depth - 1;
        }

        public String id() {
            return component.id().toString();
        }

        public Optional<String> name() {
            return stringValue(component, Bdio.DataProperty.name);
        }

        public Optional<String> version() {
            return stringValue(component, Bdio.DataProperty.version);
        }

        public Optional<String> requestedVersion() {
            return dependency.flatMap(v -> stringValue(v, Bdio.DataProperty.requestedVersion));
        }

        public Optional<String> namespace() {
            return stringValue(component, Bdio.DataProperty.namespace);
        }

        public Optional<String> identifier() {
            return stringValue(component, Bdio.DataProperty.identifier);
        }

        public Iterator<Dependency> children(GraphTraversalSource g, Predicate<String> scopeFilter) {
            return g.V(component)
                    .out(Bdio.ObjectProperty.dependency.name()).as("dependency")
                    .out(Bdio.ObjectProperty.dependsOn.name())
                    .map(t -> new Dependency(t.get(), t.path("dependency"), depth + 1));
        }

        public Optional<String> cleanRequestedVersion() {
            // TODO Use BDNS for this
            Optional<String> requestedVersion = requestedVersion();
            String namespace = namespace().orElse(null);
            if (Objects.equals(namespace, "npm")) {
                return requestedVersion.map(v -> {
                    if (v.startsWith("=v")) {
                        return v.substring(2);
                    } else if (v.startsWith("=") || v.startsWith("v")) {
                        return v.substring(1);
                    } else {
                        return v;
                    }
                });
            } else {
                return requestedVersion;
            }
        }

    }

    /**
     * (D)ependency (T)ool (T)okens.
     */
    private enum DTT {
        _Metadata,
        _root,
    }

    // TODO Options to limit by scope

    private boolean showIdentifiers;

    private boolean showNamespaces;

    public DependenciesTool(String name) {
        super(name);
        graphTool().setProperty("bdio.metadataLabel", DTT._Metadata.name());
        graphTool().setProperty("bdio.rootLabel", DTT._root.name());
    }

    public void setShowIdentifiers(boolean showIdentifiers) {
        this.showIdentifiers = showIdentifiers;
    }

    public void setShowNamespaces(boolean showNamespaces) {
        this.showNamespaces = showNamespaces;
    }

    @Override
    protected void printUsage() {
        printOutput("usage: %s [-in] [file ...]%n", name());
    }

    @Override
    protected void printHelp() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("-i", "Print identifiers for each dependency.");
        options.put("-n", "Include the namespace with the dependency identifier.");
        printOptionHelp(options);
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String arg : options(args)) {
            if (arg.equals("-i")) {
                setShowIdentifiers(true);
                args = removeFirst(arg, args);
            } else if (arg.equals("-n")) {
                setShowNamespaces(true);
                args = removeFirst(arg, args);
            }
        }
        return super.parseArguments(args);
    }

    @Override
    protected void executeWithGraph(Graph graph) {
        GraphTraversalSource g = graph.traversal();
        Deque<Iterator<Dependency>> dependencies = new ArrayDeque<>();
        Set<Integer> childrenAtDepths = new HashSet<>();

        // Find the base file in the graph
        dependencies.addFirst(root(g).map(Iterators::singletonIterator)
                .orElseThrow(illegalState("No base file found")));

        while (!dependencies.isEmpty()) {
            Iterator<Dependency> i = dependencies.getLast();
            Dependency dep = i.next();
            if (i.hasNext()) {
                childrenAtDepths.add(dep.parentDepth());
            } else {
                childrenAtDepths.remove(dep.parentDepth());
                dependencies.removeLast();
            }

            Iterator<Dependency> children = dep.children(g, x -> true);
            if (children.hasNext()) {
                dependencies.addLast(children);
            }

            formatDependency(dep, childrenAtDepths, i.hasNext());
        }
    }

    protected Optional<Dependency> root(GraphTraversalSource g) {
        return g.V().hasLabel(DTT._Metadata.name())
                .out(DTT._root.name())
                .tryNext()
                .map(Dependency::new);
    }

    @SuppressWarnings("OrphanedFormatString")
    private void formatDependency(Dependency dep, Set<Integer> childrenAtDepths, boolean hasMoreSiblings) {
        StringBuilder rowFormat = new StringBuilder();
        List<Object> arguments = new ArrayList<>();

        TreeFormat.appendAsciiIndent(rowFormat, dep.depth(), childrenAtDepths::contains, hasMoreSiblings);

        if (showIdentifiers) {
            // Just the plain identifier
            if (showNamespaces) {
                dep.namespace().ifPresent(namespace -> {
                    rowFormat.append("[%s] ");
                    arguments.add(namespace);
                });
            }
            rowFormat.append("%s");
            arguments.add(dep.identifier().orElse(dep.id()));
        } else {
            // Start with the name, then the ( <version> ) or ( <requestedVersion> " -> " <version> )
            rowFormat.append("%s");
            arguments.add(dep.name().orElse(dep.id()));
            ExtraOptionals.or(dep.version(), dep::cleanRequestedVersion).ifPresent(version -> {
                dep.cleanRequestedVersion()
                        .filter(Predicate.isEqual(version).negate())
                        .ifPresent(requestedVersion -> {
                            rowFormat.append(" %s ->");
                            arguments.add(requestedVersion);
                        });
                rowFormat.append(" %s");
                arguments.add(version);
            });
        }

        printOutput(rowFormat.append("%n").toString(), arguments.toArray());
    }
}
