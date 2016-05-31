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

import java.util.Arrays;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;

/**
 * Type instance to use when there is no constant value.
 *
 * @author jgustie
 */
public class SimpleType extends SimpleBase implements Type {
    /**
     * Cache of known types.
     */
    private static LoadingCache<String, Type> INSTANCES = CacheBuilder.newBuilder().build(new CacheLoader<String, Type>() {
        @Override
        public Type load(String key) {
            return new SimpleType(key);
        }
    });

    static {
        // Preload the cache with the known types so we don't create instances we don't need
        for (Type type : Iterables.concat(
                Arrays.asList(BlackDuckType.values()),
                Arrays.asList(DoapType.values()),
                Arrays.asList(SpdxType.values()),
                Arrays.asList(XmlSchemaType.values()))) {
            INSTANCES.put(type.toString(), type);
        }
    }

    private SimpleType(String fullyQualifiedName) {
        super(fullyQualifiedName);
    }

    /**
     * Returns a string converter for types. Will return existing constants when possible.
     */
    public static Function<String, Type> stringConverter() {
        // TODO Wrap this
        return INSTANCES;
    }

    /**
     * Converts a string to type. Will return an existing constant when possible.
     */
    public static Type create(String value) {
        return apply(stringConverter(), value);
    }
}
