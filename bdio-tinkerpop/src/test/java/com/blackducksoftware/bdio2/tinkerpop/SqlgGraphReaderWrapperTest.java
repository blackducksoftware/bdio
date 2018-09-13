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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.util.Collections.singleton;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Test;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;

/**
 * Tests for {@code SqlgGraphReaderWrapper}.
 *
 * @author jgustie
 */
public class SqlgGraphReaderWrapperTest extends BaseTest {

    public SqlgGraphReaderWrapperTest(Configuration configuration) {
        super(configuration);
    }

    @Test
    public void addMissingFileParents_partitioned() {
        assume().that(graph).isInstanceOf(SqlgGraph.class);
        SqlgGraph sqlgGraph = (SqlgGraph) graph;

        Vertex projectA = sqlgGraph.addVertex(T.label, Bdio.Class.Project.name(),
                TT.partition, "a");
        Vertex baseFileA = sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo",
                TT.partition, "a");
        projectA.addEdge(Bdio.ObjectProperty.base.name(), baseFileA, TT.partition, "a");
        sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo/bar/gus",
                GraphMapper.FILE_PARENT_KEY, "file:///foo/bar",
                TT.partition, "a");
        sqlgGraph.tx().commit();

        new SqlgGraphReaderWrapper.SqlgAddMissingFileParentsOperation(new GraphIoWrapperFactory()
                .mapper(GraphMapper.build().tokens(testTokens(TT.implicit))::create)
                .addStrategies(singleton(testPartition("a")))
                .wrapReader(graph))
                        .run();

        Vertex projectB = sqlgGraph.addVertex(T.label, Bdio.Class.Project.name(),
                TT.partition, "b");
        Vertex baseFileB = sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo",
                TT.partition, "b");
        projectB.addEdge(Bdio.ObjectProperty.base.name(), baseFileB, TT.partition, "b");
        sqlgGraph.addVertex(T.label, Bdio.Class.File.name(),
                Bdio.DataProperty.path.name(), "file:///foo/bar/gus",
                GraphMapper.FILE_PARENT_KEY, "file:///foo/bar",
                TT.partition, "b");
        sqlgGraph.tx().commit();

        new SqlgGraphReaderWrapper.SqlgAddMissingFileParentsOperation(new GraphIoWrapperFactory()
                .mapper(GraphMapper.build().tokens(testTokens(TT.implicit))::create)
                .addStrategies(singleton(testPartition("b")))
                .wrapReader(graph))
                        .run();

        GraphTraversalSource g = graph.traversal();

        // Litmus test: there should only be 6 vertices and 4 edges!
        assertThat(g.V().hasLabel(Bdio.Class.File.name()).count().next()).isEqualTo(6);
        assertThat(g.E().hasLabel(Bdio.ObjectProperty.parent.name()).count().next()).isEqualTo(4);

        // TODO More detailed verification
    }

    @Test
    public void implyFileSystemType_partitioned() {
        assume().that(graph).isInstanceOf(SqlgGraph.class);
        SqlgGraph sqlgGraph = (SqlgGraph) graph;
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

        new SqlgGraphReaderWrapper.SqlgImplyFileSystemTypeOperation(new GraphIoWrapperFactory()
                .mapper(GraphMapper.build().tokens(testTokens(TT.implicit))::create)
                .addStrategies(singleton(testPartition("a")))
                .wrapReader(graph))
                        .run();

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

        new SqlgGraphReaderWrapper.SqlgImplyFileSystemTypeOperation(new GraphIoWrapperFactory()
                .mapper(GraphMapper.build().tokens(testTokens(TT.implicit))::create)
                .addStrategies(singleton(testPartition("b")))
                .wrapReader(graph))
                        .run();

        // This should not have come back
        assertThat(g.V(fileFooA).values(Bdio.DataProperty.fileSystemType.name()).tryNext()).isEmpty();

        // TODO More detailed verification
    }

}
