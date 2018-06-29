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

import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.joining;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.RecordId;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Topology;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
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
                flush();
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

    public SqlgNodeAccumulator flush() {
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

        // Get the list of properties that go on all edges
        List<Object> edgePropertyList = new ArrayList<>();
        wrapper().forEachPartition((k, v) -> {
            edgePropertyList.add(k);
            edgePropertyList.add(v);
        });
        Object[] edgeProperties = edgePropertyList.toArray();

        // Drain the buffer
        for (Map.Entry<SqlgNodeAccumulator.EdgeKey, Collection<Pair<Object, Object>>> edge : edges.asMap().entrySet()) {
            String edgeLabel = edge.getKey().edgeLabel;
            Multimap<String, Pair<Object, Object>> effectiveEdges = findEdgesByInVertexLabel(edgeLabel, edge.getValue());
            if (!effectiveEdges.isEmpty()) {
                String outVertexLabel = edge.getKey().outVertexLabel;
                Pair<String, String> idFields = Pair.of(wrapper().mapper().identifierKey().get(), wrapper().mapper().identifierKey().get());
                effectiveEdges.asMap()
                        .forEach((inVertexLabel, uids) -> wrapper().bulkAddEdges(outVertexLabel, inVertexLabel, edgeLabel, idFields, uids, edgeProperties));

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
    @SuppressWarnings("ReturnValueIgnored")
    private void flattenVertices(String type) {
        // TODO This needs to be optimized, it can take 10x longer then the actual import

        // Collapse any split nodes (i.e. nodes with the same JSON-LD identifier)
        groupByMultiple(type, wrapper().mapper().identifierKey().get())
                .forEachRemaining(duplicates -> {
                    // Ignore the result of the reduction, it will just be the first element from `duplicates`
                    duplicates.stream().reduce((left, right) -> {
                        wrapper().mergeProperties(left, right);
                        right.remove();

                        // Periodically flush
                        wrapper().batchFlushTx();
                        return left;
                    });
                });

        // Flush the remaining results
        wrapper().flushTx();
    }

    private GraphTraversal<?, Collection<Vertex>> groupByMultiple(String label, String groupByKey) {
        // This is much faster to compute in the database
        String whereClause = wrapper().strategies()
                .flatMap(ofType(PartitionStrategy.class))
                .filter(ps -> !ps.getReadPartitions().isEmpty())
                .map(ps -> new StringBuilder()
                        .append(graph().getSqlDialect().maybeWrapInQoutes(ps.getPartitionKey()))
                        .append(ps.getReadPartitions().size() == 1 ? " = " : " IN (")
                        .append(ps.getReadPartitions().stream()
                                .map(rp -> graph().getSqlDialect().valueToValuesString(PropertyType.STRING, rp))
                                .collect(joining(", ")))
                        .append(ps.getReadPartitions().size() == 1 ? "" : ")"))
                .collect(joining(" ) AND ( "));

        return wrapper().traversal().inject(SchemaTable.from(graph(), label))
                .flatMap(t -> {
                    SchemaTable schemaTable = t.get();
                    StringBuilder sql = new StringBuilder()
                            .append("SELECT array_agg(")
                            .append(graph().getSqlDialect().maybeWrapInQoutes(Topology.ID))
                            .append(") FROM ")
                            .append(graph().getSqlDialect().maybeWrapInQoutes(schemaTable.getSchema()))
                            .append('.')
                            .append(graph().getSqlDialect().maybeWrapInQoutes(schemaTable.withPrefix(Topology.VERTEX_PREFIX).getTable()));
                    if (!whereClause.isEmpty()) {
                        sql.append(" WHERE (").append(whereClause).append(" )");
                    }
                    sql.append(" GROUP BY ")
                            .append(graph().getSqlDialect().maybeWrapInQoutes(groupByKey))
                            .append(" HAVING count(1) > 1")
                            .append(graph().getSqlDialect().needsSemicolon() ? ";" : "");

                    Connection conn = graph().tx().getConnection();
                    try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                        try (ResultSet rs = preparedStatement.executeQuery()) {
                            List<Object[]> result = new ArrayList<>();
                            while (rs.next()) {
                                Object array = rs.getArray(1).getArray();
                                Object[] row = new Object[Array.getLength(array)];
                                for (int i = 0; i < row.length; ++i) {
                                    row[i] = RecordId.from(schemaTable, (Long) Array.get(array, i));
                                }
                                Arrays.sort(row);
                                result.add(row);
                            }
                            return result.iterator();
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(t -> Lists.newArrayList(graph().vertices(t.get())));
    }
}
