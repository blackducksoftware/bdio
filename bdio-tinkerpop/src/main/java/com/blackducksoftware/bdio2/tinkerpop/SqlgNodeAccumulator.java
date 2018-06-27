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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.PrimitiveSink;

/**
 * A node accumulator for populating a SqlgGraph.
 *
 * @author jgustie
 */
class SqlgNodeAccumulator extends NodeAccumulator {

    public static boolean acceptWrapper(GraphReaderWrapper wrapper) {
        return wrapper instanceof SqlgGraphReaderWrapper && wrapper.mapper().identifierKey().isPresent();
    }

    /**
     * A key type to use for grouping potential edges.
     */
    private static class EdgeKey {

        /**
         * The practical limit of this combination is probably around 50-100 instances (sum of the size of all
         * {@code @Domain} values appearing on the object properties), in theory it could be higher if the user
         * supplies
         * additional object properties. Regardless, the size of this map is very small relative to the potential
         * number
         * of instances being created.
         */
        private static Map<String, Map<String, SqlgNodeAccumulator.EdgeKey>> INSTANCES = new HashMap<>();

        private final String edgeLabel;

        private final String outVertexLabel;

        private EdgeKey(String edgeLabel, String outVertexLabel) {
            this.edgeLabel = Objects.requireNonNull(edgeLabel);
            this.outVertexLabel = Objects.requireNonNull(outVertexLabel);
        }

        public static SqlgNodeAccumulator.EdgeKey of(String edgeLabel, String outVertexLabel) {
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
            if (obj == this) {
                return true;
            } else if (obj instanceof SqlgNodeAccumulator.EdgeKey) {
                return edgeLabel.equals(((SqlgNodeAccumulator.EdgeKey) obj).edgeLabel)
                        && outVertexLabel.equals(((SqlgNodeAccumulator.EdgeKey) obj).outVertexLabel);
            } else {
                return false;
            }
        }
    }

    /**
     * Sort order.
     */
    public static int nodeTypeOrder(Map<String, Object> left, Map<String, Object> right) {
        Object leftType = left.get(JsonLdConsts.TYPE);
        Object rightType = right.get(JsonLdConsts.TYPE);
        if (leftType instanceof String) {
            return rightType instanceof String ? ((String) leftType).compareTo((String) rightType) : 1;
        } else {
            return rightType instanceof String ? -1 : 0;
        }
    }

    /**
     * Decomposes a raw node identifier into primitives.
     */
    private static void identifierFunnel(Object id, PrimitiveSink sink) {
        if (id instanceof String) {
            sink.putUnencodedChars((String) id);
        }
    }

    /**
     * Bloom filter factory, accounts for expected insertions.
     */
    private static BloomFilter<Object> newIdentifierSet(String label) {
        int expectedInsertions = label.equals(Bdio.Class.File.name()) ? 10_000_000 : 1_000_000;
        return BloomFilter.create(SqlgNodeAccumulator::identifierFunnel, expectedInsertions);
    }

    /**
     * Merges the second bloom filter into the first.
     */
    private static <E> BloomFilter<E> combineBloomFilter(BloomFilter<E> a, BloomFilter<E> b) {
        a.putAll(b);
        return a;
    }

    /**
     * The current "last seen type".
     */
    private String type;

    /**
     * The buffer of nodes seen since the last flush.
     */
    private final List<Map<String, Object>> nodes = new ArrayList<>();

    /**
     * Representation of the edges.
     */
    private final Multimap<SqlgNodeAccumulator.EdgeKey, Pair<Object, Object>> edges = HashMultimap.create();

    /**
     * Set of all node property keys seen since the last flush. When invoking the {@link #nodeConsumer}, every map
     * must have the same key set.
     */
    private final Map<String, Object> schema = new HashMap<>();

    /**
     * Mapping of vertex label types to their probabilistic identifier sets.
     */
    private final Map<String, BloomFilter<Object>> identifierTypes = new HashMap<>();

    public SqlgNodeAccumulator(GraphReaderWrapper wrapper) {
        super(wrapper);
        checkArgument(wrapper instanceof SqlgGraphReaderWrapper, "must be a Sqlg graph wrapper");
        checkArgument(wrapper.mapper().identifierKey().isPresent(), "identifier key must be present");
    }

    @Override
    protected SqlgGraphReaderWrapper wrapper() {
        return (SqlgGraphReaderWrapper) super.wrapper();
    }

    @Override
    protected SqlgGraph graph() {
        return (SqlgGraph) super.graph();
    }

