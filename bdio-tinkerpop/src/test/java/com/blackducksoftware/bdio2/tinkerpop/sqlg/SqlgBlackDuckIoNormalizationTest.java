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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Test;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.test.GraphRunner.GraphConfiguration;
import com.blackducksoftware.bdio2.tinkerpop.BaseTest;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;

/**
 * Tests for {@code SqlgBlackDuckIoNormalization}.
 *
 * @author jgustie
 */
@GraphConfiguration("/sqlg.properties")
public class SqlgBlackDuckIoNormalizationTest extends BaseTest {

    public SqlgBlackDuckIoNormalizationTest(Graph graph) {
        super(graph);
    }

    @Test
    public void addMissingFileParents_partitioned() {
        assume().that(graph).isInstanceOf(SqlgGraph.class);
        SqlgGraph sqlgGraph = (SqlgGraph) graph;
        BlackDuckIoOptions options = BlackDuckIoOptions.build().create();
        BdioFrame frame = new BdioFrame.Builder().context(BdioContext.getActive()).build();
        GraphTraversalSource g = graph.traversal();

        Vertex projectA = sqlgGraph.addVertex(T.label, Bdio.Class.Project.name(),
                TT.partition, "a");
        Vertex baseFileA = sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo",
                TT.partition, "a");
        projectA.addEdge(Bdio.ObjectProperty.base.name(), baseFileA, TT.partition, "a");
        sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo/bar/gus",
                options.fileParentKey().get(), "file:///foo/bar",
                TT.partition, "a");
        sqlgGraph.tx().commit();

        SqlgBlackDuckIo.getInstance().normalization(graph.traversal().withStrategies(testImplicitConstant(), testPartition("a")), options, frame)
                .addMissingFileParents();

        Vertex projectB = sqlgGraph.addVertex(T.label, Bdio.Class.Project.name(),
                TT.partition, "b");
        Vertex baseFileB = sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo",
                TT.partition, "b");
        projectB.addEdge(Bdio.ObjectProperty.base.name(), baseFileB, TT.partition, "b");
        sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo/bar/gus",
                options.fileParentKey().get(), "file:///foo/bar",
                TT.partition, "b");
        sqlgGraph.tx().commit();

        SqlgBlackDuckIo.getInstance().normalization(graph.traversal().withStrategies(testImplicitConstant(), testPartition("b")), options, frame)
                .addMissingFileParents();

        // Litmus test: there should only be 6 vertices and 4 edges!
        assertThat(g.V().hasLabel(Bdio.Class.File.name()).count().next()).isEqualTo(6);
        assertThat(g.E().hasLabel(Bdio.ObjectProperty.parent.name()).count().next()).isEqualTo(4);

        // TODO More detailed verification
    }

    @Test
    public void implyFileSystemType_partitioned() {
        assume().that(graph).isInstanceOf(SqlgGraph.class);
        SqlgGraph sqlgGraph = (SqlgGraph) graph;
        BlackDuckIoOptions options = BlackDuckIoOptions.build().create();
        BdioFrame frame = new BdioFrame.Builder().context(BdioContext.getActive()).build();
        GraphTraversalSource g = graph.traversal();

        Vertex fileFooA = sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo",
                TT.partition, "a");
        Vertex fileBarA = sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo/bar",
                TT.partition, "a");
        Vertex fileGusA = sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo/bar/gus",
                Bdio.DataProperty.byteCount.name(), 1L,
                TT.partition, "a");
        fileGusA.addEdge(Bdio.ObjectProperty.parent.name(), fileBarA, TT.partition, "a");
        fileBarA.addEdge(Bdio.ObjectProperty.parent.name(), fileFooA, TT.partition, "a");
        sqlgGraph.tx().commit();

        SqlgBlackDuckIo.getInstance().normalization(graph.traversal().withStrategies(testImplicitConstant(), testPartition("a")), options, frame)
                .implyFileSystemTypes();

        // Check the expected type and clear to make sure it doesn't come back on the next run
        assertThat(g.V(fileFooA).values(Bdio.DataProperty.fileSystemType.name()).next())
                .isEqualTo(Bdio.FileSystemType.DIRECTORY.toString());
        g.V(fileFooA).properties(Bdio.DataProperty.fileSystemType.name()).drop().iterate();

        Vertex fileFooB = sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo",
                TT.partition, "b");
        Vertex fileBarB = sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo/bar",
                TT.partition, "b");
        Vertex fileGusB = sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo/bar/gus",
                Bdio.DataProperty.byteCount.name(), 1L,
                TT.partition, "b");
        fileGusB.addEdge(Bdio.ObjectProperty.parent.name(), fileBarB, TT.partition, "b");
        fileBarB.addEdge(Bdio.ObjectProperty.parent.name(), fileFooB, TT.partition, "b");
        sqlgGraph.tx().commit();

        SqlgBlackDuckIo.getInstance().normalization(graph.traversal().withStrategies(testImplicitConstant(), testPartition("b")), options, frame)
                .implyFileSystemTypes();

        // This should not have come back
        assertThat(g.V(fileFooA).values(Bdio.DataProperty.fileSystemType.name()).tryNext()).isEmpty();

        // TODO More detailed verification
    }

    @Test
    public void addMissingFileParent_multipleReadPartitions() {
        assume().that(graph).isInstanceOf(SqlgGraph.class);
        SqlgGraph sqlgGraph = (SqlgGraph) graph;
        BlackDuckIoOptions options = BlackDuckIoOptions.build().create();
        BdioFrame frame = new BdioFrame.Builder().context(BdioContext.getActive()).build();
        GraphTraversalSource g = graph.traversal();

        Vertex projectA = sqlgGraph.addVertex(T.label, Bdio.Class.Project.name(),
                "p1", "a", "p2", "1");
        Vertex baseFileA = sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo",
                "p1", "a", "p2", "1");
        projectA.addEdge(Bdio.ObjectProperty.base.name(), baseFileA, TT.partition, "a");
        sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo/bar/gus",
                options.fileParentKey().get(), "file:///foo/bar",
                "p1", "a", "p2", "2");
        sqlgGraph.tx().commit();

        SqlgBlackDuckIo.getInstance().normalization(graph.traversal().withStrategies(testImplicitConstant(),
                PartitionStrategy.build().partitionKey("p1").writePartition("a").readPartitions("a").create(),
                PartitionStrategy.build().partitionKey("p2").writePartition("3").readPartitions("1", "2", "3").create()), options, frame)
                .addMissingFileParents();

        assertThat(g.V().hasLabel(Bdio.Class.File.name()).count().next()).isEqualTo(3);
        assertThat(g.E().hasLabel(Bdio.ObjectProperty.parent.name()).count().next()).isEqualTo(2);
        assertThat(g.V().has(Bdio.Class.File.name(), Bdio.DataProperty.path.name(), "file:///foo").values("p2").next()).isEqualTo("1");
        assertThat(g.V().has(Bdio.Class.File.name(), Bdio.DataProperty.path.name(), "file:///foo/bar").values("p2").next()).isEqualTo("3");
        assertThat(g.V().has(Bdio.Class.File.name(), Bdio.DataProperty.path.name(), "file:///foo/bar/gus").values("p2").next()).isEqualTo("2");
    }

}
