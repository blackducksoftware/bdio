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
import java.util.EnumSet;
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
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoCore;
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

    static final String DEFAULT_IDENTIFIER_KEY = "_id";

    static final String DEFAULT_IMPLICIT_KEY = "_implicit";

    static final String DEFAULT_PARTITION_KEY = "inputSource";

    static final String DEFAULT_PARTITION = "<stdin>";

    private enum Actions {
        CLEAN, INITIALIZE_SCHEMA, LOAD, APPLY_SEMANTIC_RULES
    }

    private static final MapSplitter PROPERTY_SPLITTER = Splitter.on(',').limit(1).trimResults().withKeyValueSeparator('=');

    public static void main(String[] args) {
        new GraphTool(null).parseArgs(args).run();
    }

    private Map<URI, ByteSource> inputs = new LinkedHashMap<>();

    private CompositeConfiguration configuration = new CompositeConfiguration();

    private EnumSet<Actions> actions = EnumSet.of(Actions.LOAD, Actions.APPLY_SEMANTIC_RULES);

    private Consumer<GraphTraversalSource> onGraphLoaded = g -> {};

    private Consumer<GraphTraversalSource> onGraphInitialized = g -> {};

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
        if (clean) {
            actions.add(Actions.CLEAN);
        } else {
            actions.remove(Actions.CLEAN);
        }
    }

    public void setInitializeSchema(boolean initialize) {
        if (initialize) {
            actions.add(Actions.INITIALIZE_SCHEMA);
        } else {
            actions.remove(Actions.INITIALIZE_SCHEMA);
        }
    }

    public void setSkipLoad(boolean skipLoad) {
        if (skipLoad) {
            actions.remove(Actions.LOAD);
        } else {
            actions.add(Actions.LOAD);
        }
    }

    public void setSkipApplySemanticRules(boolean skipApplySemanticRules) {
        if (skipApplySemanticRules) {
            actions.remove(Actions.APPLY_SEMANTIC_RULES);
        } else {
            actions.add(Actions.APPLY_SEMANTIC_RULES);
        }
    }

    public void onGraphLoaded(Consumer<GraphTraversalSource> listener) {
        onGraphLoaded = onGraphLoaded.andThen(listener);
    }

    public void onGraphInitialized(Consumer<GraphTraversalSource> listener) {
        onGraphInitialized = onGraphInitialized.andThen(listener);
    }

    public void onGraphComplete(Consumer<Graph> listener) {
        onGraphComplete = onGraphComplete.andThen(listener);
    }

    @Override
    protected void printUsage() {
        printOutput("usage: %s [--graph=tinkergraph|sqlg|<class>] [--clean] [--skip-init]%n", name());
        printOutput("          [--config=<file>] [-D=<key>=<value>]%n");
        printOutput("          [--onGraphComplete=dump|<class>]%n%n");
    }

    @Override
    protected void printHelp() {
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
        return ImmutableSet.<String> builder()
                .addAll(graphConfigurationOptionsWithArgs())
                .add("--onGraphComplete")
                .build();
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        args = parseGraphConfigurationArguments(args, this);

        for (String arg : options(args)) {
            if (arg.equals("--clean")) {
                setClean(true);
                args = removeFirst(arg, args);
            } else if (arg.equals("--skip-rules")) {
                setSkipApplySemanticRules(true);
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

    /**
     * This is a helper to configure a graph tool programmatically.
     */
    public static String[] parseGraphConfigurationArguments(String[] args, GraphTool graphTool) throws Exception {
        for (String arg : options(args)) {
            if (arg.startsWith("--graph=")) {
                optionValue(arg).ifPresent(graphTool::setGraph);
                args = removeFirst(arg, args);
            } else if (arg.startsWith("--config=")) {
                String fileName = optionValue(arg).orElse(null);
                if (fileName != null) {
                    graphTool.addConfiguration(new PropertiesConfiguration(fileName));
                }
                args = removeFirst(arg, args);
            } else if (arg.startsWith("-D=")) {
                optionValue(arg).map(PROPERTY_SPLITTER::split)
                        .orElse(Collections.emptyMap())
                        .forEach(graphTool::setProperty);
                args = removeFirst(arg, args);
            }
        }
        return args;
    }

    /**
     * These are the options with arguments handled by {@link #parseGraphConfigurationArguments(String[], GraphTool)}.
     */
    public static Set<String> graphConfigurationOptionsWithArgs() {
        return ImmutableSet.of("--graph", "--config", "-D");
    }

    @Override
    protected void execute() throws Exception {
        checkState(!inputs.isEmpty() || (!actions.contains(Actions.LOAD) && !actions.contains(Actions.APPLY_SEMANTIC_RULES)), "no inputs");

        // Open the graph and update the BDIO specific configuration if necessary
        Graph graph = GraphFactory.open(configuration);
        if (actions.contains(Actions.CLEAN)) {
            cleanGraph(graph);
        }
        if (!graph.features().vertex().supportsUserSuppliedIds() && !configuration.containsKey("bdio.identifierKey")) {
            configuration.setProperty("bdio.identifierKey", DEFAULT_IDENTIFIER_KEY);
        }
        if (actions.contains(Actions.APPLY_SEMANTIC_RULES) && !configuration.containsKey("bdio.implicitKey")) {
            configuration.setProperty("bdio.implicitKey", DEFAULT_IMPLICIT_KEY);
        }
        if (inputs.size() > 1 && !configuration.containsKey("bdio.partitionStrategy.partitionKey")) {
            configuration.setProperty("bdio.partitionStrategy.partitionKey", DEFAULT_PARTITION_KEY);
        }
        if (inputs.size() == 1 && !configuration.containsKey("bdio.partitionStrategy.writePartition")) {
            configuration.setProperty("bdio.partitionStrategy.writePartition", DEFAULT_PARTITION);
        }

        // Create a new BDIO core using the graph's configuration to define tokens
        BlackDuckIoCore bdio = new BlackDuckIoCore(graph).withGraphConfiguration();
        if (actions.contains(Actions.INITIALIZE_SCHEMA)) {
            bdio.initializeSchema();
        }

        try {
            for (Map.Entry<URI, ByteSource> input : inputs.entrySet()) {
                try (InputStream inputStream = input.getValue().openStream()) {
                    // Do bad mutations in the loop...
                    setContentType(input.getKey(), bdio::withContentType);
                    if (inputs.size() > 1) {
                        String writePartition = input.getKey().toString();
                        bdio.withWritePartition(writePartition);
                        configuration.setProperty("bdio.partitionStrategy.writePartition", writePartition);
                    }

                    // Get a traversal for the graph honoring the partition for event listeners
                    GraphTraversalSource g = graph.traversal();
                    if (configuration.containsKey("bdio.partitionStrategy.partitionKey")) {
                        g = g.withStrategies(PartitionStrategy.create(configuration.subset("bdio.partitionStrategy")));
                    }

                    // Import the graph
                    if (actions.contains(Actions.LOAD)) {
                        Stopwatch loadGraphTimer = Stopwatch.createStarted();
                        bdio.readGraph(inputStream);
                        printDebugMessage("Time to load BDIO graph: %s%n", loadGraphTimer.stop());
                        onGraphLoaded.accept(g);
                    }

                    // Run the extra operations
                    if (actions.contains(Actions.APPLY_SEMANTIC_RULES)) {
                        Stopwatch initGraphTimer = Stopwatch.createStarted();
                        bdio.applySemanticRules();
                        printDebugMessage("Time to apply semantic BDIO rules: %s%n", initGraphTimer.stop());
                        onGraphInitialized.accept(g);
                    }
                }
            }

            // Before we close the graph, give someone else a chance to play with it
            onGraphComplete.accept(graph);
        } finally {
            graph.close();
        }
    }

    private void cleanGraph(Graph graph) throws Exception {
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
