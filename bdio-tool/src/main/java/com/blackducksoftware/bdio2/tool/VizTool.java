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
import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.hasLabel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.temporal.Temporal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import javax.activation.FileTypeMap;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SubgraphStrategy;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.common.base.ExtraEnums;
import com.blackducksoftware.common.value.ContentRange;
import com.blackducksoftware.common.value.ContentType;
import com.blackducksoftware.common.value.Digest;
import com.blackducksoftware.common.value.HID;
import com.blackducksoftware.common.value.ProductList;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * View an interactive BDIO graph in a web browser.
 * <p>
 * This tool uses the Sun HTTP server found in Oracle JREs. You must have access to
 * {@code com.sun.net.httpserver.*} to use this tool.
 * <p>
 * This tool generates Gephi JSON output. This is useful for loading graph data into a browser using
 * <a href="http://sigmajs.org">Sigma</a> or <a href="http://visjs.org">vis.js</a>, see
 * <a href="https://marketplace.gephi.org/plugin/json-exporter/">Gephi JSON Exporter</a>.
 *
 * @author jgustie
 */
public class VizTool extends AbstractGraphTool {

    static final String DEFAULT_METADATA_LABEL = "Metadata";

    public static void main(String[] args) {
        new VizTool(null).parseArgs(args).run();
    }

    /**
     * HTTP handler for static resources.
     */
    private static class StaticResourceHandler implements HttpHandler {

        /**
         * A resource, just the content and it's type. Content types are assigned based on a file extension mapping of
         * the resource name, you may need to add entries to {@code /META-INF/mime.types} if a mapping is not defined.
         */
        private static class Resource {
            private final ByteSource content;

            private final String contentType;

            private Resource(String resourceName) {
                this.content = Resources.asByteSource(Resources.getResource(VizTool.class, resourceName));
                this.contentType = FileTypeMap.getDefaultFileTypeMap().getContentType(resourceName);
            }
        }

        // Note that this map based implementation prevents malicious "../" like URIs from being effective
        private Map<String, Resource> resources = new ConcurrentHashMap<>();

        public void add(String path, String name) {
            resources.put(path, new Resource(name));
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                Resource resource = resources.get(exchange.getRequestURI().getPath());
                if (resource != null) {
                    exchange.getResponseHeaders().add("Content-Type", resource.contentType);
                    exchange.sendResponseHeaders(200, resource.content.size());
                    resource.content.copyTo(exchange.getResponseBody());
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } else {
                exchange.getResponseHeaders().add("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
            }
            exchange.close();
        }
    }

    /**
     * The graph data handler is responsible for producing the actual graph data.
     */
    private static class GraphDataHandler implements HttpHandler {

        private static final String[] EMPTY = new String[0];

        private static final Module BDIO_MODULE = new SimpleModule("BDIO Module") {
            private static final long serialVersionUID = 1L;

            {
                addSerializer(Digest.class, ToStringSerializer.instance);
                addSerializer(ContentType.class, ToStringSerializer.instance);
                addSerializer(ContentRange.class, ToStringSerializer.instance);
                addSerializer(ProductList.class, ToStringSerializer.instance);
            }
        };

        private final Graph graph;

        private final ObjectMapper objectMapper;

        private GraphDataHandler(Graph graph, boolean isPretty) {
            this.graph = Objects.requireNonNull(graph);

            objectMapper = new ObjectMapper();
            objectMapper.registerModule(BDIO_MODULE);
            objectMapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            objectMapper.configure(SerializationFeature.INDENT_OUTPUT, isPretty);
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (exchange.getRequestURI().getPath().endsWith("/graph.json")) {
                    try {
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, 0);
                        try (JsonGenerator out = objectMapper.getFactory().createGenerator(exchange.getResponseBody())) {
                            write(out, filter(graph.traversal(), exchange));
                        }
                    } catch (RuntimeException e) {
                        exchange.sendResponseHeaders(500, 0);
                        Thread currentThread = Thread.currentThread();
                        Optional.ofNullable(currentThread.getUncaughtExceptionHandler())
                                .orElse(Thread.getDefaultUncaughtExceptionHandler())
                                .uncaughtException(currentThread, e);
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } finally {
                exchange.close();
            }
        }

