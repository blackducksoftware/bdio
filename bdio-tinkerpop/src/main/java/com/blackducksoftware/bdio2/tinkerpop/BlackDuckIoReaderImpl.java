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
package com.blackducksoftware.bdio2.tinkerpop;

import static com.blackducksoftware.common.base.ExtraOptionals.ofType;
import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.tinkerpop.gremlin.structure.Direction.OUT;
import static org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality.single;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph.Features.VertexFeatures;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph.StarVertex;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;

import io.reactivex.Flowable;

/**
 * Implementation for most of the read graph logic. This is separated out because there is multiple optimized code paths
 * for importing BDIO depending on the graph implementation.
 *
 * @author jgustie
 */
class BlackDuckIoReaderImpl {

    /**
     * Mimics the Sqlg bulk edge creation API as a functional interface.
     */
    private interface BulkEdgeConsumer {
        void accept(String outVertexLabel, String inVertexLabel, String edgeLabel, Pair<String, String> idFields, Collection<Pair<Object, Object>> uids);
    }

    /**
     * A key type to use for grouping potential edges.
     */
    private static class EdgeKey {
        private static Map<String, Map<String, EdgeKey>> INSTANCES = new HashMap<>();

        private final String edgeLabel;

        private final String outVertexLabel;

        private EdgeKey(String edgeLabel, String outVertexLabel) {
            this.edgeLabel = Objects.requireNonNull(edgeLabel);
            this.outVertexLabel = Objects.requireNonNull(outVertexLabel);
        }

        public static EdgeKey of(String edgeLabel, String outVertexLabel) {
            return INSTANCES
                    .computeIfAbsent(edgeLabel, x -> new HashMap<>())
                    .computeIfAbsent(outVertexLabel, x -> new EdgeKey(edgeLabel, outVertexLabel));
        }

