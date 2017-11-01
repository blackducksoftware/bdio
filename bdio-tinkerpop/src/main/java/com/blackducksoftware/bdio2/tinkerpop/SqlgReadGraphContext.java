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

import static org.umlg.sqlg.structure.PropertyType.BOOLEAN;
import static org.umlg.sqlg.structure.PropertyType.STRING;
import static org.umlg.sqlg.structure.PropertyType.STRING_ARRAY;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.umlg.sqlg.structure.IndexType;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.Topology;
import org.umlg.sqlg.structure.VertexLabel;

import com.blackducksoftware.bdio2.Bdio;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

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

    /**
     * A bloom filter used to save a database SELECT operation.
     */
    private final BloomFilter<String> uniqueIdentifiers;

    protected SqlgReadGraphContext(SqlgGraph sqlgGraph, GraphMapper mapper, int batchSize, int expectedNodeCount) {
        super(sqlgGraph, mapper, batchSize);
        this.sqlgGraph = Objects.requireNonNull(sqlgGraph);
        this.supportsBatchMode = sqlgGraph.features().supportsBatchMode();
        uniqueIdentifiers = BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedNodeCount);

        defineTopology();
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
                .append('"').append(schemaTable.getSchema()).append('"')
                .append('.')
                .append('"').append(schemaTable.getTable()).append('"');
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

    @Override
    protected boolean isIdentifierUnique(String identifier) {
        return uniqueIdentifiers.put(identifier);
    }

    /**
     * Define a minimal topology to help reduce the number of dynamic topology changes.
     */
    private void defineTopology() {
        sqlgGraph.tx().open();
        try {
            mapper().metadataLabel().ifPresent(this::defineVertexLabel);
            mapper().forEachTypeLabel(this::defineVertexLabel);
            mapper().forEachEmbeddedLabel(this::defineVertexLabel);
            defineEdgeLabels();

            sqlgGraph.tx().commit();
        } catch (RuntimeException | Error e) {
            sqlgGraph.tx().rollback();
            throw e;
        }
    }

    /**
     * Defines the topology for a specific vertex label. Most labels contain the same base set of properties.
     */
    private void defineVertexLabel(String label) {
        Topology topology = sqlgGraph.getTopology();

        // TODO We need a unique constraint on _partition/_id for all labels (if applicable)
        // TODO We need a unique constraint on _partition/path for files (if applicable)

        // This is just to help make the code look more uniform below
        Optional<String> filePathKey = Optional.of(label).filter(Predicate.isEqual(Bdio.Class.File.name()))
                .map(x -> Bdio.DataProperty.path.name());
        Optional<String> fileFingerprintKey = Optional.of(label).filter(Predicate.isEqual(Bdio.Class.File.name()))
                .map(x -> Bdio.DataProperty.fingerprint.name());

        // Define the initial columns used to persist this vertex label
        Map<String, PropertyType> columns = new LinkedHashMap<>();
        mapper().partitionStrategy().map(PartitionStrategy::getPartitionKey).ifPresent(c -> columns.put(c, STRING));
        mapper().identifierKey().ifPresent(c -> columns.put(c, STRING));
        filePathKey.ifPresent(c -> columns.put(c, STRING));
        fileFingerprintKey.ifPresent(c -> columns.put(c, STRING_ARRAY));
        mapper().implicitKey().ifPresent(c -> columns.put(c, BOOLEAN));
        mapper().unknownKey().ifPresent(c -> columns.put(c, STRING)); // TODO PropertyType.JSON

        // Ensure the vertex label exists with the proper columns
        VertexLabel vertexLabel = topology.ensureVertexLabelExist(label, columns);

        // Add indexes
        mapper().partitionStrategy().map(PartitionStrategy::getPartitionKey)
                .flatMap(vertexLabel::getProperty).map(Collections::singletonList)
                .ifPresent(properties -> vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, properties));
        mapper().identifierKey()
                .flatMap(vertexLabel::getProperty).map(Collections::singletonList)
                .ifPresent(properties -> vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, properties));
        filePathKey
                .flatMap(vertexLabel::getProperty).map(Collections::singletonList)
                .ifPresent(properties -> vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, properties));
    }

    /**
     * Defines the topology for some of the edge labels.
     */
    private void defineEdgeLabels() {
        Topology topology = sqlgGraph.getTopology();

        // In general we do not store BDIO information on edges, however there are a few common properties we use
        Map<String, PropertyType> properties = new LinkedHashMap<>();
        mapper().partitionStrategy().map(PartitionStrategy::getPartitionKey).ifPresent(c -> properties.put(c, STRING));
        mapper().implicitKey().ifPresent(c -> properties.put(c, BOOLEAN));

        // Collect the vertex labels used to define edges
        VertexLabel projectVertex = topology.ensureVertexLabelExist(Bdio.Class.Project.name());
        VertexLabel fileVertex = topology.ensureVertexLabelExist(Bdio.Class.File.name());

        // Define a small number of edges (those that most commonly used or are extremely dense)
        topology.ensureEdgeLabelExist(Bdio.ObjectProperty.base.name(), projectVertex, fileVertex, properties);
        topology.ensureEdgeLabelExist(Bdio.ObjectProperty.parent.name(), fileVertex, fileVertex, properties);
    }

}
