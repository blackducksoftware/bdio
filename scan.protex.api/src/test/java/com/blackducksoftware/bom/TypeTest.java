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

import java.util.Arrays;

import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

/**
 * Tests for various types.
 *
 * @author jgustie
 */
public class TypeTest {

    /**
     * Collect all of the known type constants we have in a single iterable.
     */
    private static final Iterable<Type> KNOWN_TYPES = Iterables.concat(
            Arrays.<Type> asList(BlackDuckType.values()),
            Arrays.<Type> asList(DoapType.values()),
            Arrays.<Type> asList(SpdxType.values()),
            Arrays.<Type> asList(XmlSchemaType.values()));

    @Test
    public void testSimpleTypeInstanceCache() {
        // Two calls to create will produce the same instance
        assertThat(SimpleType.create("http://example.com/rdf/terms#foo"))
                .isSameAs(SimpleType.create("http://example.com/rdf/terms#foo"));

        // The converter and create share an instance pool
        assertThat(SimpleType.create("http://example.com/rdf/terms#foo"))
                .isSameAs(SimpleType.stringConverter().apply("http://example.com/rdf/terms#foo"));

        // Simple type should not create new instances for known types
        for (Type type : KNOWN_TYPES) {
            assertThat(SimpleType.create(type.toString())).isSameAs(type);
        }
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testSimpleTypeCreate_null() {
        SimpleType.create(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSimpleTypeCreate_empty() {
        SimpleType.create("");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSimpleTypeCreate_invalid() {
        SimpleType.create(":");
    }

}
