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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.temporal.Temporal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;

import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.umlg.sqlg.structure.RecordId;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.common.value.HID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.github.jsonldjava.utils.JsonUtils;

/**
 * Generates Gephi JSON output. Useful for loading graph data into a browser using
 * <a href="http://sigmajs.org">Sigma</a> or <a href="http://visjs.org">vis.js</a>.
 *
 * @author jgustie
 * @see <a href="https://marketplace.gephi.org/plugin/json-exporter/">Gephi JSON Exporter</a>
 */
public class GephiFormat {

    // For readability, went with mutable, builder-like POJOs for serialization

    @JsonInclude(Include.NON_NULL)
    @ParametersAreNullableByDefault
    public static class GephiData {

        public List<GephiNode> nodes;

        public List<GephiEdge> edges;

        public GephiData nodes(@Nonnull List<GephiNode> nodes) {
            this.nodes = Objects.requireNonNull(nodes);
            return this;
        }

        public GephiData edges(@Nonnull List<GephiEdge> edges) {
            this.edges = Objects.requireNonNull(edges);
            return this;
        }
    }

    @JsonInclude(Include.NON_NULL)
    @ParametersAreNullableByDefault
    public static class GephiNode {

        public String id;

        public String label;

        public Integer size;

        public Boolean fixed;

        public Map<Object, Object> attributes;

        public static GephiNode fromVertex(Traverser<Vertex> v) {
            Vertex vertex = v.get();
            return new GephiNode()
                    .id(GephiFormat.computeElementId(vertex))
                    .label(GephiFormat.computeNodeLabel(vertex))
                    .size(GephiFormat.computeNodeSize(vertex))
                    .attributes(GephiFormat.computeNodeAttributes(vertex));
        }

        public GephiNode id(@Nonnull String id) {
            this.id = Objects.requireNonNull(id);
            return this;
        }

        public GephiNode label(String label) {
            this.label = label;
            return this;
        }

        public GephiNode size(int size) {
            this.size = size;
            return this;
        }

        public GephiNode fixed(boolean fixed) {
            this.fixed = fixed;
            return this;
        }

        public GephiNode attributes(@Nonnull Map<Object, Object> attributes) {
            this.attributes = Objects.requireNonNull(attributes);
            return this;
        }
    }

    @JsonInclude(Include.NON_NULL)
    @ParametersAreNullableByDefault
    public static class GephiEdge {

        public String id;

        public String source;

        public String target;

        public String title;

        public static GephiEdge fromEdge(Traverser<Edge> e) {
            Edge edge = e.get();
            return new GephiEdge()
                    .id(GephiFormat.computeElementId(edge))
                    .source(GephiFormat.computeElementId(edge.outVertex()))
                    .target(GephiFormat.computeElementId(edge.inVertex()))
                    .title(edge.label());
        }

        public GephiEdge id(@Nonnull String id) {
            this.id = Objects.requireNonNull(id);
            return this;
        }

        public GephiEdge source(@Nonnull String source) {
            this.source = Objects.requireNonNull(source);
            return this;
        }

        public GephiEdge target(@Nonnull String target) {
            this.target = Objects.requireNonNull(target);
            return this;
        }

        public GephiEdge title(@Nonnull String title) {
            this.title = Objects.requireNonNull(title);
            return this;
        }
    }

    /**
     * Constructs the full Gephi data set in memory.
     */
    public static GephiData fromGraph(GraphTraversalSource g) {
        try {
            return new GephiData()
                    .nodes(g.V().map(GephiNode::fromVertex).toList())
                    .edges(g.E().map(GephiEdge::fromEdge).toList());
        } finally {
            if (g.getGraph().features().graph().supportsTransactions()) {
                g.getGraph().tx().rollback();
            }
        }
    }

    private static String computeElementId(Element element) {
        Object id = element.id();
        if (id instanceof RecordId) {
            return element.label() + "-" + ((RecordId) id).getId();
        } else {
            return id.toString();
        }
    }

    @Nullable
    private static String computeNodeLabel(Vertex vertex) {
        if (vertex.label().equals(Bdio.Class.Project.name())
                || vertex.label().equals(Bdio.Class.Component.name())) {
            return vertex.<String> property(Bdio.DataProperty.name.name()).orElse(null);
        } else if (vertex.label().equals(Bdio.Class.File.name())) {
            VertexProperty<String> path = vertex.property(Bdio.DataProperty.path.name());
            return path.isPresent() ? HID.from(URI.create(path.value())).getName() : vertex.id().toString();
        } else {
            return null;
        }
    }

    private static int computeNodeSize(Vertex vertex) {
        if (vertex.label().equals(Bdio.Class.Project.name())) {
            return 15;
        } else if (vertex.label().equals(Bdio.Class.Component.name())) {
            return 10;
        } else if (vertex.label().equals(vertex.graph().configuration().getString("bdio.metadataLabel", VizTool.DEFAULT_METADATA_LABEL))) {
            return 20;
        } else {
            return 5;
        }
    }

    private static Map<Object, Object> computeNodeAttributes(Vertex vertex) {
        Map<Object, Object> attributes = new LinkedHashMap<>();
        vertex.properties().forEachRemaining(p -> {
            switch (p.key()) {
            case "_id":
            case "~id":
            case "~label":
                break;
            case "_unknown":
                try {
                    attributes.putAll((Map<?, ?>) JsonUtils.fromString(p.value().toString()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                break;
            default:
                if (p.value() instanceof Temporal) {
                    attributes.put(p.key(), p.value().toString());
                } else {
                    attributes.put(p.key(), p.value());
                }
            }
        });
        return attributes;
    }

}
