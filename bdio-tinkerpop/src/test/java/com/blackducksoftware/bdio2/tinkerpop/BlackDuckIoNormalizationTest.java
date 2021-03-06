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

import static com.google.common.truth.Truth.assertThat;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.in;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.not;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.out;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.junit.Test;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.model.FileCollection;
import com.blackducksoftware.bdio2.model.Project;
import com.blackducksoftware.bdio2.test.GraphRunner.GraphConfiguration;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoNormalizationSpi;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoSpi;
import com.blackducksoftware.bdio2.tinkerpop.test.NamedGraphBuilder;

/**
 * Tests for {@link BlackDuckIoNormalization}.
 *
 * @author jgustie
 */
@GraphConfiguration("/tinkergraph-core.properties")
@GraphConfiguration("/sqlg-core.properties")
public class BlackDuckIoNormalizationTest extends BaseTest {

    public BlackDuckIoNormalizationTest(Graph graph) {
        super(graph);
    }

    // Instead of testing actual normalization, test the SPI so we can isolate the work
    private static BlackDuckIoNormalizationSpi spi(BlackDuckIo bdio, GraphTraversalSource traversal) {
        return BlackDuckIoSpi.getForGraph(traversal.getGraph()).normalization(traversal, bdio.options(), bdio.mapper().create().createMapper());
    }

    @Test
    public void addMissingFileParents() throws IOException {
        InputStream inputStream = new NamedGraphBuilder()
                .fileCollection(f -> {})
                .file(f -> f.path("file:///foo"))
                .relateToFirst(FileCollection.class, FileCollection::base)
                .file(f -> f.path("file:///foo/bar/gus/one/more"))
                .file(f -> f.path("file:///foo/bar"))
                .file(f -> f.path("file:///foo/bar/gus/two/more"))
                .build();

        BlackDuckIo bdio = graph.io(testBdio());
        bdio.readGraph(inputStream, null, null);
        spi(bdio, graph.traversal().withStrategies(testImplicitConstant())).addMissingFileParents();

        GraphTraversalSource g = graph.traversal();

        // There were 3 files that should have been created
        assertThat(g.V().hasLabel(Bdio.Class.File.name()).has(TT.implicit).count().next())
                .named("implicit file count").isEqualTo(3);

        // Make sure we can traverse from the project to the leaves
        List<Object> leafPaths = g.V()
                .hasLabel(Bdio.Class.FileCollection.name())
                .out(Bdio.ObjectProperty.base.name())
                .repeat(in(Bdio.ObjectProperty.parent.name()))
                .until(not(in(Bdio.ObjectProperty.parent.name())))
                .values(Bdio.DataProperty.path.name())
                .toList();

        assertThat(leafPaths).containsExactly("file:///foo/bar/gus/one/more", "file:///foo/bar/gus/two/more");

        // Make sure we can traverse from the leaves to the base directory (AND NO FURTHER!)
        List<Object> rootPaths = g.V()
                .hasLabel(Bdio.Class.File.name())
                .not(inE(Bdio.ObjectProperty.parent.name()))
                .repeat(out(Bdio.ObjectProperty.parent.name()))
                .until(not(out(Bdio.ObjectProperty.parent.name())))
                .dedup()
                .values(Bdio.DataProperty.path.name())
                .toList();

        assertThat(rootPaths).containsExactly("file:///foo");
    }

