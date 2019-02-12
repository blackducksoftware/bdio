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

import static com.blackducksoftware.common.base.ExtraEnums.set;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.repeat;
import static java.util.Collections.singleton;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.count;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.id;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.GraphFactory;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.flywaydb.core.Flyway;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIo;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoReadGraphException;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.flyway.BdioCallback;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.flyway.FlywayBackport;
import com.blackducksoftware.bdio2.tinkerpop.strategy.PropertyConstantStrategy;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ComparisonChain;
import com.google.common.io.ByteSource;

/**
 * Tool for loading BDIO data into a graph database using the Tinkerpop API.
 *
 * @author jgustie
 */
public class GraphTool extends Tool {

    public static void main(String[] args) {
        new GraphTool(null).parseArgs(args).run();
    }

    /**
     * Life-cycle listener for the graph.
     */
    public interface GraphListener {
        /**
         * Invoked after raw data has been loaded into the graph.
         */
        default void onGraphLoaded(GraphTraversalSource traversal) {
        };

        /**
         * Invoked after normalization of the data has completed.
         */
        default void onGraphInitialized(GraphTraversalSource traversal) {
        };

        /**
         * Invoked once the graph is in it's final state.
         */
        default void onGraphComplete(Graph graph) {
        };
    }

    /**
     * Used as the internal graph listener, just delegates to other listeners.
     */
    private static final class GraphToolGraphListener implements GraphListener {
        private final List<GraphListener> delegates = new CopyOnWriteArrayList<>();

        @Override
        public void onGraphLoaded(GraphTraversalSource traversal) {
            delegates.forEach(l -> l.onGraphLoaded(traversal));
        }

        @Override
        public void onGraphInitialized(GraphTraversalSource traversal) {
            delegates.forEach(l -> l.onGraphInitialized(traversal));
        }

        @Override
        public void onGraphComplete(Graph graph) {
            delegates.forEach(l -> l.onGraphComplete(graph));
        }

        public void add(GraphListener listener) {
            delegates.add(Objects.requireNonNull(listener));
        }

        public void add(Consumer<Graph> graphCompleted) {
            add(new GraphListener() {
                @Override
                public void onGraphComplete(Graph graph) {
                    graphCompleted.accept(graph);
                }
            });
        }
    }

    public static final String COMMON_GRAPH_TOOL_OPTIONS = "Common graph tool options";

    static final String DEFAULT_IDENTIFIER_KEY = "__id";

    static final String DEFAULT_IMPLICIT_KEY = "implicit";

    static final String DEFAULT_PARTITION_KEY = "inputSource";

    static final String DEFAULT_PARTITION = "<stdin>";

    private enum Action {
        CLEAN, INITIALIZE_SCHEMA, LOAD, NORMALIZE
    }

    private static final MapSplitter PROPERTY_SPLITTER = Splitter.on(',').limit(1).trimResults().withKeyValueSeparator('=');

    private Map<URI, ByteSource> inputs = new LinkedHashMap<>();

    private CompositeConfiguration configuration = new CompositeConfiguration();

    private Object expandContext;

    private Set<Action> actions = EnumSet.of(Action.LOAD, Action.NORMALIZE);

