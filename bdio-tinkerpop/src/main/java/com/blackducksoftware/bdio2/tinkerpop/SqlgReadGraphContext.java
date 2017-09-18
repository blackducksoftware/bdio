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

import java.util.Collections;
import java.util.Objects;

import org.umlg.sqlg.structure.IndexType;
import org.umlg.sqlg.structure.PropertyType;
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
     * The number of expected BDIO nodes.
     */
    private static final int EXPECTED_INSERTIONS = 10_000_000;

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

    protected SqlgReadGraphContext(SqlgGraph sqlgGraph, GraphMapper mapper, int batchSize) {
        super(sqlgGraph, mapper, batchSize);
        this.sqlgGraph = Objects.requireNonNull(sqlgGraph);
        this.supportsBatchMode = sqlgGraph.features().supportsBatchMode();
        uniqueIdentifiers = BloomFilter.create(Funnels.unencodedCharsFunnel(), EXPECTED_INSERTIONS);

        // Pre-populate label tables
        mapper().metadataLabel().ifPresent(this::defineVertexLabel);
        mapper().forEachTypeLabel(this::defineVertexLabel);

        // Commit schema changes
        commitTx();
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
        }

        mapper().unknownKey().ifPresent(key -> {
            // TODO PropertyType.JSON?
            vertexLabel.ensurePropertiesExist(Collections.singletonMap(key, PropertyType.STRING));
        });
    }

}