        @Override
        public String toString() {
            return "[" + edgeLabel + ", " + outVertexLabel + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(edgeLabel, outVertexLabel);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof EdgeKey) {
                return edgeLabel.equals(((EdgeKey) obj).edgeLabel) && outVertexLabel.equals(((EdgeKey) obj).outVertexLabel);
            } else {
                return false;
            }
        }
    }

    /**
     * Helper to stream vertices as they are collected. This must be used to collect nodes sorted by their type: each
     * time the type changes from the previously seen value, the callbacks supplied to the constructor are invoked.
     */
    private static class VertexCollector implements Consumer<Map<String, Object>>, AutoCloseable {

        /**
         * Callback to invoke for each buffered node on flush.
         */
        private final Consumer<Map<String, Object>> nodeConsumer;

        /**
         * Callback to invoke after each node type is flushed.
         */
        private final Runnable onFlush;

        /**
         * The current "last seen type".
         */
        private String type;

        /**
         * Set of all node property keys seen since the last flush. When invoking the {@link #nodeConsumer}, every map
         * must have the same key set.
         */
        private final Set<String> schema = new HashSet<>();

        /**
         * The buffer of nodes seen since the last flush.
         */
        private final List<Map<String, Object>> buffer = new ArrayList<>();

        public VertexCollector(Consumer<Map<String, Object>> nodeConsumer, Runnable onFlush) {
            this.nodeConsumer = Objects.requireNonNull(nodeConsumer);
            this.onFlush = Objects.requireNonNull(onFlush);
        }

        @Override
        public void close() {
            flush();
        }

        @Override
        public void accept(Map<String, Object> node) {
            getNodeType(node).ifPresent(type -> {
                if (!type.equals(this.type)) {
                    flush();
                    this.type = type;
                }
                buffer.add(node);
                schema.addAll(node.keySet());
            });
        }

        public void flush() {
            if (!buffer.isEmpty() && !schema.isEmpty()) {
                // We need to ensure that all the maps have the same keys
                Map<String, Object> schemaMap = new HashMap<>(schema.size());
                schema.forEach(s -> schemaMap.put(s, null));

                // Drain the buffer
                for (Map<String, Object> node : buffer) {
                    Map<String, Object> effectiveNode = node;
                    if (effectiveNode.size() != schemaMap.size()) {
                        effectiveNode = new HashMap<>(schemaMap);
                        effectiveNode.putAll(node);
                    }
                    nodeConsumer.accept(effectiveNode);
                }

                // Record the flush
                onFlush.run();
            }

            // Make sure we get back to an empty state
            // TODO Should we re-assign buffer to a new list?
            type = null;
            buffer.clear();
            schema.clear();
        }

    }

    /**
     * Helper to aggregate the edges.
     */
    private static class EdgeCollector {

        /**
         * Funnel used for hashing vertex identifiers.
         */
        private static Funnel<Object> VERTEX_IDENTIFIER_FUNNEL = (id, sink) -> {
            if (id instanceof String) {
                sink.putUnencodedChars((String) id);
            } else {
                // TODO What should we do here?
                throw new ClassCastException("Expected identifier to be a string: " + id);
            }
        };

        /**
         * Bloom filter factory, accounts for expected insertions.
         */
        private static BloomFilter<Object> newIdentifierSet(String label) {
            if (label.equals(Bdio.Class.File.name())) {
                return BloomFilter.create(VERTEX_IDENTIFIER_FUNNEL, 10_000_000);
            } else {
                return BloomFilter.create(VERTEX_IDENTIFIER_FUNNEL, 1_000_000);
            }
        }

        /**
         * Functional reference to the Sqlg bulk add edges API.
         */
        private final BulkEdgeConsumer edgeConsumer;

        /**
         * The identifier fields used to bulk add edges.
         */
        private final Pair<String, String> idFields;

        /**
         * Callback to flatten persisted vertices by label.
         */
        private final Consumer<String> doFlatten;

        /**
         * Callback to invoke when performing a flush.
         */
        private final Runnable onFlush;

        /**
         * Mapping of vertex label types to their probabilistic identifier sets.
         */
        private final Map<String, BloomFilter<Object>> vertexLabelCache = new HashMap<>();

        /**
         * Representation of the edges.
         */
        private final Multimap<EdgeKey, Pair<Object, Object>> edges = HashMultimap.create();

        public EdgeCollector(BulkEdgeConsumer edgeConsumer, String identifierKey, Consumer<String> doFlatten, Runnable onFlush) {
            this.edgeConsumer = Objects.requireNonNull(edgeConsumer);
            this.idFields = Pair.of(Objects.requireNonNull(identifierKey), identifierKey);
            this.doFlatten = Objects.requireNonNull(doFlatten);
            this.onFlush = Objects.requireNonNull(onFlush);
        }

        public void addVertexLabel(String vertexLabel, Object vertexId) {
            vertexLabelCache.computeIfAbsent(vertexLabel, EdgeCollector::newIdentifierSet).put(vertexId);
        }

        public void addEdge(String outVertexLabel, Object outVertexId, String edgeLabel, Object inVertexId) {
            edges.put(EdgeKey.of(edgeLabel, outVertexLabel), Pair.of(outVertexId, inVertexId));
        }

        public EdgeCollector combine(EdgeCollector other) {
            // Combine the state of the other collector into this collector
            checkArgument(idFields.equals(other.idFields), "mismatched identifier fields");
            for (Map.Entry<String, BloomFilter<Object>> label : other.vertexLabelCache.entrySet()) {
                vertexLabelCache.merge(label.getKey(), label.getValue(), (a, b) -> {
                    a.putAll(b);
                    return a;
                });
            }
            edges.putAll(other.edges);
            return this;
        }

        public void finish() {
            // Flatten the labels that were encountered
            vertexLabelCache.keySet().forEach(doFlatten);

            for (Map.Entry<EdgeKey, Collection<Pair<Object, Object>>> edge : edges.asMap().entrySet()) {
                String edgeLabel = edge.getKey().edgeLabel;
                String outVertexLabel = edge.getKey().outVertexLabel;

                // Group the identifier pairs by available in-vertex labels
                Multimap<String, Pair<Object, Object>> availableEdges = HashMultimap.create();
                for (Pair<Object, Object> uid : edge.getValue()) {
                    // This is where things get tricky. Since the JSON-LD "edge" is just a reference, we need to
                    // reconstruct the "in-vertex" label. Caching the entire identifier set is prohibitive, thus the
                    // bloom filters keyed by label. Only one bloom filter should claim an identifier, however there may
                    // be multiple matches in the false-positive case. We can accept false-positives by asking Sqlg to
                    // bulk create an edge to a vertex that does not exist (bulk edge creation is accomplished using a
                    // JOIN that will not produce an edge row for the false-positives).
                    boolean found = false;
                    for (Map.Entry<String, BloomFilter<Object>> idsByType : vertexLabelCache.entrySet()) {
                        if (idsByType.getValue().mightContain(uid.getRight())) {
                            availableEdges.put(idsByType.getKey(), uid);
                            found = true;
                        }
                    }

                    // If no bloom filter matched, we definitely never saw the requested identifier and should fail
                    if (!found) {
                        throw new RuntimeException(new NodeDoesNotExistException(uid.getLeft(), edgeLabel, uid.getRight()));
                    }
                }

                // If we found anything, stream it to the database and flush
                // TODO Write partitions on the edges?!
                availableEdges.asMap().forEach((inVertexLabel, uids) -> edgeConsumer.accept(outVertexLabel, inVertexLabel, edgeLabel, idFields, uids));
                onFlush.run();
            }
        }
    }

    /**
     * The wrapper used to access the graph.
     */
    private final GraphReaderWrapper wrapper;

    /**
     * Cached instance of vertex features.
     */
    private final VertexFeatures vertexFeatures;

    public BlackDuckIoReaderImpl(GraphReaderWrapper wrapper) {
        this.wrapper = Objects.requireNonNull(wrapper);
        vertexFeatures = wrapper.graph().features().vertex();
    }

    public Flowable<?> readNodes(Flowable<Map<String, Object>> framedEntries) {
        // `framedEntries` is a sequence of node lists, each list should have an upper bound around 2^15 elements

        // We can take an optimized path for Sqlg, but only if we are preserving original JSON-LD identifiers
        if (wrapper.graph() instanceof SqlgGraph && wrapper.mapper().identifierKey().isPresent()) {
            return readNodesSqlg(framedEntries);
        } else {
            return readNodesMemory(framedEntries);
        }
    }

    private Flowable<?> readNodesSqlg(Flowable<Map<String, Object>> framedEntries) {
        // Stream vertices straight to the database and aggregate the edges
        SqlgGraph sqlgGraph = (SqlgGraph) wrapper.graph();
        return framedEntries
                .map(BdioDocument::toGraphNodes)
                .map(nodes -> {
                    try (VertexCollector collectVertices = new VertexCollector(this::streamNode, wrapper::flushTx)) {
                        nodes.stream().sorted(BlackDuckIoReaderImpl::nodeTypeOrder).forEach(collectVertices);
                    }

                    return nodes.stream().reduce(
                            new EdgeCollector(sqlgGraph::bulkAddEdges, wrapper.mapper().identifierKey().get(), this::flattenVertices, wrapper::flushTx),
                            this::aggregateEdges, EdgeCollector::combine);
                })
                .reduce(EdgeCollector::combine)
                .doOnSuccess(EdgeCollector::finish)
                .doOnSubscribe(x -> sqlgGraph.tx().streamingBatchModeOn())
                .toFlowable();
    }

    private Flowable<?> readNodesMemory(Flowable<Map<String, Object>> framedEntries) {
        // Collect all of the vertices in a map, creating the actual vertices in the graph as we go
        return framedEntries
                .flatMapIterable(BdioDocument::toGraphNodes)
                .map(node -> {
                    StarGraph starGraph = StarGraph.open();
                    StarVertex vertex = (StarVertex) starGraph.addVertex(wrapper.getNodeProperties(node, true));
                    wrapper.getNodeObjectProperties(node, (edgeLabel, inVertexId) -> vertex.addEdge(edgeLabel, starGraph.addVertex(T.id, inVertexId)));
                    return vertex;
                })
                .reduce(new HashMap<>(), this::toMap)
                .flatMapPublisher(this::createEdges)
                .doOnSubscribe(x -> wrapper.startBatchTx())
                .doOnNext(x -> wrapper.batchFlushTx());
    }

    public EdgeCollector aggregateEdges(EdgeCollector edges, Map<String, Object> node) {
        // Collect all the object properties for the node into the edge collector
        getNodeType(node).ifPresent(outVertexLabel -> {
            Object outVertexId = node.get(JsonLdConsts.ID);
            edges.addVertexLabel(outVertexLabel, outVertexId);
            wrapper.getNodeObjectProperties(node, (edgeLabel, inVertexId) -> {
                edges.addEdge(outVertexLabel, outVertexId, edgeLabel, inVertexId);
            });
        });
        return edges;
    }

    private void streamNode(Map<String, Object> node) {
        // Stream the supplied node to the database
        ((SqlgGraph) wrapper.graph()).streamVertex(wrapper.getNodeProperties(node, false));
    }

    private void flattenVertices(String type) {
        // Collapse any split nodes (i.e. nodes with the same JSON-LD identifier)
        wrapper.groupByMultiple(type, wrapper.mapper().identifierKey().get())
                .forEachRemaining(duplicates -> duplicates.stream().reduce((left, right) -> {
                    // TODO 'single' force overwrite, can we detect multiple (e.g. file fingerprints) from the mapper?
                    right.properties().forEachRemaining(vp -> left.property(single, vp.key(), vp.value()));
                    right.remove();

                    // Periodically flush
                    wrapper.batchFlushTx();
                    return left;
                }));

        // Flush the remaining results
        wrapper.flushTx();
    }

    private Map<StarVertex, Vertex> toMap(Map<StarVertex, Vertex> map, StarVertex baseVertex) {
        Vertex persisted = map.get(baseVertex);
        if (persisted != null) {
            // Update properties
            // TODO 'single' force overwrite, can we detect multiple (e.g. file fingerprints) from the mapper?
            baseVertex.properties().forEachRemaining(vp -> persisted.property(single, vp.key(), vp.value()));

            // Update edges
            Iterator<Edge> edges = baseVertex.edges(Direction.OUT);
            if (edges.hasNext()) {
                // Worst case scenario. We need to get the old key, which means a linear search...
                // The problem is that `baseVertex` can be used for a lookup, but it's not the actual key
                for (StarVertex existingUnpersistedVertex : map.keySet()) {
                    if (existingUnpersistedVertex.equals(baseVertex)) {
                        edges.forEachRemaining(e -> existingUnpersistedVertex.addEdge(e.label(), e.inVertex()));
                        break;
                    }
                }
            }
        } else {
            // Create the new vertex
            boolean includeId = vertexFeatures.willAllowId(baseVertex.id());
            map.put(baseVertex, wrapper.graph().addVertex(ElementHelper.getProperties(baseVertex, includeId, true, Collections.emptySet())));
        }

        // Batch commit update or insertion
        wrapper.batchFlushTx();
        return map;
    }

    private Flowable<Edge> createEdges(Map<StarVertex, Vertex> persistedVertices) {
        return Flowable.fromIterable(persistedVertices.keySet())

                // Gets all the outbound edges from in-memory (StarVertex) vertices
                .flatMapIterable(kv -> (Iterable<Edge>) (() -> kv.edges(Direction.OUT)))

                // Connect the edges and store their properties
                .map(e -> {
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
                            return edge;
                        }
                    }

                    // Create a new edge
                    Edge edge = cachedOutV.addEdge(e.label(), cachedInV);
                    wrapper.forEachPartition(edge::property);
                    return edge;
                });
    }

    private static Optional<String> getNodeType(Map<String, Object> node) {
        return Optional.ofNullable(node.get(JsonLdConsts.TYPE)).flatMap(ofType(String.class));
    }

    private static int nodeTypeOrder(Map<String, Object> left, Map<String, Object> right) {
        Object leftType = left.get(JsonLdConsts.TYPE);
        Object rightType = right.get(JsonLdConsts.TYPE);
        if (leftType instanceof String) {
            return rightType instanceof String ? ((String) leftType).compareTo((String) rightType) : 1;
        } else {
            return rightType instanceof String ? -1 : 0;
        }
    }

}
