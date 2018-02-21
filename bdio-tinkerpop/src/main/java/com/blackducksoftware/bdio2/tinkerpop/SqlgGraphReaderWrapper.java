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

import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static java.util.stream.Collectors.joining;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Topology;

/**
 * Specialization of the read graph context to use when the underlying graph is a Sqlg graph.
 *
 * @author jgustie
 */
class SqlgGraphReaderWrapper extends GraphReaderWrapper {

    /**
     * Flag indicating if the graph supports batch mode or not.
     */
    private final boolean supportsBatchMode;

    protected SqlgGraphReaderWrapper(SqlgGraph sqlgGraph, GraphMapper mapper, List<TraversalStrategy<?>> strategies, Optional<Object> expandContext,
            int batchSize) {
        super(sqlgGraph, mapper, strategies, expandContext, batchSize);
        this.supportsBatchMode = sqlgGraph.features().supportsBatchMode();
    }

    @Override
    public SqlgGraph graph() {
        return (SqlgGraph) super.graph();
    }

    @Override
    public void startBatchTx() {
        // (Re-)enable batch mode if it is supported
        super.startBatchTx();
        if (supportsBatchMode) {
            graph().tx().normalBatchModeOn();
        }
    }

    @Override
    public long countVerticesByLabel(String label) {
        // NOTE: This is modified code from `SqlgGraph.countVertices()`

        // Determine where the vertices are stored
        SchemaTable schemaTable = SchemaTable.from(graph(), label)
                .withPrefix(Topology.VERTEX_PREFIX);

        // Build the query
        StringBuilder sql = new StringBuilder()
                .append("SELECT COUNT(1) FROM ")
                .append(graph().getSqlDialect().maybeWrapInQoutes(schemaTable.getSchema()))
                .append('.')
                .append(graph().getSqlDialect().maybeWrapInQoutes(schemaTable.getTable()));

        // Honor the partition strategy, if configured with read partitions (assume there is probably just one)
        // TODO Use statement parameters
        if (strategies().anyMatch(s -> s instanceof PartitionStrategy && !((PartitionStrategy) s).getReadPartitions().isEmpty())) {
            sql.append(" WHERE ").append(strategies().flatMap(ofType(PartitionStrategy.class))
                    .flatMap(s -> {
                        String field = graph().getSqlDialect().maybeWrapInQoutes(s.getPartitionKey());
                        return s.getReadPartitions().stream().map(p -> field + " = '" + p + "'");
                    })
                    .collect(joining(" OR ")));
        }

        if (graph().getSqlDialect().needsSemicolon()) {
            sql.append(';');
        }

        // Execute the query
        Connection conn = graph().tx().getConnection();
        try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
            ResultSet rs = preparedStatement.executeQuery();
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            // Wrapping SQLException with a RuntimeException is consistent with how Sqlg behaves
            throw new RuntimeException(e);
        }
    }

}
