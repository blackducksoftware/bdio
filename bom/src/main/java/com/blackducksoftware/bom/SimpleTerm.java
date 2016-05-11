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

    private final URI uri;

    private SimpleTerm(String fullyQualifiedName) {
        // Verify that the supplied term is a valid non-empty IRI (URI)
        checkArgument(!fullyQualifiedName.isEmpty());
        checkArgument(fullyQualifiedName.charAt(0) != '@', "unexpected keyword: %s", fullyQualifiedName.substring(1));
        uri = URI.create(fullyQualifiedName);
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
