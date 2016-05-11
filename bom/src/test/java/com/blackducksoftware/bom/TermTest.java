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
 * Tests for various terms.
 *
 * @author jgustie
 */
public class TermTest {

    /**
     * Collect all of the known term constants we have in a single iterable.
     */
    private static final Iterable<Term> KNOWN_TERMS = Iterables.concat(
            Arrays.<Term> asList(BlackDuckTerm.values()),
            Arrays.<Term> asList(DoapTerm.values()),
            Arrays.<Term> asList(RdfsTerm.values()),
            Arrays.<Term> asList(RdfTerm.values()),
            Arrays.<Term> asList(SpdxTerm.values()));

    @Test
    public void testSimpleTermInstanceCache() {
        // Two calls to create will produce the same instance
        assertThat(SimpleTerm.create("http://example.com/rdf/terms#foo"))
                .isSameAs(SimpleTerm.create("http://example.com/rdf/terms#foo"));

        // The converter and create share an instance pool
        assertThat(SimpleTerm.create("http://example.com/rdf/terms#foo"))
                .isSameAs(SimpleTerm.stringConverter().apply("http://example.com/rdf/terms#foo"));

        // Simple term should not create new instances for known terms
        for (Term term : KNOWN_TERMS) {
            assertThat(SimpleTerm.create(term.toString())).isSameAs(term);
        }
    }

    @Test(expected = NullPointerException.class)
    public void testSimpleTermCreate_null() {
        SimpleTerm.create(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSimpleTermCreate_empty() {
        SimpleTerm.create("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSimpleTermCreate_invalid() {
        SimpleTerm.create(":");
    }

}
