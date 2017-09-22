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
import java.util.function.Consumer;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.junit.Test;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.tinkerpop.test.NamedGraphBuilder;

public class BlackDuckIoOperationsTest extends BaseTest {

    public BlackDuckIoOperationsTest(Configuration configuration) {
        super(configuration);
    }

    @Test
    public void addMissingFileParents() throws IOException {
        InputStream bdio = new NamedGraphBuilder()
                .withBaseFile(new File(BdioObject.randomId()).path("file:///foo"))
                .add(new File(BdioObject.randomId()).path("file:///foo/bar/gus/one/more"))
                .add(new File(BdioObject.randomId()).path("file:///foo/bar"))
                .add(new File(BdioObject.randomId()).path("file:///foo/bar/gus/two/more"))
                .build();

        Consumer<GraphMapper.Builder> config = b -> b.implicitKey(TT.implicit);
        graph.io(BlackDuckIo.build().onGraphMapper(config)).readGraph(bdio);

        BlackDuckIoOperations.build().onGraphMapper(config).create().addImplicitEdges(graph);

        GraphTraversalSource g = graph.traversal();

        // There were 3 files that should have been created
        assertThat(g.V().hasLabel(Bdio.Class.File.name()).has(TT.implicit).count().next())
                .named("implicit file count").isEqualTo(3);

        // Make sure we can traverse from the project to the leaves
        List<Object> leafPaths = g.V()
                .hasLabel(Bdio.Class.Project.name())
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
        InputStream bdio = new NamedGraphBuilder().build();

        Consumer<GraphMapper.Builder> config = b -> b.metadataLabel(TT.Metadata).implicitKey(TT.implicit).rootProjectKey(TT.rootProject);
        graph.io(BlackDuckIo.build().onGraphMapper(config)).readGraph(bdio);

        BlackDuckIoOperations.build().onGraphMapper(config).create().addImplicitEdges(graph);

        GraphTraversalSource g = graph.traversal();
        List<Object> rootProjectFlags = g.V().hasLabel(TT.Metadata)
                .out(TT.rootProject)
                .values(TT.rootProject)
                .toList();

        assertThat(rootProjectFlags).containsExactly(true);
    }

}
