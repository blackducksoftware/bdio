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

import static com.blackducksoftware.common.base.ExtraStrings.afterLast;

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

import com.blackducksoftware.bdio2.tinkerpop.GraphContextFactory.AbstractContextBuilder;
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

        public static final String root = "_root";

        public static final String Metadata = "Metadata";
    }

    /**
     * The most basic context builder possible, produces graph context factories directly.
     */
    public static class GraphContextFactoryBuilder extends AbstractContextBuilder<GraphContextFactory, GraphContextFactoryBuilder> {
        public GraphContextFactoryBuilder() {
            super(GraphContextFactoryBuilder::contextFactory);
        }
    }

    /**
     * Helper to simplify graph configuration and make test parameter names more readable.
     */
    private static class GraphConfiguration extends CompositeConfiguration {
        private GraphConfiguration(Class<? extends Graph> graphType) {
            addPropertyDirect(Graph.GRAPH, graphType.getName());
        }

        @Override
        public String toString() {
            return afterLast(getString(Graph.GRAPH), '.');
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
     * A common "on-graph-topology" configuration for storing graph metadata and JSON-LD identifiers.
     *
     * @see TT
     */
    public Consumer<GraphTopology.Builder> storeMetadataAndIds() {
        return b -> b.metadataLabel(TT.Metadata).identifierKey(TT.id);
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

    /**
     * Helper method to commit the current graph transaction only if the graph supports them.
     */
    public final void commit() {
        if (graph.features().graph().supportsTransactions()) {
            graph.tx().commit();
        }
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
