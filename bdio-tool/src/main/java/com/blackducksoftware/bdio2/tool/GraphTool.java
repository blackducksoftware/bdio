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

import static com.google.common.base.Preconditions.checkState;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.id;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.util.GraphFactory;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.util.SqlgUtil;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIo;
import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;

/**
 * Tool for loading BDIO data into a graph database using the Tinkerpop API.
 *
 * @author jgustie
 */
public class GraphTool extends Tool {

    private static final String DEFAULT_IDENTIFIER_KEY = "_id";

    private static final String DEFAULT_IMPLICIT_KEY = "_implicit";

    private static final String DEFAULT_PARTITION_KEY = "inputSource";

    private static final String DEFAULT_PARTITION = "<stdin>";

    private static final MapSplitter PROPERTY_SPLITTER = Splitter.on(',').limit(1).trimResults().withKeyValueSeparator('=');

    public static void main(String[] args) {
        new GraphTool(null).parseArgs(args).run();
    }

    private Map<URI, ByteSource> inputs = new LinkedHashMap<>();

    private CompositeConfiguration configuration = new CompositeConfiguration();

    private boolean clean;

    private boolean skipInitialization;

    private Consumer<GraphTraversalSource> onGraphLoaded = g -> {};

    private Consumer<GraphTraversalSource> onGraphCompleted = g -> {};

    private Consumer<Graph> onGraphComplete = graph -> {};

    public GraphTool(@Nullable String name) {
        super(name);
    }

    public void addInput(@Nullable URI id, ByteSource input) {
        inputs.put(id, Objects.requireNonNull(input));
    }

    public void addInput(File file) {
        // Use `getInput` to provide extra validation
        addInput(file.toURI(), getInput(file.getPath()));
    }

    public Map<URI, ByteSource> getInputs() {
        return Collections.unmodifiableMap(inputs);
    }

    public void addConfiguration(Configuration config) {
        configuration.addConfiguration(Objects.requireNonNull(config));
    }

    public void setProperty(String key, Object value) {
        configuration.setProperty(key, value);
    }

    public void setGraph(String graph) {
        switch (graph) {
        case "tinkergraph":
            configuration.setProperty(Graph.GRAPH, TinkerGraph.class.getName());
            break;
        case "sqlg":
            configuration.setProperty(Graph.GRAPH, SqlgGraph.class.getName());
            break;
        default:
            configuration.setProperty(Graph.GRAPH, graph);
        }
    }

    public void setClean(boolean clean) {
        this.clean = clean;
    }

    public void setSkipInitialization(boolean skipInitialization) {
        this.skipInitialization = skipInitialization;
    }

    public void onGraphLoaded(Consumer<GraphTraversalSource> listener) {
        onGraphLoaded = onGraphLoaded.andThen(listener);
    }

    public void onGraphCompleted(Consumer<GraphTraversalSource> listener) {
        onGraphCompleted = onGraphCompleted.andThen(listener);
    }

    public void onGraphComplete(Consumer<Graph> listener) {
        onGraphComplete = onGraphComplete.andThen(listener);
    }

    @Override
    protected void printHelp() {
        printOutput("usage: %s [--graph=tinkergraph|sqlg|<class>] [--clean] [--skip-init]%n", name());
        printOutput("          [--config=<file>] [-D=<key>=<value>]%n");
        printOutput("          [--onGraphComplete=dump|<class>]%n%n");
        printOutput("Some common properties are:%n");
        printOutput("   gremlin.tinkergraph.graphFormat - Format used to persist TinkerGraph:%n");
        printOutput("       graphml, graphson, gryo, %s%n", BlackDuckIo.Builder.class.getName());
        printOutput("   gremlin.tinkergraph.graphLocation - File location used to persist TinkerGraph%n");
        printOutput("   jdbc.url - URL of the database (e.g. 'jdbc:postgresql://localhost:5432/bdio')%n");
        printOutput("   jdbc.username - Database username%n");
        printOutput("   jdbc.password - Database password%n");
        printOutput("   bdio.metadataLabel - BDIO metadata vertex label%n");
        printOutput("   bdio.identifierKey - JSON-LD identifier vertex property%n");
        printOutput("   bdio.unknownKey - Vertex property for unknown JSON-LD data%n");
        printOutput("   bdio.implicitKey - Record implicitly created vertices%n");
    }

    @Override
    protected Set<String> optionsWithArgs() {
        return ImmutableSet.of("--graph", "--config", "-D", "--onGraphComplete");
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String arg : options(args)) {
            if (arg.equals("--clean")) {
                setClean(true);
                args = removeFirst(arg, args);
            } else if (arg.equals("--skip-init")) {
                setSkipInitialization(true);
                args = removeFirst(arg, args);
            } else if (arg.startsWith("--graph=")) {
                optionValue(arg).ifPresent(this::setGraph);
                args = removeFirst(arg, args);
            } else if (arg.startsWith("--config=")) {
                String fileName = optionValue(arg).orElse(null);
                if (fileName != null) {
                    addConfiguration(new PropertiesConfiguration(fileName));
                }
                args = removeFirst(arg, args);
            } else if (arg.startsWith("-D=")) {
                optionValue(arg).map(PROPERTY_SPLITTER::split).orElse(Collections.emptyMap())
                        .forEach(this::setProperty);
                args = removeFirst(arg, args);
            } else if (arg.startsWith("--onGraphComplete=")) {
                optionValue(arg).map(GraphTool::listenerForString).ifPresent(this::onGraphComplete);
                args = removeFirst(arg, args);
            }
        }

        for (String name : arguments(args)) {
            addInput(new File(name).toURI(), getInput(name));
        }
        if (inputs.isEmpty()) {
            addInput(null, getInput("-"));
        }

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        checkState(!inputs.isEmpty(), "no inputs");

