/*
 * Copyright 2015 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

/**
 * Tests for various nodes.
 *
 * @author jgustie
 */
public class NodeTest {

    @Test(expected = UnsupportedOperationException.class)
    public void testAnonymousNodeTypes() {
        AnonymousNode.create(SpdxType.FILE).types().add(SpdxType.LICENSE);
    }

    @Test
    public void testAnonymousNodeData() {
        Node node = AnonymousNode.create(SpdxType.FILE);
        node.data().put(SpdxTerm.FILE_NAME, "./foo.txt");
        assertThat(node.data().get(SpdxTerm.FILE_NAME)).isEqualTo("./foo.txt");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testImmutableNodeTypes() {
        ImmutableNode.builder().id("_:123").type(SpdxType.FILE).build().types().add(SpdxType.LICENSE);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testImmutableNodeData() {
        ImmutableNode.builder().id("_:123").type(SpdxType.FILE).build().data().put(SpdxTerm.FILE_NAME, "./foo.txt");
    }

    @Test
    public void testImmutableNodeBuilderPutAll() {
        Node node = ImmutableNode.builder().putAll(ImmutableMap.<Term, Object> of(SpdxTerm.FILE_NAME, "./foo.txt")).build();
        assertThat(node.data().get(SpdxTerm.FILE_NAME)).isEqualTo("./foo.txt");
    }

}
