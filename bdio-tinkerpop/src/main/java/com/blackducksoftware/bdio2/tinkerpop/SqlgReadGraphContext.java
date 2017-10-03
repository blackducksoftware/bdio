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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.umlg.sqlg.structure.IndexType;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaManager;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
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

        // Pre-populate vertex tables
        sqlgGraph.tx().open();
        try {
            mapper().metadataLabel().ifPresent(this::defineVertexLabel);
            mapper().forEachTypeLabel(this::defineVertexLabel);
            mapper().forEachEmbeddedLabel(this::defineVertexLabel);
            sqlgGraph.tx().commit();
        } finally {
            sqlgGraph.tx().rollback();
        }
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
                .withPrefix(SchemaManager.VERTEX_PREFIX);

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
     * Defines the topology for a specific vertex label. Most labels contain the same base set of properties.
     */
    private void defineVertexLabel(String label) {
        // TODO We need a unique constraint on _partition/_id for all labels (if applicable)
        // TODO We need a unique constraint on _partition/path for files (if applicable)

        VertexLabel vertexLabel = sqlgGraph.getTopology().ensureVertexLabelExist(label);

        mapper().partitionStrategy().ifPresent(strategy -> {
            vertexLabel.ensurePropertiesExist(Collections.singletonMap(strategy.getPartitionKey(), PropertyType.STRING));
            vertexLabel.getProperty(strategy.getPartitionKey())
                    .ifPresent(property -> vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, Collections.singletonList(property)));
        });

        mapper().identifierKey().ifPresent(key -> {
            vertexLabel.ensurePropertiesExist(Collections.singletonMap(key, PropertyType.STRING));
            vertexLabel.getProperty(key)
                    .ifPresent(property -> vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, Collections.singletonList(property)));
        });

        if (label.equals(Bdio.Class.File.name())) {
            vertexLabel.ensurePropertiesExist(Collections.singletonMap(Bdio.DataProperty.path.name(), PropertyType.STRING));
            vertexLabel.getProperty(Bdio.DataProperty.path.name())
                    .ifPresent(property -> vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, Collections.singletonList(property)));

            Map<String, PropertyType> parentEdgeProperties = new LinkedHashMap<>();
            mapper().implicitKey().ifPresent(key -> {
                vertexLabel.ensurePropertiesExist(Collections.singletonMap(key, PropertyType.BOOLEAN));
                parentEdgeProperties.put(key, PropertyType.BOOLEAN);
            });
            sqlgGraph.getTopology().ensureEdgeLabelExist(Bdio.ObjectProperty.parent.name(), vertexLabel, vertexLabel, parentEdgeProperties);
        }

        mapper().unknownKey().ifPresent(key -> {
            // TODO PropertyType.JSON?
            vertexLabel.ensurePropertiesExist(Collections.singletonMap(key, PropertyType.STRING));
        });
    }

}
