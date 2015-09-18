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
package com.blackducksoftware.bom.io;

import java.util.Map;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.Term;
import com.github.jsonldjava.core.Context;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.google.common.collect.ImmutableMap;

/**
 * A context for generating linked data.
 *
 * @author jgustie
 */
public class LinkedDataContext {

    /**
     * Use a JSON-LD context object from the reference implementation to make sure we handle the specification
     * correctly. This introduces some overhead for the sake of correctness.
     */
    private final Context context;

    /**
     * An internal context which contains some extra term definitions for abusing the compaction/expansion
     * implementation.
     */
    private final Context internalContext;

    public LinkedDataContext() {
        this(new JsonLdOptions());
    }

    public LinkedDataContext(JsonLdOptions opts) {
        try {
            // Construct the default context
            context = new Context(opts).parse(ImmutableMap.builder()
                    .put("@vocab", "http://blackducksoftware.com/rdf/terms#")
                    .put("spdx", "http://spdx.org/rdf/terms#")
                    .put("doap", "http://usefulinc.com/ns/doap#")
                    .put("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
                    .build());

            // The context we use for abuse
            internalContext = context.parse(ImmutableMap.builder()
                    .put("_:type", ImmutableMap.of("@type", "@vocab"))
                    .build());
        } catch (JsonLdError e) {
            throw new IllegalStateException("parsing internal context failed");
        }
    }

    /**
     * Generates a serialized version of the context.
     */
    public Map<String, Object> serialize() {
        return context.serialize();
    }

    /**
     * Returns the compacted term name as a string.
     */
    public String compactTerm(Term term) {
        // Force special treatment by compacting a "@type"
        return compactValue(JsonLdTerm.TYPE, term).toString();
    }

    /**
     * Returns the expanded field name for a term as a string.
     */
    public String expandTerm(String term) {
        // Force special treatment by expanding a "@type"
        return expandValue(JsonLdTerm.TYPE, term).toString();
    }

    /**
     * Produces a normalized value for a given input according to this context's rules for the supplied term.
     */
    @Nullable
    public Object compactValue(Term term, @Nullable Object value) {
        if (term.toString().equals("@type")) {
            return internalContext.compactValue("_:type", id(value));
        } else {
            return context.compactValue(term.toString(), value(value));
        }
    }

    /**
     * Produces a normalized value for a given input according to this context's rules for the supplied term.
     */
    @Nullable
    public Object expandValue(Term term, @Nullable Object value) {
        try {
            if (term.toString().equals("@type")) {
                return ((Map<?, ?>) internalContext.expandValue("_:type", value)).get("@id");
            } else {
                return ((Map<?, ?>) context.expandValue(term.toString(), value)).get("@value");
            }
        } catch (JsonLdError e) {
            throw new IllegalStateException("failed to expand value for term: " + term, e);
        }
    }

    private static Map<String, Object> id(Object id) {
        return ImmutableMap.of("@id", (Object) id.toString());
    }

    private static Map<String, Object> value(@Nullable Object value) {
        // TODO Empty map or do we need to return a mapping of @value=>null?
        return value != null ? ImmutableMap.of("@value", value) : ImmutableMap.<String, Object> of();
    }

}
