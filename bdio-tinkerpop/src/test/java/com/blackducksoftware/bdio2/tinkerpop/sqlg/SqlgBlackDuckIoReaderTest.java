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
package com.blackducksoftware.bdio2.tinkerpop.sqlg;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.junit.Test;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.test.GraphRunner.GraphConfiguration;
import com.blackducksoftware.bdio2.tinkerpop.BaseTest;
import com.google.common.collect.ImmutableMap;

/**
 * Tests for the {@code SqlgBlackDuckIoReader}.
 *
 * @author jgustie
 */
@GraphConfiguration("/sqlg.properties")
public class SqlgBlackDuckIoReaderTest extends BaseTest {

    public SqlgBlackDuckIoReaderTest(Graph graph) {
        super(graph);
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
        SchemaTable table = SchemaTable.from(sqlgGraph, "Test");
        Pair<String, String> idFields = Pair.of(TT.id, TT.id);
        Collection<Pair<String, String>> uids = Arrays.asList(Pair.of("foo", "bar"), Pair.of("bar", "gus"));
        Map<String, PropertyType> edgeColumns = ImmutableMap.of(TT.partition, PropertyType.STRING);

        // Create the first partition and add the edges
        sqlgGraph.addVertex(T.label, "Test", TT.partition, "a", TT.id, "foo");
        sqlgGraph.addVertex(T.label, "Test", TT.partition, "a", TT.id, "bar");
        sqlgGraph.addVertex(T.label, "Test", TT.partition, "a", TT.id, "gus");
        sqlgGraph.tx().commit();

        // Bulk add the edges
        sqlgGraph.tx().streamingBatchModeOn();
        Map<String, Object> readPartitionsA = ImmutableMap.of(TT.partition, "a");
        Map<String, Object> writePartitionA = ImmutableMap.of(TT.partition, "a");
        new SqlgBlackDuckIoReader.BulkAddEdgeDialect(readPartitionsA)
                .bulkAddEdges(sqlgGraph, table, table, "test", idFields, uids, edgeColumns, writePartitionA);
        sqlgGraph.tx().commit();

        // Create the second partition and add the edges
        sqlgGraph.addVertex(T.label, "Test", TT.partition, "b", TT.id, "foo");
        sqlgGraph.addVertex(T.label, "Test", TT.partition, "b", TT.id, "bar");
        sqlgGraph.addVertex(T.label, "Test", TT.partition, "b", TT.id, "gus");
        sqlgGraph.tx().commit();

        sqlgGraph.tx().streamingBatchModeOn();
        Map<String, Object> readPartitionsB = ImmutableMap.of(TT.partition, "b");
        Map<String, Object> writePartitionB = ImmutableMap.of(TT.partition, "b");
        new SqlgBlackDuckIoReader.BulkAddEdgeDialect(readPartitionsB)
                .bulkAddEdges(sqlgGraph, table, table, "test", idFields, uids, edgeColumns, writePartitionB);
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
