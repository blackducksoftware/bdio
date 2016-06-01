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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;

/**
 * Term instance to use when there is no constant value.
 *
 * @author jgustie
 */
public class SimpleTerm extends SimpleBase implements Term {
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
        for (Term term : ImmutableSet.<Term> builder()
                .add(BlackDuckTerm.values())
                .add(DoapTerm.values())
                .add(RdfsTerm.values())
                .add(RdfTerm.values())
                .add(SpdxTerm.values())
                .build()) {
            INSTANCES.put(term.toString(), term);
        }
    }

    private SimpleTerm(String fullyQualifiedName) {
        super(fullyQualifiedName);
        checkArgument(fullyQualifiedName.charAt(0) != '@', "unexpected keyword: %s", fullyQualifiedName.substring(1));
    }

    /**
     * Returns a string converter for terms. Will return existing constants when possible.
     */
    public static Function<String, Term> stringConverter() {
        // TODO Wrap this
        return INSTANCES;
    }

    /**
     * Converts a string to term. Will return an existing constant when possible.
     */
    public static Term create(String value) {
        return apply(stringConverter(), value);
    }
}
