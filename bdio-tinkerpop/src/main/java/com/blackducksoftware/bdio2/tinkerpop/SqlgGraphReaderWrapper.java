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
package com.blackducksoftware.bdio2.tinkerpop;

import static com.blackducksoftware.common.base.ExtraOptionals.and;
import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static java.lang.Boolean.FALSE;
import static java.util.stream.Collectors.joining;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.RecordId;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Topology;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.Lists;

/**
 * Specialization of the read graph context to use when the underlying graph is a Sqlg graph.
 *
 * @author jgustie
 */
class SqlgGraphReaderWrapper extends GraphReaderWrapper {

    /**
     * Until issue #296 is resolved we cannot flush or commit concurrently because of the potential for interleaving
     * {@code COPY} statements getting assigned the wrong identifier.
     */
    private static final Object flushLock = new Object();

    /**
     * Flag indicating if the graph supports batch mode or not.
     */
    private final boolean supportsBatchMode;

    protected SqlgGraphReaderWrapper(SqlgGraph sqlgGraph, GraphMapper mapper, List<TraversalStrategy<?>> strategies, Optional<Object> expandContext,
            int batchSize) {
        // Sqlg issue #296 workaround: the extra synchronization overhead requires a smaller default batch size
        super(sqlgGraph, mapper, strategies, expandContext, batchSize != 10_000 ? batchSize : 2_000);
        this.supportsBatchMode = sqlgGraph.features().supportsBatchMode();
    }

    @Override
    public SqlgGraph graph() {
        return (SqlgGraph) super.graph();
    }

    @Override
    public void startBatchTx() {
        // (Re-)enable batch mode if it is supported
        if (supportsBatchMode) {
            graph().tx().normalBatchModeOn();
        }
    }

    @Override
    public void flushTx() {
        if (supportsBatchMode) {
            // Sqlg issue #296 workaround
            synchronized (flushLock) {
                graph().tx().flush();
            }
        }
    }

    @Override
    public void commitTx() {
        // Sqlg issue #296 workaround
        synchronized (flushLock) {
            super.commitTx();
        }
    }

    @Override
    public void createMetadata(BdioMetadata metadata) {
        // If we are streaming, we cannot create a new metadata vertex unless it is also streamed
        if (graph().tx().isInStreamingBatchMode()) {
            mapper().metadataLabel().ifPresent(metadataLabel -> {
                Object[] metadataKeyValues = getMetadataProperties(metadata);
                Vertex metadataVertex = traversal().V().hasLabel(metadataLabel).tryNext().orElse(null);
                if (metadataVertex != null) {
                    ElementHelper.attachProperties(metadataVertex, metadataKeyValues);
                } else {
                    flushTx();
                    graph().streamVertex(metadataKeyValues);
                    flushTx();
                }
            });
        } else {
            super.createMetadata(metadata);
        }
    }

    @Override
    public GraphTraversal<?, Collection<Vertex>> groupByMultiple(String label, String groupByKey) {
        // This is much faster to compute in the database
        String whereClause = strategies()
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

        return traversal().inject(SchemaTable.from(graph(), label))
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

    @Override
    public void getNodeDataProperties(Map<String, Object> node, BiConsumer<Object, Object> dataPropertyHandler, boolean includeId) {
        // Sqlg issue #294 workaround
        BiConsumer<Object, Object> handler = dataPropertyHandler;
        boolean isMetadata = and(Optional.ofNullable(node.get(JsonLdConsts.TYPE)), mapper().metadataLabel(), Objects::equals).orElse(FALSE);
        if (!isMetadata && (graph().tx().isInNormalBatchMode() || graph().tx().isInStreamingBatchMode())) {
            handler = (key, value) -> {
                if (!(value instanceof ZonedDateTime)) {
                    dataPropertyHandler.accept(key, value);
                }
            };
        }
        super.getNodeDataProperties(node, handler, includeId);
    }

}
