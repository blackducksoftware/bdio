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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.umlg.sqlg.structure.SqlgGraph;

import com.google.common.collect.Iterables;
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

    protected SqlgReadGraphContext(SqlgGraph sqlgGraph, int batchSize, PartitionStrategy partitionStrategy) {
        super(sqlgGraph, batchSize, partitionStrategy);
        this.sqlgGraph = Objects.requireNonNull(sqlgGraph);
        this.supportsBatchMode = sqlgGraph.features().supportsBatchMode();
        uniqueIdentifiers = BloomFilter.create(Funnels.unencodedCharsFunnel(), EXPECTED_INSERTIONS);
    }

    @Override
    public void initialize(BdioFrame frame) {
        super.initialize(frame);

        // Pre-create and index a few import columns in the database
        Object[] dummyKeyValues = indexedDummyKeyValues();
        frame.forEachTypeName(label -> sqlgGraph.createVertexLabeledIndex(label, dummyKeyValues));

        // Commit schema changes
        commitTx();
    }

    @Override
    public void commitTx() {
        super.commitTx();

        // (Re-)enable batch mode if it is supported
        if (supportsBatchMode) {
            sqlgGraph.tx().normalBatchModeOn();
        }
    }

    @Override
    protected boolean isIdentifierUnique(String identifier) {
        return uniqueIdentifiers.put(identifier);
    }

    /**
     * Returns a key/value array of indexed property keys and dummy values. The dummy values are used by the database to
     * determine what type to assign the newly created column so they must be representative of what will actually be
     * stored in that property.
     */
    private Object[] indexedDummyKeyValues() {
        List<Object> dummyKeyValues = new ArrayList<>();

        // If there is a partitioning strategy, index it as the first column
        partitionStrategy().filter(p -> !p.getReadPartitions().isEmpty()).ifPresent(p -> {
            dummyKeyValues.add(p.getPartitionKey());
            if (p.getReadPartitions().size() == 1) {
                dummyKeyValues.add(Iterables.getOnlyElement(p.getReadPartitions()));
            } else {
                dummyKeyValues.add(new ArrayList<>(p.getReadPartitions()));
            }
        });

        // The BDIO identifier is present on every vertex
        dummyKeyValues.add(Tokens.id);
        dummyKeyValues.add("http://example.com/1");

        return dummyKeyValues.toArray();
    }

}
