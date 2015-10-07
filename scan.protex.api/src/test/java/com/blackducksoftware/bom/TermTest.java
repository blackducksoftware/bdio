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

import java.net.URI;
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
    public void verifyKnownTermSyntax() {
        // Iterate over the known terms and verify they all parse as a URI
        // This is actually important because we don't verify this at runtime
        for (Term term : KNOWN_TERMS) {
            URI.create(term.toString());
        }
    }

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
