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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.activation.FileTypeMap;

import org.apache.tinkerpop.gremlin.structure.Graph;

import com.blackducksoftware.common.value.ContentRange;
import com.blackducksoftware.common.value.ContentType;
import com.blackducksoftware.common.value.Digest;
import com.blackducksoftware.common.value.ProductList;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * View an interactive BDIO graph in a web browser.
 * <p>
 * NOTE: This tool uses the Sun HTTP server found in Oracle JREs. You must have access to
 * {@code com.sun.net.httpserver.*} to use this tool.
 *
 * @author jgustie
 */
public class VizTool extends AbstractGraphTool {

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

        private static final Module BDIO_MODULE = new SimpleModule("BDIO Module") {
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
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestURI().getPath().endsWith("graph.json")) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                // TODO Can we stream this instead?
                objectMapper.writeValue(exchange.getResponseBody(), GephiFormat.fromGraph(graph));
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        }
    }

    enum VTT {
        _Metadata,
        _root,
    }

    private int port = 0;

    public VizTool(String name) {
        super(name);
        graphTool().setProperty("bdio.metadataLabel", VTT._Metadata.name());
        graphTool().setProperty("bdio.rootLabel", VTT._root.name());
    }

    public void setPort(int port) {
        checkArgument(port >= 0 && port <= 0xFFFF, "Invalid port number: " + port);
        this.port = port;
    }

    @Override
    protected void executeWithGraph(Graph graph) {
        try {
            // Bind explicitly to the local host instead of all available to avoid IPv6 confusion
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLocalHost(), port), 0);

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
                printMessage("%s", uri);
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