    @Override
    public SqlgNodeAccumulator addNode(Map<String, Object> node) {
        Object rawType = node.get(JsonLdConsts.TYPE);
        if (rawType instanceof String) {
            String outVertexLabel = (String) rawType;

            // Detect type changes (assumes we are invoked with sorted input!)
            if (!outVertexLabel.equals(type)) {
                streamVertices();
                type = outVertexLabel;
            }

            // Accumulate the node state
            nodes.add(node);
            node.keySet().forEach(k -> schema.put(k, null));
            Object outVertexId = node.get(JsonLdConsts.ID);
            identifierTypes.computeIfAbsent(outVertexLabel, SqlgNodeAccumulator::newIdentifierSet).put(outVertexId);
            wrapper().getNodeObjectProperties(node,
                    (edgeLabel, inVertexId) -> edges.put(EdgeKey.of(edgeLabel, outVertexLabel), Pair.of(outVertexId, inVertexId)));
        }
        return this;
    }

    public SqlgNodeAccumulator combine(SqlgNodeAccumulator other) {
        // Do not discard the other instance if it still contain vertex state
        checkState(other.nodes.isEmpty(), "must flush vertex state prior to merging");

        // Combine edge related state
        edges.putAll(other.edges);
        other.identifierTypes.forEach((type, identifiers) -> identifierTypes.merge(type, identifiers, SqlgNodeAccumulator::combineBloomFilter));

        return this;
    }

    public SqlgNodeAccumulator streamVertices() {
        if (!nodes.isEmpty() && !schema.isEmpty()) {
            // Drain the buffer
            for (Map<String, Object> node : nodes) {
                Map<String, Object> effectiveNode = node;
                if (effectiveNode.size() != schema.size()) {
                    effectiveNode = new HashMap<>(schema);
                    effectiveNode.putAll(node);
                }
                graph().streamVertex(wrapper().getNodeProperties(effectiveNode, false));
            }

            // Record the flush so we can stream a different type of vertex
            wrapper().flushTx();
        }

        type = null;
        nodes.clear();
        schema.clear();
        return this;
    }

    @Override
    public void finish() throws NodeDoesNotExistException {
        // Flatten the labels that were encountered
        identifierTypes.keySet().forEach(this::flattenVertices);

        // Drain the buffer
        for (Map.Entry<SqlgNodeAccumulator.EdgeKey, Collection<Pair<Object, Object>>> edge : edges.asMap().entrySet()) {
            String edgeLabel = edge.getKey().edgeLabel;
            Multimap<String, Pair<Object, Object>> effectiveEdges = findEdgesByInVertexLabel(edgeLabel, edge.getValue());
            if (!effectiveEdges.isEmpty()) {
                String outVertexLabel = edge.getKey().outVertexLabel;
                Pair<String, String> idFields = Pair.of(wrapper().mapper().identifierKey().get(), wrapper().mapper().identifierKey().get());

                // TODO Write partitions on the edges?!
                effectiveEdges.asMap().forEach((inVertexLabel, uids) -> graph().bulkAddEdges(outVertexLabel, inVertexLabel, edgeLabel, idFields, uids));

                // Record the flush so we can stream a different type of edge
                wrapper().flushTx();
            }
        }

        edges.clear();
        identifierTypes.clear();
    }

    /**
     * This is where things get tricky. Since the JSON-LD "edge" is just a reference, we need to reconstruct the
     * "in-vertex" label. Caching the entire identifier set is prohibitive, thus the bloom filters keyed by label.
     * Only one bloom filter should claim an identifier, however there may be multiple matches in the false-positive
     * case. We can accept false-positives by asking Sqlg to bulk create an edge to a vertex that does not exist
     * (bulk edge creation is accomplished using a JOIN that will not produce an edge row for the false-positives).
     */
    private Multimap<String, Pair<Object, Object>> findEdgesByInVertexLabel(String edgeLabel, Collection<Pair<Object, Object>> uids)
            throws NodeDoesNotExistException {

        // Kind of like a non-distinct "group-by" operation
        // TODO What about duplicates?
        Multimap<String, Pair<Object, Object>> result = HashMultimap.create();
        for (Pair<Object, Object> uid : uids) {
            boolean found = false;
            for (Map.Entry<String, BloomFilter<Object>> idsByType : identifierTypes.entrySet()) {
                if (idsByType.getValue().mightContain(uid.getRight())) {
                    result.put(idsByType.getKey(), uid);
                    found = true;
                }
            }

            // If no bloom filter matched, we definitely never saw the requested identifier and should fail
            if (!found) {
                throw new NodeDoesNotExistException(uid.getLeft(), edgeLabel, uid.getRight());
            }
        }
        return result;
    }

    /**
     * This can be extremely inefficient when there are a significant number of "split nodes".
     */
    private void flattenVertices(String type) {
        // Collapse any split nodes (i.e. nodes with the same JSON-LD identifier)
        wrapper().groupByMultiple(type, wrapper().mapper().identifierKey().get())
                .forEachRemaining(duplicates -> duplicates.stream().reduce((left, right) -> {
                    wrapper().mergeProperties(left, right);
                    right.remove();

                    // Periodically flush
                    wrapper().batchFlushTx();
                    return left;
                }));

        // Flush the remaining results
        wrapper().flushTx();
    }
}
