/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 * 
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bom;

import static com.google.common.truth.Truth.assertThat;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

/**
 * Tests for various nodes.
 *
 * @author jgustie
 */
public class NodeTest {

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testAnonymousNodeTypes() {
        AnonymousNode.create(SpdxType.FILE).types().add(SpdxType.LICENSE);
    }

    @Test
    public void testAnonymousNodeData() {
        Node node = AnonymousNode.create(SpdxType.FILE);
        node.data().put(SpdxTerm.FILE_NAME, "./foo.txt");
        assertThat(node.data().get(SpdxTerm.FILE_NAME)).isEqualTo("./foo.txt");
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testImmutableNodeTypes() {
        ImmutableNode.builder().id("_:123").type(SpdxType.FILE).build().types().add(SpdxType.LICENSE);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testImmutableNodeData() {
        ImmutableNode.builder().id("_:123").type(SpdxType.FILE).build().data().put(SpdxTerm.FILE_NAME, "./foo.txt");
    }

    @Test
    public void testImmutableNodeBuilderPutAll() {
        Node node = ImmutableNode.builder().putAll(ImmutableMap.<Term, Object> of(SpdxTerm.FILE_NAME, "./foo.txt")).build();
        assertThat(node.data().get(SpdxTerm.FILE_NAME)).isEqualTo("./foo.txt");
    }

}
