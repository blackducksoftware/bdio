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
package com.blackducksoftware.bom;

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;

import org.junit.Test;

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

    @Test(expected = NullPointerException.class)
    public void testSimpleTypeCreate_null() {
        SimpleType.create(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSimpleTypeCreate_empty() {
        SimpleType.create("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSimpleTypeCreate_invalid() {
        SimpleType.create(":");
    }

}
