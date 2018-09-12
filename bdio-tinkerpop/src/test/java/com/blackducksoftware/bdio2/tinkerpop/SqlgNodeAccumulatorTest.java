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
package com.blackducksoftware.bdio2.tinkerpop;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.util.Collections.singleton;

import java.util.Arrays;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.T;
import org.junit.Test;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;

public class SqlgNodeAccumulatorTest extends BaseTest {

    public SqlgNodeAccumulatorTest(Configuration configuration) {
        super(configuration);
    }

    /**
     * Tests that bulk adding edges with non-existing identifiers still works. Although not directly testing any node
     * accumulator logic directly, this behavior is important as it allows us to generate identifier lists that are not
     * perfect (specifically, when a bloom filter accepts multiple vertex labels for a single identifier).
     */
    @Test
    public void bulkAddEdgeWithNonExistingVertexId() {
        assume().that(graph).isInstanceOf(SqlgGraph.class);
        String identifierKey = "__id";

        SqlgGraph sqlgGraph = (SqlgGraph) graph;

        sqlgGraph.tx().streamingBatchModeOn();
        sqlgGraph.streamVertex(T.label, Bdio.Class.Dependency.name(), identifierKey, "a");
        sqlgGraph.tx().flush();
        sqlgGraph.streamVertex(T.label, Bdio.Class.Component.name(), identifierKey, "1");
        sqlgGraph.tx().flush();
        sqlgGraph.bulkAddEdges(Bdio.Class.Dependency.name(), Bdio.Class.Component.name(), Bdio.ObjectProperty.dependsOn.name(),
                Pair.of(identifierKey, identifierKey), Arrays.asList(Pair.of("a", "1"), Pair.of("b", "2"), Pair.of("a", "2"), Pair.of("b", "1")));
        sqlgGraph.tx().commit();

        // Of the pairs specified, only one pair should produce a valid edge, the other three should be silently ignored
        // because they do not produce results when joined against the valid data

        GraphTraversalSource g = graph.traversal();
        assertThat(g.E().hasLabel(Bdio.ObjectProperty.dependsOn.name()).count().next()).isEqualTo(1L);
    }

    /**
     * This burnt us in a big way: the edge join query MUST respect partitions otherwise edges cross over the partition
     * boundaries. Worse it is possible that millions of unnecessary edges are created over time as the number of
     * partitions grows (even a few hundred partitions could be problematic).
     */
    @Test
    public void bulkAddEdgeWithPartition() {
        assume().that(graph).isInstanceOf(SqlgGraph.class);
        SqlgGraph sqlgGraph = (SqlgGraph) graph;
        SqlgNodeAccumulator accumulator;

        // Create the first partition and add the edges
        accumulator = new SqlgNodeAccumulator(new GraphIoWrapperFactory()
                .mapper(GraphMapper.build().tokens(testTokens(TT.id))::create)
                .addStrategies(singleton(testPartition("a")))
                .wrapReader(graph));

        sqlgGraph.addVertex(T.label, "Test", TT.partition, "a", TT.id, "foo");
        sqlgGraph.addVertex(T.label, "Test", TT.partition, "a", TT.id, "bar");
        sqlgGraph.addVertex(T.label, "Test", TT.partition, "a", TT.id, "gus");
        sqlgGraph.tx().commit();

        sqlgGraph.tx().streamingBatchModeOn();
        accumulator.bulkAddEdges("Test", "Test", "test", Pair.of(TT.id, TT.id),
                Arrays.asList(Pair.of("foo", "bar"), Pair.of("bar", "gus")), TT.partition, "a");
        sqlgGraph.tx().commit();

        // Create the second partition and add the edges
        accumulator = new SqlgNodeAccumulator(new GraphIoWrapperFactory()
                .mapper(GraphMapper.build().tokens(testTokens(TT.id))::create)
                .addStrategies(singleton(testPartition("b")))
                .wrapReader(graph));

        sqlgGraph.addVertex(T.label, "Test", TT.partition, "b", TT.id, "foo");
        sqlgGraph.addVertex(T.label, "Test", TT.partition, "b", TT.id, "bar");
        sqlgGraph.addVertex(T.label, "Test", TT.partition, "b", TT.id, "gus");
        sqlgGraph.tx().commit();

        sqlgGraph.tx().streamingBatchModeOn();
        accumulator.bulkAddEdges("Test", "Test", "test", Pair.of(TT.id, TT.id),
                Arrays.asList(Pair.of("foo", "bar"), Pair.of("bar", "gus")), TT.partition, "b");
        sqlgGraph.tx().commit();

        // At this point the non-partitioned implementation would have created 8 edges in the "b" partition:
        // e.g. [ a.foo -> a.bar, a.foo -> b.bar, b.foo -> a.bar, b.foo -> b.bar, ... ]

        GraphTraversalSource g = graph.traversal();

        // Litmus test: there should only be 4 edges!
        assertThat(g.E().hasLabel("test").has(TT.partition, "a").count().next()).isEqualTo(2L);
        assertThat(g.E().hasLabel("test").has(TT.partition, "b").count().next()).isEqualTo(2L);

        // Following edges from "foo", you should not jump partition boundaries
        Set<?> partitionsFromAFoo = g.V().hasLabel("Test").has(TT.partition, "a").has(TT.id, "foo").out("test").out("test").values(TT.partition).toSet();
        assertThat(partitionsFromAFoo).containsExactly("a");

        Set<?> partitionsFromBFoo = g.V().hasLabel("Test").has(TT.partition, "b").has(TT.id, "foo").out("test").out("test").values(TT.partition).toSet();
        assertThat(partitionsFromBFoo).containsExactly("b");
    }

}