    private GraphToolGraphListener listener = new GraphToolGraphListener();

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
            setProperty(Graph.GRAPH, TinkerGraph.class.getName());
            break;
        case "sqlg":
            setProperty(Graph.GRAPH, SqlgGraph.class.getName());
            break;
        default:
            setProperty(Graph.GRAPH, graph);
        }
    }

    public void setExpandContext(Object expandContext) {
        if (Objects.equals(expandContext, "bdio")) {
            this.expandContext = Bdio.Context.DEFAULT;
        } else {
            this.expandContext = Objects.requireNonNull(expandContext);
        }
    }

    public void setClean(boolean clean) {
        set(actions, Action.CLEAN, clean);
    }

    public void setInitializeSchema(boolean initializeSchema) {
        set(actions, Action.INITIALIZE_SCHEMA, initializeSchema);
    }

    public void setSkipLoad(boolean skipLoad) {
        set(actions, Action.LOAD, !skipLoad);
    }

    public void setSkipApplySemanticRules(boolean skipApplySemanticRules) {
        set(actions, Action.NORMALIZE, !skipApplySemanticRules);
    }

    public void setGraphListener(GraphListener listener) {
        this.listener.add(listener);
    }

    public void onGraphComplete(Consumer<Graph> graphCompleted) {
        this.listener.add(graphCompleted);
    }

    public void onGraphComplete(String listener) {
        switch (listener) {
        case "dump":
            onGraphComplete(GraphTool::dump);
            return;
        case "summary":
            onGraphComplete(GraphTool::summary);
            return;
        case "write":
            onGraphComplete(this::write);
            return;
        default:
            try {
                setGraphListener(Class.forName(listener).asSubclass(GraphListener.class).getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("unable to create graph listener: " + listener, e);
            }
        }
    }

    @Override
    protected void printUsage() {
        printOutput("usage: %s [--graph=tinkergraph|sqlg|<class>]%n", name());
        printOutput("          [--config=<file>] [-D=<key>=<value>]%n");
        printOutput("          [--clean] [--skip-rules] [--init-schema]%n");
        printOutput("          [--onGraphComplete=dump|summary|write|<class>]%n");
        printOutput("          [--context=bdio|<uri>]%n");
        printOutput("%n");
    }

    @Override
    protected void printHelp() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(COMMON_GRAPH_TOOL_OPTIONS, null);
        options.put("--graph=<factory>", "Graph implementation factory (default: tinkergraph)");
        options.put("--config=<file>", "Graph configuration file (see properties below)");
        options.put("-D=<key>=<value>", "Single property graph configuration (see properties below)");
        options.put("--context=<ctx>", "JSON-LD context used to expand JSON inputs");

        options.put("Graph tool options", null);
        options.put("--clean", "Wipe the graph contents before starting");
        options.put("--skip-rules", "Skip application of BDIO normalization rules");
        options.put("--init-schema", "Initialize using the full BDIO schema");
        options.put("--onGraphComplete", "Register an onGraphComplete listener (can be applied multiple times)");

        options.put("Frequently used properties", null);
        options.put("gremlin.tinkergraph.graphFormat",
                "Format used to persist TinkerGraph (graphml, graphson, gryo, " + BlackDuckIo.Builder.class.getName() + ")");
        options.put("gremlin.tinkergraph.graphLocation", "File location used to persist TinkerGraph");
        options.put("jdbc.url", "URL of the database (e.g. 'jdbc:postgresql://localhost:5432/bdio')");
        options.put("jdbc.username", "Database username");
        options.put("jdbc.password", "Database password");
        options.put("bdio.metadataLabel", "BDIO metadata vertex label");
        options.put("bdio.identifierKey", "JSON-LD identifier vertex property");
        options.put("bdio.unknownKey", "Vertex property for unknown JSON-LD data");
        printOptionHelp(options);
    }

    @Override
    protected String formatException(Throwable failure) {
        String result = super.formatException(failure);

        if (failure instanceof BlackDuckIoReadGraphException && failure.getCause() instanceof NodeDoesNotExistException) {
            Object missingNodeIdentifier = ((NodeDoesNotExistException) failure.getCause()).getMissingNodeIdentifier();
            if (missingNodeIdentifier instanceof Map<?, ?> && ((Map<?, ?>) missingNodeIdentifier).containsKey(JsonLdConsts.ID)) {
                missingNodeIdentifier = ((Map<?, ?>) missingNodeIdentifier).get(JsonLdConsts.ID);
            }
            result += ", unable to find '" + missingNodeIdentifier + "'";
        }

        return result;
    }

    /**
     * These are the options with arguments handled by {@link #parseGraphConfigurationArguments(String[], GraphTool)}.
     */
    public static boolean isGraphConfigurationOptionWithArgs(String option) {
        return option.equals("--graph") || option.equals("--config") || option.equals("-D") || option.equals("--context");
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
            } else if (arg.startsWith("--context=")) {
                optionValue(arg).ifPresent(graphTool::setExpandContext);
                args = removeFirst(arg, args);
            }
        }
        return args;
    }

    @Override
    protected boolean isOptionWithArgs(String option) {
        return super.isOptionWithArgs(option) || isGraphConfigurationOptionWithArgs(option) || option.equals("--onGraphComplete");
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
            } else if (arg.equals("--init-schema")) {
                setInitializeSchema(true);
                args = removeFirst(arg, args);
            } else if (arg.startsWith("--onGraphComplete=")) {
                optionValue(arg).ifPresent(this::onGraphComplete);
                args = removeFirst(arg, args);
            }
        }

        for (String name : arguments(args)) {
            addInput(new File(name).toURI(), getInput(name));
        }
        if (inputs.isEmpty()) {
            addInput(null, getInput("-"));
        }
        if (!configuration.containsKey(Graph.GRAPH)) {
            setGraph(TinkerGraph.class.getName());
        }

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        checkState(!inputs.isEmpty() || (!actions.contains(Action.LOAD) && !actions.contains(Action.NORMALIZE)), "no inputs");
        Graph graph = GraphFactory.open(configuration);

        // If the graph does not support user identifiers, configure an extra property to hold them
        if (!graph.features().vertex().supportsUserSuppliedIds() && !configuration.containsKey("bdio.identifierKey")) {
            configuration.setProperty("bdio.identifierKey", DEFAULT_IDENTIFIER_KEY);
        }

        // Apply clean up and initialization (the graph may need to be re-instantiated)
        if (actions.contains(Action.CLEAN) || actions.contains(Action.INITIALIZE_SCHEMA)) {
            graph = cleanGraph(graph);
        }

        try {
            // Use the BDIO configuration logic shared with schema initialization
            BlackDuckIo bdio = graph.io(configureBdio(BlackDuckIo.build()));
            for (Map.Entry<URI, ByteSource> input : inputs.entrySet()) {
                try (InputStream inputStream = input.getValue().openStream()) {
                    // Load the graph
                    if (actions.contains(Action.LOAD)) {
                        load(graph, bdio, input.getKey(), inputStream);
                    }

                    // Run the extra operations
                    if (actions.contains(Action.NORMALIZE)) {
                        normalize(graph, bdio, input.getKey());
                    }
                }
            }

            // Before we close the graph, give someone else a chance to play with it
            listener.onGraphComplete(graph);
        } finally {
            graph.close();
        }
    }

    protected Graph cleanGraph(Graph graph) throws Exception {
        Stopwatch timer = Stopwatch.createStarted();
        Graph result = graph;
        if (graph instanceof TinkerGraph) {
            // Directly manipulate the TinkerGraph
            TinkerGraph tinkerGraph = (TinkerGraph) graph;
            if (actions.contains(Action.CLEAN)) {
                tinkerGraph.clear();
            }
            if (actions.contains(Action.INITIALIZE_SCHEMA)) {
                tinkerGraph.createIndex(Bdio.DataProperty.path.name(), Vertex.class);
            }
        } else if (graph instanceof SqlgGraph) {
            // Use Flyway for Sqlg
            Flyway flyway = FlywayBackport.configure()
                    .dataSource(((SqlgGraph) graph).getSqlgDataSource().getDatasource())
                    .callbacks(BdioCallback.create(this::configureBdio, getTraversalStrategies(Action.INITIALIZE_SCHEMA, null)))
                    .locations("classpath:com/blackducksoftware/bdio2/tinkerpop/sqlg/flyway")
                    .load();
            if (actions.contains(Action.CLEAN)) {
                flyway.clean();
            }
            if (actions.contains(Action.INITIALIZE_SCHEMA)) {
                flyway.migrate();
            }
            graph.close();
            result = GraphFactory.open(configuration);
        } else {
            // We would need specialized support for this, better to fail the silently ignore (just don't clean!)
            throw new UnsupportedOperationException("unable to clean graph: " + graph.getClass().getSimpleName());
        }
        printDebugMessage("Time to clean BDIO graph: %s%n", timer.stop());
        return result;
    }

    protected void load(Graph graph, BlackDuckIo bdio, URI id, InputStream inputStream) throws IOException {
        TraversalStrategy<?>[] strategies = getTraversalStrategies(Action.LOAD, id);

        Stopwatch timer = Stopwatch.createStarted();
        bdio.readGraph(inputStream, getBase(id), getExpandContext(id), strategies);
        printDebugMessage("Time to load BDIO graph: %s%n", timer.stop());
        listener.onGraphLoaded(graph.traversal().withStrategies(strategies));
    }

    protected void normalize(Graph graph, BlackDuckIo bdio, URI id) {
        TraversalStrategy<?>[] strategies = getTraversalStrategies(Action.NORMALIZE, id);

        Stopwatch timer = Stopwatch.createStarted();
        bdio.normalize(strategies);
        printDebugMessage("Time to normalize BDIO graph: %s%n", timer.stop());
        listener.onGraphInitialized(graph.traversal().withStrategies(strategies));
    }

    protected BlackDuckIo.Builder configureBdio(BlackDuckIo.Builder builder) {
        // Currently do nothing, just take the defaults
        return builder;
    }

    @Nullable
    protected String getBase(@Nullable URI id) {
        // TODO Should we return the id?
        // If so, we should probably adjust the base URL of the BdioFrame appropriately
        return null;
    }

    @Nullable
    protected Object getExpandContext(@Nullable URI id) {
        if (id != null && id.getPath() != null) {
            try {
                // If we have a path to look at, always return a context
                Object context = Bdio.ContentType.forFileName(id.getPath());
                if (context == Bdio.ContentType.JSON) {
                    if (expandContext == null) {
                        // Modify the expand context so we only print this message once
                        printDebugMessage("Using default BDIO context for JSON%n");
                        expandContext = Bdio.Context.DEFAULT;
                    }
                    context = expandContext;
                }
                return context;
            } catch (IllegalArgumentException e) {
                // This just means we couldn't imply the content type
                if (expandContext == null) {
                    printMessage("Unable to determine the input content type, you may need to specify the JSON-LD context using the --context option%n");
                }
            }
        }

        // Fall back to the value of the --context argument
        return expandContext;
    }

    protected TraversalStrategy<?>[] getTraversalStrategies(Action action, @Nullable URI id) {
        List<TraversalStrategy<?>> result = new ArrayList<>(3);

        // Only partition if there is one input and a configured key or there are more then one inputs
        if (!inputs.isEmpty() && (inputs.size() > 1 || configuration.containsKey("bdio.partitionStrategy.partitionKey"))) {
            // TODO This whole thing seems off now that we have property constants...

            // Isolate the input(s) by creating a partition for the input identifier (`null` coming from stdin)
            String key = configuration.getString("bdio.partitionStrategy.partitionKey", DEFAULT_PARTITION_KEY);
            String write = Optional.ofNullable(id).map(URI::toString).orElse(DEFAULT_PARTITION);
            List<String> read = new ArrayList<>(singleton(write));

            // The write partition is still configurable if there is only one input
            if (inputs.size() == 1) {
                write = configuration.getString("bdio.partitionStrategy.writePartition", write);
                if (!read.contains(write)) {
                    read.add(write); // If the value changed, be sure to include it
                }
            }

            result.add(PartitionStrategy.build().partitionKey(key).writePartition(write).readPartitions(read).create());
        }

        // If we are performing normalization add a constant
        if (actions.contains(Action.NORMALIZE) && (action == Action.INITIALIZE_SCHEMA || action == Action.NORMALIZE)) {
            // Add a boolean property constant indicating the row was added during normalization
            String key = configuration.getString("bdio.implicitKey", DEFAULT_IMPLICIT_KEY);
            Object value = Boolean.TRUE;

            result.add(PropertyConstantStrategy.build().addProperty(key, value).create());
        }

        return result.toArray(new TraversalStrategy<?>[result.size()]);
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
     * Helper to dump a graph to standard output.
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
                    TreeMap<String, Object> sortedValueMap = new TreeMap<>((s1, s2) -> ComparisonChain.start()
                            .compareTrueFirst(s1.equals("label"), s2.equals("label"))
                            .compareTrueFirst(s1.equals("id"), s2.equals("id"))
                            .compare(s1, s2)
                            .result());
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

    /**
     * Helper to summarize a graph to standard output.
     */
    // TODO Make this more configurable?
    public static void summary(Graph graph) {
        GraphTraversalSource g = graph.traversal();
        PrintStream out = System.out;
        Map<String, Long> vertexCount = graph.traversal().V().<String, Long> group().by(T.label).by(count()).next();
        Map<String, Long> edgeCount = graph.traversal().E().<String, Long> group().by(T.label).by(count()).next();

        String partitionKey = graph.configuration().getString("bdio.partitionStrategy.partitionKey", DEFAULT_PARTITION_KEY);
        String writePartition = graph.configuration().getString("bdio.partitionStrategy.writePartition", DEFAULT_PARTITION);
        Set<String> partitions = g.V().<String> values(partitionKey).inject(writePartition).dedup().toSet();

        {
            out.format("Vertex count by label%n");
            out.format("=====================%n");
            vertexCount.forEach((l, c) -> out.format("  %s = %s%n", l, c));
            out.format("%n");
        }
        {
            out.format("Edge count by label%n");
            out.format("===================%n");
            edgeCount.forEach((l, c) -> out.format("  %s = %s%n", l, c));
            out.format("%n");
        }
        {
            out.format("Distinct Partitions (%s)%n", partitionKey);
            out.format("=====================%s=%n", repeat("=", partitionKey.length()));
            partitions.forEach(p -> out.format("  %s%n", p));
            out.format("%n");
        }
        {
            out.format("Graph indicies%n");
            out.format("==============%n");

            double v = vertexCount.values().stream().mapToDouble(Long::doubleValue).sum();
            double e = edgeCount.values().stream().mapToDouble(Long::doubleValue).sum();
            double p = partitions.size();
            double u = e - v + p;
            double d = e / (v * (v - 1));
            double alpha = u / ((2 * v) - 5);
            double beta = e / v;
            double gamma = e / (3 * (v - 2));

            out.format("  Number of Vertices:   \uD835\uDC63 = %.0f%n", v);
            out.format("  Number of Edges:      \uD835\uDC52 = %.0f%n", e);
            out.format("  Number of Sub-graphs: \uD835\uDC5D = %.0f%n", p);
            out.format("  Number of Cycles:     \uD835\uDC62 = %.0f%n", u);
            out.format("  Network Density:      \uD835\uDC51 = %.4f%n", d);
            out.format("  Alpha Index:          \u03B1 = %.4f%n", alpha);
            out.format("  Beta Index:           \u03B2 = %.4f%n", beta);
            out.format("  Gamma Index:          \u03B3 = %.4f%n", gamma);
            out.format("%n");
        }
    }

    /**
     * Helper to write a graph as BDIO back to standard output.
     */
    public void write(Graph graph) {
        try {
            write(graph, getBdioOutput(getOutput("-")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Helper to write a graph as BDIO back to the specified streams (e.g. a BDIO file or console).
     */
    public static void write(Graph graph, StreamSupplier out) {
        try {
            graph.io(BlackDuckIo.build()).writeGraph(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