        Graph graph = openGraph();
        try {
            for (Map.Entry<URI, ByteSource> input : inputs.entrySet()) {
                try (InputStream inputStream = input.getValue().openStream()) {
                    PartitionStrategy partition = partition(input.getKey());
                    BlackDuckIo.Builder bdio = BlackDuckIo.build()
                            .onGraphMapper(builder -> {
                                // Set the JSON-LD context using file extensions
                                setContentType(input.getKey(), builder::forContentType);
                            })
                            .onGraphTopology(builder -> {
                                // Make sure each file goes into it's own partition
                                if (inputs.size() > 1) {
                                    builder.partitionStrategy(partition);
                                }

                                // If the graph does not support user identifiers, ensure we store JSON-LD identifiers
                                if (!graph.features().vertex().supportsUserSuppliedIds()) {
                                    builder.identifierKey(configuration.getString("bdio.identifierKey", DEFAULT_IDENTIFIER_KEY));
                                }

                                // Add an implicit key, otherwise we won't generate implicit edges
                                if (!skipInitialization) {
                                    builder.implicitKey(configuration.getString("bdio.implicitKey", DEFAULT_IMPLICIT_KEY));
                                }
                            });

                    // Import the graph
                    Stopwatch loadGraphTimer = Stopwatch.createStarted();
                    graph.io(bdio).readGraph(inputStream);
                    printDebugMessage("Time to load BDIO graph: %s%n", loadGraphTimer.stop());
                    onGraphLoaded.accept(graph.traversal().withStrategies(partition));

                    // Run the extra operations
                    if (!skipInitialization) {
                        Stopwatch initGraphTimer = Stopwatch.createStarted();
                        graph.io(bdio).applySemanticRules();
                        printDebugMessage("Time to initialize BDIO graph: %s%n", initGraphTimer.stop());
                    }
                    onGraphCompleted.accept(graph.traversal().withStrategies(partition));
                }
            }

            // Before we close the graph, give someone else a chance to play with it
            onGraphComplete.accept(graph);
        } finally {
            graph.close();
        }
    }

    private Graph openGraph() throws Exception {
        Graph graph = GraphFactory.open(configuration);
        if (clean) {
            if (graph instanceof TinkerGraph) {
                ((TinkerGraph) graph).clear();
            } else if (graph instanceof SqlgGraph) {
                SqlgUtil.dropDb((SqlgGraph) graph);
                graph.tx().commit();
                graph.close();
                graph = GraphFactory.open(configuration);
            } else {
                throw new UnsupportedOperationException("unable to clean graph: " + graph.getClass().getSimpleName());
            }
        }
        return graph;
    }

    private PartitionStrategy partition(@Nullable URI inputSource) {
        // Since we are overriding the configuration, at least try to honor the requested partition key
        String partitionKey = configuration.getString("bdio.partitionStrategy.partitionKey", DEFAULT_PARTITION_KEY);
        String partition = Objects.toString(inputSource, DEFAULT_PARTITION);
        return PartitionStrategy.build().partitionKey(partitionKey).writePartition(partition).readPartitions(partition).create();
    }

    private static Consumer<Graph> listenerForString(String listener) {
        switch (listener) {
        case "dump":
            return GraphTool::dump;
        default:
            // TODO Use reflection to create a consumer?
            throw new UnsupportedOperationException("unable to create listener: " + listener);
        }
    }

    /**
     * Sets the content type and expansion context given the supplied identifier.
     */
    public static void setContentType(@Nullable URI id, BiConsumer<Bdio.ContentType, Object> contentType) {
        if (id != null && id.getPath() != null) {
            contentType.accept(Bdio.ContentType.forFileName(id.getPath()), Bdio.Context.DEFAULT.toString());
        }
    }

    /**
     * Helper to construct a base configuration for storing a graph in PostgreSQL.
     * <p>
     *
     * <pre>
     * GraphTool graphTool = new GraphTool("Example");
     * graphTool.addConfiguration(GraphTool.postgresConfig("localhost", 5432, "bdio", "bdio", "bdio"));
     * graphTool.addInput(new File("data/example.bdio"));
     * graphTool.run();
     * </pre>
     */
    public static Configuration postgresConfig(String host, int port, String dbName, String username, CharSequence password) {
        Properties props = new Properties();
        props.setProperty(Graph.GRAPH, SqlgGraph.class.getName());
        props.setProperty("jdbc.url", "jdbc:postgresql://" + host + ":" + port + "/" + dbName);
        props.setProperty("jdbc.username", username);
        props.setProperty("jdbc.password", password.toString());
        return new MapConfiguration(props);
    }

    /**
     * Helper to a graph to standard output.
     */
    // TODO Make this more configurable?
    public static void dump(Graph graph) {
        GraphTraversalSource g = graph.traversal();
        PrintStream out = System.out;

        // TODO This isn't the greatest output, but somewhat useful for small graphs..

        g.V()
                .order().by(id().map(Objects::toString))
                .valueMap(true)
                .forEachRemaining(valueMap -> {
                    // TODO Why was this needed?
                    TreeMap<String, Object> sortedValueMap = new TreeMap<>();
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) valueMap).entrySet()) {
                        sortedValueMap.put(e.getKey().toString(), e.getValue());
                    }

                    out.println("{");
                    sortedValueMap.forEach((k, v) -> out.format("  %s = %s%n", k, v));
                    out.println("}");
                    out.println();
                });

        g.E().forEachRemaining(e -> {
            out.format("[%s] %s --%s--> %s%n", e.id(), e.outVertex().id(), e.label(), e.inVertex().id());
        });
    }

}
