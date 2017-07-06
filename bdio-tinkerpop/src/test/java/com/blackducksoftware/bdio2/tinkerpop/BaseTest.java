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

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.util.GraphFactory;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.util.SqlgUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

@RunWith(Parameterized.class)
public abstract class BaseTest {

    /**
     * (T)est (T)okens.
     */
    public static final class TT {
        public static final String id = "_id";

        public static final String partition = "_partition";

        public static final String implicit = "_implicit";

        public static final String Metadata = "Metadata";
    }

    /**
     * Helper to simplify graph configuration.
     */
    private static class GraphConfiguration extends CompositeConfiguration {
        private GraphConfiguration(Class<? extends Graph> graphType) {
            addPropertyDirect(Graph.GRAPH, graphType.getName());
        }

        @Override
        public String toString() {
            String graph = getString(Graph.GRAPH);
            return graph.substring(graph.lastIndexOf('.') + 1);
        }
    }

    /**
     * Returns the graph configurations to test.
     */
    @Parameters(name = "{0}")
    public static Iterable<Configuration> configurations() throws ConfigurationException, IOException {
        // Default in-memory graph for testing
        GraphConfiguration tinkerGraphConfiguration = new GraphConfiguration(TinkerGraph.class);

        // Sqlg backed graph for testing (load database connection parameters from a properties file)
        GraphConfiguration sqlgGraphConfiguration = new GraphConfiguration(SqlgGraph.class);
        sqlgGraphConfiguration.addConfiguration(new PropertiesConfiguration(Resources.getResource("sqlg.properties")));

        return ImmutableList.<Configuration> builder()
                .add(tinkerGraphConfiguration)
                .add(sqlgGraphConfiguration)
                .build();
    }

    /**
     * The current configuration to use for obtaining graph instances.
     */
    private final Configuration configuration;

    /**
     * The graph instance to test.
     */
    protected Graph graph;

    public BaseTest(Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }

    /**
     * Common configuration.
     *
     * @see TT
     */
    public Consumer<BlackDuckIoConfig.Builder> storeMetadataAndIds() {
        return builder -> {
            builder.metadataLabel(TT.Metadata).identifierKey(TT.id);
        };
    }

    /**
     * Creates a partition identified by a new random value.
     */
    public PartitionStrategy createRandomPartition() {
        String id = UUID.randomUUID().toString();
        return PartitionStrategy.build()
                .partitionKey(TT.partition)
                .writePartition(id)
                .readPartitions(id)
                .create();
    }

    @Before
    public final void openGraph() throws Exception {
        // Open a new graph
        graph = GraphFactory.open(configuration);

        // Hard reset the Sqlg graph database into a clean state
        if (graph instanceof SqlgGraph) {
            SqlgUtil.dropDb((SqlgGraph) graph);
            graph.tx().commit();
            graph.close();
            graph = GraphFactory.open(configuration);
        }
    }

    @After
    public final void closeGraph() throws Exception {
        // Make sure that transactions are cleaned up if necessary
        if (graph.features().graph().supportsTransactions()) {
            graph.tx().onClose(Transaction.CLOSE_BEHAVIOR.ROLLBACK);
        }

        // Close out the graph
        graph.close();
        graph = null;
    }

}
