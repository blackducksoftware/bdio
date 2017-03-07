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
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.not;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.junit.Test;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.model.File;

public class BdioOperationsTest extends BaseTest {

    public BdioOperationsTest(Configuration configuration) {
        super(configuration);
    }

    @Test
    public void addMissingFileParents() throws IOException {
        InputStream bdio = new NamedGraphBuilder()
                .withBaseFile(new File(BdioObject.randomId()).path("file:///foo"))
                .add(new File(BdioObject.randomId()).path("file:///foo/bar/gus/one/more"))
                .add(new File(BdioObject.randomId()).path("file:///foo/bar/gus/two/more"))
                .build();

        graph.io(BlackDuckIo.build()).readGraph(bdio);

        GraphTraversalSource g = graph.traversal();
        BdioOperations.create(g).addImplicitEdges();

        List<Object> leafPaths = g.V()
                .hasLabel(Bdio.Class.Project.name())
                .out(Bdio.ObjectProperty.base.name())
                .repeat(in("parent"))
                .until(not(in("parent")))
                .values(Bdio.DataProperty.path.name())
                .toList();

        assertThat(leafPaths).containsExactly("file:///foo/bar/gus/one/more", "file:///foo/bar/gus/two/more");
    }

}