        private GraphTraversalSource filter(GraphTraversalSource g, HttpExchange exchange) throws IOException {
            // Parse the query string
            // TODO This should be a Map<String, Collection<String>>
            Map<String, String> query = Splitter.on('&')
                    .omitEmptyStrings()
                    .withKeyValueSeparator('=')
                    .split(Strings.nullToEmpty(exchange.getRequestURI().getQuery()));

            // Use "?p=00000000-0000-0000-0001-000000000000" to filter by partition
            if (query.containsKey("p")) {
                String key = g.getGraph().configuration().getString("bdio.partitionStrategy.partitionKey", GraphTool.DEFAULT_PARTITION_KEY);
                return g.withStrategies(PartitionStrategy.build()
                        .partitionKey(key)
                        .readPartitions(query.get("p")) // TODO This should be a query.get("p").toArray(EMPTY)
                        .create());
            }

            // Use "?l=l1,l2,...,lN" to filter by label
            if (query.containsKey("l")) {
                // TODO This should be splitting each of multiple "l" values
                List<String> labels = Splitter.on(",").omitEmptyStrings().splitToList(query.get("l"));
                if (!labels.isEmpty()) {
                    String label = labels.get(0);
                    String[] otherLabels = labels.subList(1, labels.size()).toArray(EMPTY);
                    return g.withStrategies(SubgraphStrategy.build()
                            .vertices(hasLabel(label, otherLabels))
                            .create());
                }
            }

            // Use "?m=" to filter to metadata only
            if (query.containsKey("m")) {
                String metadataLabel = g.getGraph().configuration().getString("bdio.metadataLabel");
                return g.withStrategies(SubgraphStrategy.build()
                        .vertices(hasLabel(metadataLabel))
                        .create());
            }

            // Just return the unfiltered traversal source
            return g;
        }
    }

    private int port = 0;

    public VizTool(String name) {
        super(name);

        // These effectively serve as defaults which can be overridden later
        graphTool().setProperty("bdio.metadataLabel", DEFAULT_METADATA_LABEL);
        graphTool().setProperty("bdio.rootLabel", "root");
    }

    public void setPort(int port) {
        checkArgument(port >= 0 && port <= 0xFFFF, "Invalid port number: " + port);
        this.port = port;
    }

    @Override
    public void addInput(URI id, ByteSource input) {
        if (id != null) {
            super.addInput(id, input);
        } else {
            // Importing from stdin doesn't make sense here, instead just open the configured graph
            graphTool().setSkipLoad(true);
            graphTool().setSkipApplySemanticRules(true);
        }
    }

    @Override
    protected boolean isOptionWithArgs(String option) {
        return super.isOptionWithArgs(option) || GraphTool.isGraphConfigurationOptionWithArgs(option) || option.equals("--port");
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        args = GraphTool.parseGraphConfigurationArguments(args, graphTool());

        for (String arg : options(args)) {
            if (arg.startsWith("--port=")) {
                optionValue(arg).map(Integer::valueOf).ifPresent(this::setPort);
                args = removeFirst(arg, args);
            }
        }

        return super.parseArguments(args);
    }

    @Override
    protected void executeWithGraph(Graph graph) {
        try {
            // Bind explicitly to the local host instead of all available to avoid IPv6 confusion
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLocalHost(), port), 0);
            server.setExecutor(task -> {
                setThreadName();
                task.run();
            });

