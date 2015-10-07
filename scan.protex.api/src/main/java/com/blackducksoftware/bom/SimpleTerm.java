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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * Term instance to use when there is no constant value.
 *
 * @author jgustie
 */
public class SimpleTerm implements Term, Serializable {

    // TODO Consolidate this with SimpleType?

    /**
     * Cache of known terms.
     */
    private static LoadingCache<String, Term> INSTANCES = CacheBuilder.newBuilder().build(new CacheLoader<String, Term>() {
        @Override
        public Term load(String key) {
            return new SimpleTerm(key);
        }
    });

    static {
        // Preload the cache with the known terms so we don't create instances we don't need
        for (Term term : Iterables.concat(
                Arrays.asList(BlackDuckTerm.values()),
                Arrays.asList(DoapTerm.values()),
                Arrays.asList(RdfsTerm.values()),
                Arrays.asList(RdfTerm.values()),
                Arrays.asList(SpdxTerm.values()))) {
            INSTANCES.put(term.toString(), term);
        }
    }

    /**
     * Returns a string converter for terms. Will return existing constants when possible.
     */
    public static Function<String, Term> stringConverter() {
        return INSTANCES;
    }

    /**
     * Converts a string to term. Will return an existing constant when possible.
     */
    public static Term create(String value) {
        try {
            return stringConverter().apply(value);
        } catch (UncheckedExecutionException e) {
            Throwables.propagateIfPossible(e.getCause());
            throw e;
        }
    }

    private final URI uri;

    private SimpleTerm(String fullyQualifiedName) {
        // Verify that the supplied term is a valid non-empty IRI (URI)
        checkArgument(!fullyQualifiedName.isEmpty());
        checkArgument(fullyQualifiedName.charAt(0) != '@', "unexpected keyword: %s", fullyQualifiedName.substring(1));
        uri = URI.create(fullyQualifiedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleTerm) {
            return uri.equals(((SimpleTerm) obj).uri);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return uri.toString();
    }

    @Override
    public URI toUri() {
        return uri;
    }
}
