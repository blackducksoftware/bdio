/*
 * Copyright 2019 Synopsys, Inc.
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
package com.blackducksoftware.bdio2.tinkerpop.strategy;

import static com.google.common.truth.Truth.assertThat;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.junit.Test;

import com.blackducksoftware.bdio2.test.GraphRunner.GraphConfiguration;
import com.blackducksoftware.bdio2.tinkerpop.BaseTest;

/**
 * Tests for {@code PropertyConstantStrategy}
 *
 * @author jgustie
 */
@GraphConfiguration("/tinkergraph-core.properties")
@GraphConfiguration("/sqlg-core.properties")
public class PropertyConstantStrategyTest extends BaseTest {

    public PropertyConstantStrategyTest(Graph graph) {
        super(graph);
    }

    @Test
    public void noProperty() {
        GraphTraversalSource g = graph.traversal().withStrategies(PropertyConstantStrategy.build().create());
        VertexProperty<String> a = g.addV().next().property("a");
        assertThat(a.isPresent()).isFalse();
    }

    @Test
    public void nullProperty() {
        GraphTraversalSource g = graph.traversal().withStrategies(PropertyConstantStrategy.build().addProperty("a", null).create());
        VertexProperty<String> a = g.addV().next().property("a");
        assertThat(a.isPresent()).isFalse();
    }

    @Test
    public void singleProperty() {
        GraphTraversalSource g = graph.traversal().withStrategies(PropertyConstantStrategy.build().addProperty("a", "AAA").create());
        VertexProperty<String> a = g.addV().next().property("a");
        assertThat(a.isPresent()).isTrue();
        assertThat(a.value()).isEqualTo("AAA");
    }

    @Test
    public void twoProperties() {
        GraphTraversalSource g = graph.traversal().withStrategies(PropertyConstantStrategy.build().addProperty("a", "AAA").addProperty("b", "BBB").create());
        VertexProperty<String> a = g.addV().next().property("a");
        VertexProperty<String> b = g.addV().next().property("b");
        assertThat(a.isPresent()).isTrue();
        assertThat(a.value()).isEqualTo("AAA");
        assertThat(b.isPresent()).isTrue();
        assertThat(b.value()).isEqualTo("BBB");
    }

}
