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
package com.blackducksoftware.bdio2.tinkerpop.sqlg;

import static com.blackducksoftware.common.base.ExtraConsumers.withBoth;
import static com.blackducksoftware.common.base.ExtraThrowables.illegalState;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.joining;
import static org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality.single;
import static org.umlg.sqlg.structure.topology.Topology.EDGE_PREFIX;
import static org.umlg.sqlg.structure.topology.Topology.VERTEX_PREFIX;

import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.sql.dialect.PostgresDialect;
import org.umlg.sqlg.sql.dialect.SqlDialect;
import org.umlg.sqlg.structure.BatchManager;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgExceptions;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.PropertyColumn;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;
import org.umlg.sqlg.util.SqlgUtil;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoReaderSpi;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.PrimitiveSink;

import io.reactivex.rxjava3.core.Flowable;

final class SqlgBlackDuckIoReader extends BlackDuckIoReaderSpi {

    /**
     * A key type to use for grouping potential edges.
     */
    private static class EdgeKey {

        /**
         * The practical limit of this combination is probably around 50-100 instances (sum of the size of all
         * {@code @Domain} values appearing on the object properties), in theory it could be higher if the user supplies
         * additional object properties. Regardless, the size of this map is very small relative to the potential number
         * of instances being created.
         */
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
            if (obj == this) {
                return true;
            } else if (obj instanceof EdgeKey) {
                return edgeLabel.equals(((EdgeKey) obj).edgeLabel)
                        && outVertexLabel.equals(((EdgeKey) obj).outVertexLabel);
            } else {
                return false;
            }
        }
    }

    private class SqlgNodeAccumulator {

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
        private final Multimap<EdgeKey, Pair<Object, Object>> edges = HashMultimap.create();

        /**
         * Set of all node property keys seen since the last flush. When invoking the {@link #nodeConsumer}, every map
         * must have the same key set.
         */
        private final Map<String, Object> schema = new HashMap<>();

        /**
         * Mapping of vertex label types to their probabilistic identifier sets.
         */
        private final Map<String, BloomFilter<Object>> identifierTypes = new HashMap<>();

        /**
         * An estimate of the number of modifications made to a table.
         */
        // TODO Should we just check 'pg_stat_user_tables' since this is really just for Postgres?
        private final Multiset<SchemaTable> modificationEstimate = LinkedHashMultiset.create();

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
                identifierTypes.computeIfAbsent(outVertexLabel, SqlgBlackDuckIoReader::newIdentifierSet).put(outVertexId);
                getNodeEdges(node, (edgeLabel, inVertexId) -> {
                edges.put(EdgeKey.of(edgeLabel, outVertexLabel), Pair.of(outVertexId, inVertexId));
                });
                                               
            }
            return this;
        }

        public SqlgNodeAccumulator combine(SqlgNodeAccumulator other) {
            // Do not discard the other instance if it still contain vertex state
            checkState(other.nodes.isEmpty(), "must flush vertex state prior to merging");

            // Combine edge related state
            edges.putAll(other.edges);
            other.identifierTypes.forEach((type, identifiers) -> identifierTypes.merge(type, identifiers, SqlgBlackDuckIoReader::combineBloomFilter));

            return this;
        }

        public SqlgNodeAccumulator flush() {
            if (!nodes.isEmpty() && !schema.isEmpty()) {
                // Drain the buffer
                int count = 0;
                for (Map<String, Object> node : nodes) {
                    Map<String, Object> effectiveNode = node;
                    if (effectiveNode.size() != schema.size()) {
                        effectiveNode = new HashMap<>(schema);
                        effectiveNode.putAll(node);
                    }
                    List<Object> keyValues = new ArrayList<>();
                    getTraversalProperties(withBoth(keyValues::add), true);
                    getNodeProperties(effectiveNode, (k, v) -> {
                        // Sqlg issue #294 workaround
                        if (k.equals(Bdio.DataProperty.lastModifiedDateTime.name())
                                || k.equals(Bdio.DataProperty.creationDateTime.name())) {
                            return;
                        }

                        if (k != T.id) {
                            keyValues.add(k);
                            keyValues.add(v);
                        }
                    });
                    graph().streamVertex(keyValues.toArray());
                    if (count++ > batchSize) {
                        graph().tx().flush();
                        count = 0;
                    }
                }

                // Record the flush so we can stream a different type of vertex
                graph().tx().flush();
            }

            type = null;
            nodes.clear();
            schema.clear();
            return this;
        }

        public void finish() throws NodeDoesNotExistException {
            // Commit all the vertices to the database
            flush();
            graph().tx().commit();
            doVacuumAnalyze();

            // Flatten the labels that were encountered
            identifierTypes.keySet().forEach(this::flattenVertices);
            graph().tx().commit();
            doVacuumAnalyze();

            // Get the list of properties that go on all edges
            List<Object> edgePropertyList = new ArrayList<>();
            getTraversalProperties((k, v) -> {
                edgePropertyList.add(k);
                edgePropertyList.add(v);
            }, true);
            Object[] edgeProperties = edgePropertyList.toArray();
            Pair<String, String> idFields = Pair.of(options().identifierKey().get(), options().identifierKey().get());

            // Drain the edge buffer
            graph().tx().streamingBatchModeOn();
            for (Map.Entry<EdgeKey, Collection<Pair<Object, Object>>> edge : edges.asMap().entrySet()) {
                String edgeLabel = edge.getKey().edgeLabel;
                Multimap<String, Pair<Object, Object>> effectiveEdges = findEdgesByInVertexLabel(edgeLabel, edge.getValue());
                if (!effectiveEdges.isEmpty()) {
                    String outVertexLabel = edge.getKey().outVertexLabel;
                    effectiveEdges.asMap().forEach((inVertexLabel, uids) -> {
                        bulkAddEdges(outVertexLabel, inVertexLabel, edgeLabel, idFields, uids, edgeProperties);
                        modificationEstimate.add(SchemaTable.from(graph(), edgeLabel).withPrefix(EDGE_PREFIX), uids.size());
                    });

                    // Record the flush so we can stream a different type of edge
                    graph().tx().flush();
                }
            }
            graph().tx().commit();
            doVacuumAnalyze();

            // Free the buffers
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
        private void flattenVertices(String label) {
            SqlDialect dialect = graph().getSqlDialect();
            SchemaTable table = SchemaTable.from(graph(), label).withPrefix(Topology.VERTEX_PREFIX);

            SecureRandom random = new SecureRandom();
            byte bytes[] = new byte[6];
            random.nextBytes(bytes);
            String tempTableName = (dialect.needsTemporaryTablePrefix() ? dialect.temporaryTablePrefix() : "") + Base64.getEncoder().encodeToString(bytes);
            SqlgQueryBuilder sql = new SqlgQueryBuilder(dialect);

            // We cannot have an empty property map after we remove our GROUP BY key
            Map<String, PropertyColumn> properties = new LinkedHashMap<>(graph().getTopology().getPropertiesFor(table));
            properties.remove(options().identifierKey().get());
            if (properties.isEmpty()) {
                graph().getTopology().ensureVertexLabelPropertiesExist(label, Collections.singletonMap("_flatten_dummy", PropertyType.from(0)));
                properties.putAll(graph().getTopology().getPropertiesFor(table));
                properties.remove(options().identifierKey().get());
            }

            Map<String, String> partitions = new LinkedHashMap<>();
            getTraversalProperties((k, v) -> partitions.put((String) k, dialect.valueToValuesString(properties.get(k).getPropertyType(), v)), false);

            sql.clear();
            sql.append(dialect.createTemporaryTableStatement())
                    .maybeWrapInQuotes(tempTableName)
                    .append(" ON COMMIT DROP AS (")
                    .append("\nSELECT \n  ")
                    .maybeWrapInQuotes(options().identifierKey().get())
                    .forEachAppend(Stream.concat(Stream.of(Topology.ID), properties.keySet().stream()),
                            (k, s) -> s.append("first(").maybeWrapInQuotes(k).append(") AS ").maybeWrapInQuotes(k),
                            ",\n  ", ",\n  ", "")
                    .append("\nFROM (SELECT * FROM ")
                    .maybeWrapInQuotes(table.getTable())
                    .forEachAppend(partitions.entrySet().stream(),
                            (e, s) -> s.maybeWrapInQuotes(e.getKey()).append(" = ").append(e.getValue()),
                            " AND ", " WHERE ", "")
                    .append(" ORDER BY ")
                    .maybeWrapInQuotes(options().identifierKey().get())
                    .append(", ")
                    .maybeWrapInQuotes(Topology.ID)
                    .append(") t GROUP BY ")
                    .maybeWrapInQuotes(options().identifierKey().get())
                    .append(" HAVING COUNT(1) > 1\n) WITH DATA")
                    .semicolon();
            String createTempTableQuery = sql.toString();

            sql.clear();
            sql.append("DELETE FROM ")
                    .maybeWrapInQuotes(table.getTable())
                    .append(" USING ")
                    .maybeWrapInQuotes(tempTableName)
                    .append(" WHERE ")
                    .qualify(table.getTable(), options().identifierKey().get())
                    .append(" = ")
                    .qualify(tempTableName, options().identifierKey().get())
                    .forEachAppend(partitions.entrySet().stream(),
                            (e, s) -> s.qualify(table.getTable(), e.getKey()).append(" = ").append(e.getValue()),
                            " AND ", " AND ", "")
                    .forEachAppend(partitions.entrySet().stream(),
                            (e, s) -> s.qualify(tempTableName, e.getKey()).append(" = ").append(e.getValue()),
                            " AND ", " AND ", "")
                    .semicolon();
            String deleteQuery = sql.toString();

            sql.clear();
            sql.append("INSERT INTO ")
                    .maybeWrapInQuotes(table.getTable())
                    .append(" (")
                    .maybeWrapInQuotes(options().identifierKey().get(), Topology.ID)
                    .forEachAppend(properties.keySet().stream(), (k, s) -> s.maybeWrapInQuotes(k), ", ", ", ", "")
                    .append(") SELECT ")
                    .maybeWrapInQuotes(options().identifierKey().get(), Topology.ID)
                    .forEachAppend(properties.keySet().stream(), (k, s) -> s.maybeWrapInQuotes(k), ", ", ", ", "")
                    .append(" FROM ")
                    .maybeWrapInQuotes(tempTableName)
                    .semicolon();
            String insertQuery = sql.toString();

            Connection connection = graph().tx().getConnection();
            try (Statement statement = connection.createStatement()) {
                // Believe it or not, it is the deleteQuery that is the long pole here
                statement.executeUpdate(createTempTableQuery);
                statement.executeUpdate(deleteQuery);
                int insertCount = statement.executeUpdate(insertQuery);

                // Keep track of changes to the table so we can clean up later
                modificationEstimate.add(table, insertCount);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Performs a VACUUM ANALYZE on Postgres databases for any pending tables with a large number of mutations.
         */
        private void doVacuumAnalyze() {
            SqlDialect dialect = graph().getSqlDialect();
            if (modificationEstimate.size() > 10_000 && dialect.isPostgresql()) {
                try (Connection conn = graph().getConnection()) {
                    try (Statement statement = conn.createStatement()) {
                        Iterator<Multiset.Entry<SchemaTable>> i = modificationEstimate.entrySet().iterator();
                        while (i.hasNext()) {
                            Multiset.Entry<SchemaTable> e = i.next();
                            if (e.getCount() > 10_000) {
                                SqlgQueryBuilder sql = new SqlgQueryBuilder(dialect)
                                        .append("VACUUM ANALYZE ")
                                        .maybeWrapInQuotes(e.getElement().getTable())
                                        .semicolon();
                                statement.execute(sql.toString());
                                i.remove();
                            }
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        private <LL, RR> void bulkAddEdges(String outVertexLabel, String inVertexLabel, String edgeLabel,
                Pair<String, String> idFields, Collection<Pair<LL, RR>> uids, Object... keyValues) {
            SqlgGraph sqlgGraph = graph();
            if (!sqlgGraph.tx().isInStreamingBatchMode() && !sqlgGraph.tx().isInStreamingWithLockBatchMode()) {
                throw SqlgExceptions.invalidMode("Transaction must be in "
                        + BatchManager.BatchModeType.STREAMING + " or "
                        + BatchManager.BatchModeType.STREAMING_WITH_LOCK + " mode for bulkAddEdges");
            } else if (uids.isEmpty()) {
                return;
            }

            Map<String, Object> partitions = new LinkedHashMap<>();
            getTraversalProperties((k, v) -> {
                partitions.put(k.toString(), v);
            }, false);

            BulkAddEdgeDialect dialect = new BulkAddEdgeDialect(partitions);
            SchemaTable outSchemaTable = SchemaTable.from(sqlgGraph, outVertexLabel);
            SchemaTable inSchemaTable = SchemaTable.from(sqlgGraph, inVertexLabel);
            Triple<Map<String, PropertyType>, Map<String, Object>, Map<String, Object>> keyValueMapTriple = SqlgUtil.validateVertexKeysValues(dialect,
                    keyValues);
            dialect.bulkAddEdges(sqlgGraph, outSchemaTable, inSchemaTable, edgeLabel, idFields, uids, keyValueMapTriple.getLeft(),
                    keyValueMapTriple.getRight());
        }
    }

    // Basically a hack to duplicate a bunch of the Sqlg code to manage a partitioned bulk-add
    // TODO This shouldn't need to be a PostgresDialect, it should probably just pass through...
    @VisibleForTesting
    static class BulkAddEdgeDialect extends PostgresDialect {

        private final Logger logger = LoggerFactory.getLogger(PostgresDialect.class.getName());

        private final Map<String, Object> partitions;

        public BulkAddEdgeDialect(Map<String, Object> partitions) {
            this.partitions = Objects.requireNonNull(partitions);
        }

        // @formatter:off
        @Override
        public <L, R> void bulkAddEdges(SqlgGraph sqlgGraph, SchemaTable out, SchemaTable in, String edgeLabel, Pair<String, String> idFields, Collection<Pair<L, R>> uids, Map<String, PropertyType> edgeColumns, Map<String, Object> edgePropertyMap) {
            if (!sqlgGraph.tx().isInStreamingBatchMode() && !sqlgGraph.tx().isInStreamingWithLockBatchMode()) {
                throw SqlgExceptions.invalidMode("Transaction must be in " + BatchManager.BatchModeType.STREAMING + " or " + BatchManager.BatchModeType.STREAMING_WITH_LOCK + " mode for bulkAddEdges");
            }
            if (!uids.isEmpty()) {
                //createVertexLabel temp table and copy the uids into it
                Map<String, PropertyType> columns = new HashMap<>();
                Map<String, PropertyType> outProperties = sqlgGraph.getTopology().getTableFor(out.withPrefix(VERTEX_PREFIX));
                Map<String, PropertyType> inProperties = sqlgGraph.getTopology().getTableFor(in.withPrefix(VERTEX_PREFIX));
                PropertyType outPropertyType;
                if (idFields.getLeft().equals(Topology.ID)) {
                    outPropertyType = PropertyType.INTEGER;
                } else {
                    outPropertyType = outProperties.get(idFields.getLeft());
                }
                PropertyType inPropertyType;
                if (idFields.getRight().equals(Topology.ID)) {
                    inPropertyType = PropertyType.INTEGER;
                } else {
                    inPropertyType = inProperties.get(idFields.getRight());
                }
                columns.put("out", outPropertyType);
                columns.put("in", inPropertyType);
                SecureRandom random = new SecureRandom();
                byte bytes[] = new byte[6];
                random.nextBytes(bytes);
                String tmpTableIdentified = Base64.getEncoder().encodeToString(bytes);
                tmpTableIdentified = Topology.BULK_TEMP_EDGE + tmpTableIdentified;
                sqlgGraph.getTopology().getPublicSchema().createTempTable(tmpTableIdentified, columns);
                this._copyInBulkTempEdges(sqlgGraph, SchemaTable.of(out.getSchema(), tmpTableIdentified), uids, outPropertyType, inPropertyType);
                //executeRegularQuery copy from select. select the edge ids to copy into the new table by joining on the temp table

                Optional<VertexLabel> outVertexLabelOptional = sqlgGraph.getTopology().getVertexLabel(out.getSchema(), out.getTable());
                Optional<VertexLabel> inVertexLabelOptional = sqlgGraph.getTopology().getVertexLabel(in.getSchema(), in.getTable());
                Preconditions.checkState(outVertexLabelOptional.isPresent(), "Out VertexLabel must be present. Not found for %s", out.toString());
                Preconditions.checkState(inVertexLabelOptional.isPresent(), "In VertexLabel must be present. Not found for %s", in.toString());

                //noinspection OptionalGetWithoutIsPresent
                sqlgGraph.getTopology().ensureEdgeLabelExist(edgeLabel, outVertexLabelOptional.get(), inVertexLabelOptional.get(), edgeColumns);

                StringBuilder sql = new StringBuilder("INSERT INTO \n");
                sql.append(this.maybeWrapInQoutes(out.getSchema()));
                sql.append(".");
                sql.append(this.maybeWrapInQoutes(EDGE_PREFIX + edgeLabel));
                sql.append(" (");
                sql.append(this.maybeWrapInQoutes(out.getSchema() + "." + out.getTable() + Topology.OUT_VERTEX_COLUMN_END));
                sql.append(",");
                sql.append(this.maybeWrapInQoutes(in.getSchema() + "." + in.getTable() + Topology.IN_VERTEX_COLUMN_END));
                edgePropertyMap.keySet().forEach(k -> sql.append(',').append(this.maybeWrapInQoutes(k)));
                sql.append(") \n");
                sql.append("select _out.\"ID\" as \"");
                sql.append(out.getSchema()).append(".").append(out.getTable()).append(Topology.OUT_VERTEX_COLUMN_END);
                sql.append("\", _in.\"ID\" as \"");
                sql.append(in.getSchema()).append(".").append(in.getTable()).append(Topology.IN_VERTEX_COLUMN_END);
                sql.append("\"");
                edgePropertyMap.forEach((k, v) -> {
                    sql.append(',');
                    sql.append(this.valueToValuesString(edgeColumns.get(k), v));
                    sql.append(" as ");
                    sql.append(this.maybeWrapInQoutes(k));
                });
                sql.append(" FROM ");
                sql.append(this.maybeWrapInQoutes(in.getSchema()));
                sql.append(".");
                sql.append(this.maybeWrapInQoutes(VERTEX_PREFIX + in.getTable()));
                sql.append(" _in join ");
                sql.append(this.maybeWrapInQoutes(tmpTableIdentified)).append(" ab on ab.in = _in.").append(this.maybeWrapInQoutes(idFields.getRight())).append(" join ");
                sql.append(this.maybeWrapInQoutes(out.getSchema()));
                sql.append(".");
                sql.append(this.maybeWrapInQoutes(VERTEX_PREFIX + out.getTable()));
                sql.append(" _out on ab.out = _out.").append(this.maybeWrapInQoutes(idFields.getLeft()));
                // MODIFICATION START
                addPartitions(sql, inProperties, outProperties);
                // MODIFICATION END
                if (logger.isDebugEnabled()) {
                    logger.debug(sql.toString());
                }
                Connection conn = sqlgGraph.tx().getConnection();
                try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                    preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        // @formatter:on

        /**
         * Reflective hack to avoid duplicating too much functionality from the super class.
         */
        private <L, R> void _copyInBulkTempEdges(SqlgGraph sqlgGraph, SchemaTable schemaTable,
                Collection<Pair<L, R>> uids, PropertyType inPropertyType, PropertyType outPropertyType) {
            try {
                Method m = PostgresDialect.class.getDeclaredMethod("copyInBulkTempEdges",
                        SqlgGraph.class, SchemaTable.class, Collection.class, PropertyType.class, PropertyType.class);
                m.setAccessible(true);
                m.invoke(this, sqlgGraph, schemaTable, uids, inPropertyType, outPropertyType);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Adds a "WHERE" clause to an existing bulk edge insert to account for partitions.
         */
        private void addPartitions(StringBuilder sql, Map<String, PropertyType> inProperties, Map<String, PropertyType> outProperties) {
            if (!partitions.isEmpty()) {
                sql.append(partitions.entrySet().stream()
                        .map(e -> new StringBuilder()
                                .append("_in.").append(this.maybeWrapInQoutes(e.getKey()))
                                .append(" = ").append(this.valueToValuesString(inProperties.get(e.getKey()), e.getValue()))
                                .append(" AND _out.").append(this.maybeWrapInQoutes(e.getKey()))
                                .append(" = ").append(this.valueToValuesString(outProperties.get(e.getKey()), e.getValue()))
                                .toString())
                        .collect(joining(" AND ", " WHERE ", "")));
            }
            sql.append(this.needsSemicolon() ? ";" : "");
        }
    }

    /**
     * Sort order.
     */
    private static int nodeTypeOrder(Map<String, Object> left, Map<String, Object> right) {
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
        return BloomFilter.create(SqlgBlackDuckIoReader::identifierFunnel, expectedInsertions);
    }

    /**
     * Merges the second bloom filter into the first.
     */
    private static <E> BloomFilter<E> combineBloomFilter(BloomFilter<E> a, BloomFilter<E> b) {
        a.putAll(b);
        return a;
    }

    private final int batchSize;

    public SqlgBlackDuckIoReader(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame, int batchSize) {
        super(traversal, options, frame);
        checkArgument(traversal.getGraph() instanceof SqlgGraph, "expected SqlgGraph");
        checkArgument(options.identifierKey().isPresent(), "identifier key should be configured");
        this.batchSize = batchSize;
    }

    @Override
    protected SqlgGraph graph() {
        return (SqlgGraph) super.graph();
    }

    @Override
    public void persistMetadata(BdioMetadata metadata) {
        // If we are streaming, we cannot create a new metadata vertex unless it is also streamed
        if (graph().tx().isInStreamingBatchMode()) {
            String metadataLabel = options().metadataLabel().orElseThrow(illegalState("metadata label should be configured"));

            List<Object> keyValues = new ArrayList<>();
            BiConsumer<Object, Object> properties = (k, v) -> {
                if (k != T.id) {
                    keyValues.add(k);
                    keyValues.add(v);
                }
            };
            getMetadataProperties(metadata, properties);

            GraphTraversalSource g = traversal();
            Vertex vertex = g.V().hasLabel(metadataLabel).tryNext().orElse(null);
            if (vertex != null) {
                // Do the same thing as calling the super, just without duplicating work
                ElementHelper.attachProperties(vertex, single, keyValues.toArray());
            } else {
                // Since we are by-passing the traversal, we must add partition and constant values
                getTraversalProperties(properties, true);

                // The metadata is a new vertex so we must stream it to create it
                graph().tx().flush();
                graph().streamVertex(keyValues.toArray());
                graph().tx().flush();
            }
        } else {
            super.persistMetadata(metadata);
        }
    }

    @Override
    public Publisher<?> persistFramedEntries(Flowable<Map<String, Object>> framedEntries) {
        return framedEntries
                .map(BdioDocument::toGraphNodes)
                .map(nodes -> nodes.stream()
                        .sorted(SqlgBlackDuckIoReader::nodeTypeOrder)
                        .reduce(new SqlgNodeAccumulator(), SqlgNodeAccumulator::addNode, SqlgNodeAccumulator::combine)
                        .flush())
                .reduce(SqlgNodeAccumulator::combine)
                .doOnSuccess(SqlgNodeAccumulator::finish)
                .doOnSubscribe(x -> graph().tx().streamingBatchModeOn())
                .toFlowable()
                .doOnComplete(() -> graph().tx().commit())
                .doOnError(x -> graph().tx().rollback())
                .doOnCancel(() -> graph().tx().rollback());
    }

}