    @Test
    public void identifyRootProject() throws IOException {
        InputStream inputStream = new NamedGraphBuilder()
                .project(p -> {})
                .build();

        BlackDuckIo bdio = graph.io(testBdio(TT.Metadata, TT.root));
        bdio.readGraph(inputStream, null, null);
        spi(bdio, graph.traversal().withStrategies(testImplicitConstant())).identifyRoot();

        GraphTraversalSource g = graph.traversal();
        List<Vertex> roots = g.V().hasLabel(TT.Metadata).out(TT.root).toList();

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).label()).isEqualTo(Bdio.Class.Project.name());
    }

    @Test
    public void identifyRootProjectWithEdges() throws IOException {
        InputStream inputStream = new NamedGraphBuilder()
                .project(p -> p.name("project"))
                .project(p -> p.name("subproject"))
                .relateToFirst(Project.class, Project::subproject)
                .project(p -> p.name("subsubproject"))
                .relateToLast(Project.class, Project::subproject)
                .project(p -> p.name("oldproject"))
                .relateToFirst(Project.class, Project::previousVersion)
                .build();

        BlackDuckIo bdio = graph.io(testBdio(TT.Metadata, TT.root));
        bdio.readGraph(inputStream, null, null);
        spi(bdio, graph.traversal().withStrategies(testImplicitConstant())).identifyRoot();

        GraphTraversalSource g = graph.traversal();
        List<Vertex> roots = g.V().hasLabel(TT.Metadata).out(TT.root).toList();

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).<String> value(Bdio.DataProperty.name.name())).isEqualTo("project");
    }

    @Test
    public void addMissingProjectDependencies() throws IOException {
        InputStream inputStream = new NamedGraphBuilder()
                .project(p -> {})
                .component(c -> c.name("test1"))
                .component(c -> c.name("test2"))
                .build();

        BlackDuckIo bdio = graph.io(testBdio(TT.Metadata, TT.root));
        bdio.readGraph(inputStream, null, null);
        BlackDuckIoNormalizationSpi spi = spi(bdio, graph.traversal().withStrategies(testImplicitConstant()));
        spi.identifyRoot();
        spi.addMissingProjectDependencies();

        GraphTraversalSource g = graph.traversal();
        List<String> directDependencyNames = g.V().hasLabel(TT.Metadata)
                .out(TT.root)
                .out(Bdio.ObjectProperty.dependency.name())
                .out(Bdio.ObjectProperty.dependsOn.name())
                .<String> values(Bdio.DataProperty.name.name())
                .toList();

        assertThat(directDependencyNames).containsExactly("test1", "test2");
    }

    @Test
    public void implyFileSystemType() throws IOException {
        InputStream inputStream = new NamedGraphBuilder()
                .fileCollection(f -> {})
                .file(f -> f.path("file:///foo"))
                .relateToFirst(FileCollection.class, FileCollection::base)
                .file(f -> f.path("file:///foo/bar"))
                .file(f -> f.path("file:///foo/gus").linkPath("file:///foo/bar"))
                .file(f -> f.path("file:///foo/bar/test.bin"))
                .file(f -> f.path("file:///foo/bar/test.zip").byteCount(1L))
                .file(f -> f.path("zip:file:%2F%2F%2Ffoo%2Fbar%2Ftest.zip#test.txt").encoding("UTF-8"))
                .build();

        BlackDuckIo bdio = graph.io(testBdio(TT.Metadata, TT.root));
        bdio.readGraph(inputStream, null, null);
        BlackDuckIoNormalizationSpi spi = spi(bdio, graph.traversal().withStrategies(testImplicitConstant()));
        spi.addMissingFileParents();
        spi.implyFileSystemTypes();

        GraphTraversalSource g = graph.traversal();
        List<Vertex> files = g.V().hasLabel(Bdio.Class.File.name()).toList();
        for (Vertex file : files) {
            VertexProperty<String> fileSystemType = file.property(Bdio.DataProperty.fileSystemType.name());
            assertThat(fileSystemType.isPresent()).isTrue();
            String path = file.value(Bdio.DataProperty.path.name());
            if (path.equals("file:///foo")) {
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.DIRECTORY.toString());
            } else if (path.equals("file:///foo/bar")) {
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.DIRECTORY.toString());
            } else if (path.equals("file:///foo/gus")) {
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.SYMLINK.toString());
            } else if (path.equals("file:///foo/bar/test.bin")) {
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.REGULAR.toString());
            } else if (path.equals("file:///foo/bar/test.zip")) {
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.DIRECTORY_ARCHIVE.toString());
            } else if (path.equals("zip:file:%2F%2F%2Ffoo%2Fbar%2Ftest.zip#test.txt")) {
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.REGULAR_TEXT.toString());
            }
        }
    }

    @Test
    public void implyFileSystemType_noOverwrite() throws IOException {
        InputStream inputStream = new NamedGraphBuilder()
                .fileCollection(f -> {})
                .file(f -> f.path("file:///foo").fileSystemType(Bdio.FileSystemType.SYMLINK.toString()))
                .relateToFirst(FileCollection.class, FileCollection::base)
                .file(f -> f.path("file:///foo/bar").fileSystemType(Bdio.FileSystemType.REGULAR.toString()))
                .file(f -> f.path("file:///foo/gus").fileSystemType(Bdio.FileSystemType.SYMLINK.toString()))
                .file(f -> f.path("file:///foo/bar/test.bin").fileSystemType(Bdio.FileSystemType.SYMLINK.toString()))
                .file(f -> f.path("file:///foo/bar/test.zip").byteCount(1L).fileSystemType(Bdio.FileSystemType.SYMLINK.toString()))
                .file(f -> f.path("zip:file:%2F%2F%2Ffoo%2Fbar%2Ftest.zip#test.txt").encoding("UTF-8").fileSystemType(Bdio.FileSystemType.SYMLINK.toString()))
                .build();

        BlackDuckIo bdio = graph.io(testBdio(TT.Metadata, TT.root));
        bdio.readGraph(inputStream, null, null);
        BlackDuckIoNormalizationSpi spi = spi(bdio, graph.traversal().withStrategies(testImplicitConstant()));
        spi.addMissingFileParents();
        spi.implyFileSystemTypes();

        GraphTraversalSource g = graph.traversal();
        List<Vertex> files = g.V().hasLabel(Bdio.Class.File.name()).toList();
        for (Vertex file : files) {
            VertexProperty<String> fileSystemType = file.property(Bdio.DataProperty.fileSystemType.name());
            assertThat(fileSystemType.isPresent()).isTrue();
            String path = file.value(Bdio.DataProperty.path.name());
            if (path.equals("file:///foo")) {
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.SYMLINK.toString());
            } else if (path.equals("file:///foo/bar")) {
                // This is the one exception to the "no overwrite"; a regular file with children is corrected
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.DIRECTORY.toString());
            } else if (path.equals("file:///foo/gus")) {
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.SYMLINK.toString());
            } else if (path.equals("file:///foo/bar/test.bin")) {
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.SYMLINK.toString());
            } else if (path.equals("file:///foo/bar/test.zip")) {
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.SYMLINK.toString());
            } else if (path.equals("zip:file:%2F%2F%2Ffoo%2Fbar%2Ftest.zip#test.txt")) {
                assertThat(fileSystemType.value()).isEqualTo(Bdio.FileSystemType.SYMLINK.toString());
            }
        }
    }

}
