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
package com.blackducksoftware.bdio2.tinkerpop.sqlg.strategy;

import static com.google.common.collect.Iterables.getLast;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.apache.tinkerpop.gremlin.process.traversal.util.TraversalMetrics.ELEMENT_COUNT_ID;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.T;
import org.junit.Test;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tinkerpop.BaseTest;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.step.SqlgGraphCountStep;

/**
 * Tests for various Sqlg specific optimizations. Because this extends the base test, each test will run using the
 * Tinkergraph and using Sqlg; this allows us to verify the strategy only impacts Sqlg.
 *
 * @author jgustie
 */
public class OptimizationTest extends BaseTest {

    public OptimizationTest(Configuration configuration) {
        super(configuration);
    }

    @Test
    public void simpleGraphCount() {
        // Add two vertices to the graph
        graph.addVertex(T.label, Bdio.Class.File.name());
        graph.addVertex(T.label, Bdio.Class.File.name());

        // Enable our optimization
        GraphTraversalSource g = graph.traversal().withStrategies(SqlgGraphCountStrategy.instance());

        // Regardless what the query plan is, this query should always return the same thing
        assertThat(g.V().hasLabel(Bdio.Class.File.name()).count().next()).isEqualTo(2);

    }

    @Test
    public void simpleGraphCountProfile() {
        graph.addVertex(T.label, Bdio.Class.File.name());
        graph.addVertex(T.label, Bdio.Class.File.name());

        GraphTraversalSource g = graph.traversal().withStrategies(SqlgGraphCountStrategy.instance());

        long count = g.V().hasLabel(Bdio.Class.File.name()).count().profile().next().getMetrics()
                .stream().mapToLong(m -> m.getCount(ELEMENT_COUNT_ID)).sum();
        if (graph instanceof SqlgGraph) {
            // We should have been optimized so there is only one traverser
            assertThat(count).isEqualTo(1L);
        } else {
            // There should have been a traverser for each file plus a traverser to count them
            assertThat(count).isAtLeast(2L + 1L);
        }
    }

    @Test
    public void partitionedGraphCount() {
        // Add vertices with different values to partition on
        graph.addVertex(T.label, Bdio.Class.File.name(), "a", "1", "b", "1");
        graph.addVertex(T.label, Bdio.Class.File.name(), "a", "1", "b", "2");
        graph.addVertex(T.label, Bdio.Class.File.name(), "a", "2", "b", "1");
        graph.addVertex(T.label, Bdio.Class.File.name(), "a", "2", "b", "2");
        graph.addVertex(T.label, Bdio.Class.File.name(), "a", "3", "b", "3");

        // Create some partition strategies
        PartitionStrategy a1 = PartitionStrategy.build().partitionKey("a").readPartitions("1").create();
        PartitionStrategy b2 = PartitionStrategy.build().partitionKey("b").readPartitions("2").create();

        // Start the traversal source with our optimization
        GraphTraversalSource g = graph.traversal().withStrategies(SqlgGraphCountStrategy.instance());

        assertThat(g.V().hasLabel(Bdio.Class.File.name()).count().next()).named("no partition").isEqualTo(5L);
        assertThat(g.withStrategies(a1).V().hasLabel(Bdio.Class.File.name()).count().next()).named("a = 1").isEqualTo(2L);
        assertThat(g.withStrategies(b2).V().hasLabel(Bdio.Class.File.name()).count().next()).named("b = 2").isEqualTo(2L);
        assertThat(g.withStrategies(a1, b2).V().hasLabel(Bdio.Class.File.name()).count().next()).named("a = 1 && b = 2").isEqualTo(1L);
    }

    @Test
    public void graphCountAndThenSum() {
        graph.addVertex(T.label, Bdio.Class.File.name());
        graph.addVertex(T.label, Bdio.Class.File.name());
        GraphTraversalSource g = graph.traversal().withStrategies(SqlgGraphCountStrategy.instance());

        // Kind of a dumb test, summing the single count, but verifies that a subsequent step doesn't flat out break
        assertThat(g.V().hasLabel(Bdio.Class.File.name()).count().sum().next()).isEqualTo(2L);

        if (graph instanceof SqlgGraph) {
            Traversal.Admin<?, ?> finalStep = getLast(g.V().hasLabel(Bdio.Class.File.name()).count().sum().explain().getStrategyTraversals()).getValue1();
            assertThat(finalStep.getStartStep()).isInstanceOf(SqlgGraphCountStep.class);
        }
    }

    @Test
    public void simpleGraphAddProperty() {
        graph.addVertex(T.label, Bdio.Class.File.name(), "a", "1");
        graph.addVertex(T.label, Bdio.Class.File.name(), "a", "2");
        GraphTraversalSource g = graph.traversal().withStrategies(SqlgGraphAddPropertyStrategy.instance());

        g.V().hasLabel(Bdio.Class.File.name()).has("a", "1").property("b", "1").property("c", "2").iterate();

        assertThat(g.V().hasLabel(Bdio.Class.File.name()).has("a", "1").values("b").toStream()).containsExactly("1");
        assertThat(g.V().hasLabel(Bdio.Class.File.name()).has("a", "1").values("c").toStream()).containsExactly("2");
        assertThat(g.V().hasLabel(Bdio.Class.File.name()).has("a", "2").values("b").toStream()).isEmpty();
        assertThat(g.V().hasLabel(Bdio.Class.File.name()).has("a", "2").values("c").toStream()).isEmpty();
    }

    @Test
    public void simpleGraphAddPropertyProfile() {
        graph.addVertex(T.label, Bdio.Class.File.name(), "a", "1");
        GraphTraversalSource g = graph.traversal().withStrategies(SqlgGraphAddPropertyStrategy.instance());

        long count = g.V().hasLabel(Bdio.Class.File.name()).has("a", "1").property("b", "1").profile().next().getMetrics()
                .stream().mapToLong(m -> m.getCount(ELEMENT_COUNT_ID)).sum();
        if (graph instanceof SqlgGraph) {
            // We should have been optimized so there is only one traverser
            assertThat(count).isEqualTo(1L);
        } else {
            // There should have been a traverser for each file plus a traverser to add the property
            assertThat(count).isAtLeast(1L + 1L);
        }
    }

    @Test
    public void partitionedGraphAddProperty() {
        // Add vertices with different values to partition on
        graph.addVertex(T.label, Bdio.Class.File.name(), "a", "1", "b", "1");
        graph.addVertex(T.label, Bdio.Class.File.name(), "a", "1", "b", "2");
        graph.addVertex(T.label, Bdio.Class.File.name(), "a", "2", "b", "1");

        // Create some partition strategies
        PartitionStrategy a1 = PartitionStrategy.build().partitionKey("a").readPartitions("1").create();

        // Start the traversal source with our optimization
        GraphTraversalSource g = graph.traversal().withStrategies(SqlgGraphAddPropertyStrategy.instance());

        g.withStrategies(a1).V().hasLabel(Bdio.Class.File.name()).has("b", "1").property("c", "1").iterate();
        assertThat(g.V().has("c", "1").count().next()).isEqualTo(1);
    }

}