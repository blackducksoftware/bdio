/*
 * Copyright 2018 Synopsys, Inc.
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
package com.blackducksoftware.bdio2.tinkerpop.spi;

import static java.nio.charset.StandardCharsets.UTF_16BE;
import static org.apache.tinkerpop.gremlin.structure.Direction.OUT;
import static org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality.single;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph.Features.VertexFeatures;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph.StarVertex;
import org.reactivestreams.Publisher;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.common.base.ExtraUUIDs;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Joiner;
import com.google.common.hash.Hashing;

import io.reactivex.Flowable;

final class DefaultBlackDuckIoReader extends BlackDuckIoReaderSpi {

    /**
     * UUID namespace to use for converting JSON-LD "@id" values when the underlying graph is configured to use UUID
     * identifiers.
     */
    private static final UUID COMPOSITE_IDENTIFIER_NAME_SPACE = ExtraUUIDs.fromString("d51a24f4-3ab9-4ac1-ba32-6295399acf5f");

    /**
     * Helper class to manage the accumulation of nodes. This particular implementation requires a lot of memory because
     * it keeps an in-memory cache of all of the nodes.
     */
    private final class NodeAccumulator {

        /**
         * The map used to store two copies of the entire graph at once.
         */
        private final Map<StarVertex, Vertex> persistedVertices = new HashMap<>();

        public NodeAccumulator addNode(Map<String, Object> node) {
            // Convert the node to a vertex and upsert it into the graph
            StarVertex baseVertex = convertNodeToVertex(node);
            Vertex persisted = persistedVertices.get(baseVertex);
            if (persisted != null) {
                mergeVertex(baseVertex, persisted);
            } else {
                persistedVertices.put(baseVertex, createVertex(baseVertex));
            }
            return this;
        }

        private StarVertex convertNodeToVertex(Map<String, Object> node) {
            // Store all the properties in a key/value list
            List<Object> keyValues = new ArrayList<>(node.size());
            getNodeProperties(node, (k, v) -> {
                if (k == T.id) {
                    keyValues.add(k);
                    keyValues.add(convertId(v));
                } else if (v != null) {
                    keyValues.add(k);
                    keyValues.add(v);
                }
            });

            // Create a new graph for the node and it's immediate connections
            StarGraph starGraph = StarGraph.open();
            StarVertex vertex = (StarVertex) starGraph.addVertex(keyValues.toArray());

            // Add the node edges into the graph
            getNodeEdges(node, (edgeLabel, inVertexId) -> vertex.addEdge(edgeLabel, starGraph.addVertex(T.id, convertId(inVertexId))));

            return vertex;
        }

        private Vertex createVertex(StarVertex baseVertex) {
            // Create the new vertex using a traversal
            GraphTraversalSource g = traversal();
            GraphTraversal<Vertex, Vertex> t = g.addV(baseVertex.label());
            if (vertexFeatures.willAllowId(baseVertex.id())) {
                t = t.property(T.id, baseVertex.id());
            }
            Iterator<VertexProperty<Object>> i = baseVertex.properties();
            while (i.hasNext()) {
                VertexProperty<Object> p = i.next();
                t = t.property(p.key(), p.value());
            }
            return t.next();
        }

        private void mergeVertex(StarVertex baseVertex, Vertex persisted) {
            // Update properties
            // TODO Adjust the cardinality based on the vp.key()
            baseVertex.properties().forEachRemaining(vp -> persisted.property(single, vp.key(), vp.value()));

            // Update edges
            Iterator<Edge> edges = baseVertex.edges(Direction.OUT);
            if (edges.hasNext()) {
                // Worst case scenario. We need to get the old key, which means a linear search...
                // The problem is that `baseVertex` can be used for a lookup, but it's not the actual key
                for (StarVertex oldBaseVertex : persistedVertices.keySet()) {
                    if (oldBaseVertex.equals(baseVertex)) {
                        edges.forEachRemaining(e -> oldBaseVertex.addEdge(e.label(), e.inVertex()));
                        break;
                    }
                }
            }
        }

        public void addEdges() throws NodeDoesNotExistException {
            // Persist all the edges from the StarVertex keys to corresponding persisted vertices
            GraphTraversalSource g = traversal();
            for (StarVertex starVertex : persistedVertices.keySet()) {
                Iterator<Edge> starEdges = starVertex.edges(OUT);
                edgeLoop: while (starEdges.hasNext()) {
                    Edge e = starEdges.next();

                    // Look up the vertices
                    Vertex cachedOutV = persistedVertices.get(e.outVertex());
                    Vertex cachedInV = persistedVertices.get(e.inVertex());
                    if (cachedInV == null) {
                        throw new NodeDoesNotExistException(e.outVertex().id(), e.label(), e.inVertex().id());
                    }

                    // First try to find an existing edge
                    Iterator<Edge> edges = cachedOutV.edges(OUT, e.label());
                    while (edges.hasNext()) {
                        Edge edge = edges.next();
                        if (edge.inVertex().equals(cachedInV)) {
                            break edgeLoop;
                        }
                    }

                    // Create a new edge
                    g.V(cachedOutV).addE(e.label()).to(cachedInV).iterate();
                }
            }
        }

        public void commitTx() {
            if (supportsTransactions) {
                graph().tx().commit();
            }
        }
    }

    /**
     * Cached transaction support flag.
     */
    private final boolean supportsTransactions;

    /**
     * Cached instance of vertex features.
     */
    private final VertexFeatures vertexFeatures;

    public DefaultBlackDuckIoReader(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        super(traversal, options, frame);
        this.supportsTransactions = graph().features().graph().supportsTransactions();
        this.vertexFeatures = traversal.getGraph().features().vertex();
    }

    @Override
    public Publisher<?> persistFramedEntries(Flowable<Map<String, Object>> framedEntries) {
        return framedEntries
                .flatMapIterable(BdioDocument::toGraphNodes)
                .reduce(new NodeAccumulator(), NodeAccumulator::addNode)
                .doOnSuccess(NodeAccumulator::addEdges)
                .doAfterSuccess(NodeAccumulator::commitTx)
                .doOnError(this::handleError)
                .toFlowable();
    }

    protected Object convertId(Object id) {
        if (vertexFeatures.supportsUserSuppliedIds()) {
            Map<Object, Object> mapId = new LinkedHashMap<>();
            mapId.put(JsonLdConsts.ID, id);

            // If the graph supports "user supplied identifiers" and we are using a partition strategy the "primary
            // key" must account for both the raw identifier in the document and the partition strategy, otherwise
            // reading the same document into different partitions would conflict with each other.
            getTraversalProperties(mapId::put, false);

            // From the map, we (may) need a string
            String stringId;
            if (mapId.size() > 1) {
                if (vertexFeatures.willAllowId(mapId)) {
                    return mapId;
                }

                stringId = Joiner.on("\",\"").withKeyValueSeparator("\"=\"")
                        .appendTo(new StringBuilder().append("{\""), mapId).append("\"}").toString();
            } else {
                // The "@id" should already be a string if it is valid
                stringId = id.toString();
            }

            // The default implementations will blindly accept strings without checking them
            if (vertexFeatures.willAllowId(Long.valueOf(0L))) {
                return Hashing.farmHashFingerprint64().hashUnencodedChars(stringId).asLong();
            } else if (vertexFeatures.willAllowId(ExtraUUIDs.nilUUID())) {
                return ExtraUUIDs.nameUUIDFromBytes(COMPOSITE_IDENTIFIER_NAME_SPACE, stringId.getBytes(UTF_16BE));
            } else if (vertexFeatures.willAllowId(stringId)) {
                return stringId;
            }
        }

        // Nothing we can do with the conversion, just allow the raw identifier back out in hopes that it works
        return id;
    }

    private void handleError(Throwable t) {
        if (supportsTransactions) {
            graph().tx().rollback();
        }
    }

}
