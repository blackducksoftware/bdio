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

import static java.util.stream.Collectors.joining;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.Topology;

/**
 * Specialization of the read graph context to use when the underlying graph is a Sqlg graph.
 *
 * @author jgustie
 */
class SqlgReadGraphContext extends ReadGraphContext {

    /**
     * Typed reference to the Sqlg graph.
     */
    private final SqlgGraph sqlgGraph;

    /**
     * Flag indicating if the graph supports batch mode or not.
     */
    private final boolean supportsBatchMode;

    protected SqlgReadGraphContext(SqlgGraph sqlgGraph, GraphMapper mapper, int batchSize) {
        super(sqlgGraph, mapper, batchSize);
        this.sqlgGraph = Objects.requireNonNull(sqlgGraph);
        this.supportsBatchMode = sqlgGraph.features().supportsBatchMode();
    }

    @Override
    public void startBatchTx() {
        // (Re-)enable batch mode if it is supported
        super.startBatchTx();
        if (supportsBatchMode) {
            sqlgGraph.tx().normalBatchModeOn();
        }
    }

    @Override
    public long countVerticesByLabel(String label) {
        // NOTE: This is modified code from `SqlgGraph.countVertices()`

        // Determine where the vertices are stored
        SchemaTable schemaTable = SchemaTable.from(sqlgGraph, label)
                .withPrefix(Topology.VERTEX_PREFIX);

        // Build the query
        StringBuilder sql = new StringBuilder()
                .append("SELECT COUNT(1) FROM ")
                .append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(schemaTable.getSchema()))
                .append('.')
                .append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(schemaTable.getTable()));

        // Honor the partition strategy, if configured with read partitions (assume there is probably just one)
        topology().partitionStrategy()
                .filter(ps -> !ps.getReadPartitions().isEmpty())
                .ifPresent(ps -> {
                    String field = sqlgGraph.getSqlDialect().maybeWrapInQoutes(ps.getPartitionKey());
                    // TODO Use statement parameters
                    sql.append(ps.getReadPartitions().stream()
                            .collect(joining("' OR " + field + " = \"", " WHERE " + field + " = '", "'")));
                });

        if (sqlgGraph.getSqlDialect().needsSemicolon()) {
            sql.append(';');
        }

        // Execute the query
        Connection conn = sqlgGraph.tx().getConnection();
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
