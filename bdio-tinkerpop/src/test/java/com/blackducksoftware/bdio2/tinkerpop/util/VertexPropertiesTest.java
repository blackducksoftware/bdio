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
package com.blackducksoftware.bdio2.tinkerpop.util;

import static com.google.common.truth.Truth8.assertThat;

import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph;
import org.junit.Test;

import com.blackducksoftware.bdio2.tinkerpop.util.VertexProperties;

/**
 * Tests for {@code GraphMapper}.
 *
 * @author jgustie
 */
public class VertexPropertiesTest {

    @Test
    public void streamVertexPropertyStringArray() {
        VertexProperty<String[]> vp = StarGraph.open().addVertex("test").property("test", new String[] { "foo", "bar" });
        assertThat(VertexProperties.streamValue(vp)).containsExactly("foo", "bar");
    }

}