            // Add the static resources
            StaticResourceHandler staticResources = new StaticResourceHandler();
            staticResources.add("/", "www_viz/index.html");
            staticResources.add("/bdio/bdio.css", "www_viz/bdio.css");
            staticResources.add("/bdio/bdio.js", "www_viz/bdio.js");
            server.createContext("/", staticResources);

            // Add the graph data
            GraphDataHandler graphData = new GraphDataHandler(graph, isPretty());
            server.createContext("/data/", graphData);

            // Start it up
            server.start();

            try {
                // Compute the effective URI to the root resource
                URI uri = new URI("http", null, server.getAddress().getHostString(), server.getAddress().getPort(), "/", null, null);

                // TODO Can we open the web browser?
                // TODO Should the URL be output instead of a message?
                printMessage("%s%n", uri);
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }

            // TODO Is there a better way to block here?
            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop(0);
                latch.countDown();
            }, "shutdown-hook"));
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The thread gets used by Sqlg as the PostgreSQL application name, make sure it is something useful.
     */
    private static void setThreadName() {
        Thread currentThread = Thread.currentThread();
        if (currentThread.getName().equals("VizTool-http")) {
            currentThread.setName("VizTool-http");
        }
    }

    /**
     * Writes a graph to the specified JSON generator.
     */
    private static void write(JsonGenerator out, GraphTraversalSource g) throws IOException {
        out.writeStartObject();
        out.writeArrayFieldStart("nodes");
        g.V().forEachRemaining(v -> writeVertex(out, v));
        out.writeEndArray();
        out.writeArrayFieldStart("edges");
        g.E().forEachRemaining(e -> writeEdge(out, e));
        out.writeEndArray();
        out.writeEndObject();
    }

    private static void writeVertex(JsonGenerator out, Vertex vertex) {
        try {
            // Compute the label and size
            String label = null;
            int size = 5;
            if (vertex.label().equals(Bdio.Class.Project.name())) {
                label = stringValue(vertex, Bdio.DataProperty.name).orElse(null);
            } else if (vertex.label().equals(Bdio.Class.Component.name())) {
                label = stringValue(vertex, Bdio.DataProperty.name).orElse(null);
                size = 10;
            } else if (vertex.label().equals(Bdio.Class.File.name())) {
                label = stringValue(vertex, Bdio.DataProperty.path).map(p -> HID.from(p).getName()).orElse(null);
            } else if (vertex.label().equals(vertex.graph().configuration().getString("bdio.metadataLabel", VizTool.DEFAULT_METADATA_LABEL))) {
                label = vertex.id().toString();
                size = 20;
            }
            if (ExtraEnums.tryByName(Bdio.Class.class, vertex.label()).filter(Bdio.Class::root).isPresent()) {
                size = 15;
            }

            // Write the vertex
            out.writeStartObject();
            out.writeStringField("id", vertex.id().toString());
            out.writeStringField("label", label);
            out.writeNumberField("size", size);
            out.writeBooleanField("fixed", false);
            out.writeObjectFieldStart("attributes");
            out.writeStringField("@type", vertex.label());
            out.writeStringField("@id", vertex.id().toString());
            Iterator<VertexProperty<Object>> properites = vertex.properties();
            while (properites.hasNext()) {
                VertexProperty<Object> property = properites.next();

                // Instead of using 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
                if (property.value() instanceof Temporal) {
                    out.writeObjectField(property.key(), property.value().toString());
                } else {
                    out.writeObjectField(property.key(), property.value());
                }
            }
            out.writeEndObject();
            out.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeEdge(JsonGenerator out, Edge edge) {
        try {
            // Write the edge
            out.writeStartObject();
            out.writeStringField("id", edge.id().toString());
            out.writeStringField("source", edge.outVertex().id().toString());
            out.writeStringField("target", edge.inVertex().id().toString());
            out.writeObjectFieldStart("attributes");
            out.writeStringField("@type", edge.label());
            out.writeStringField("@id", edge.id().toString());
            out.writeEndObject();
            out.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
