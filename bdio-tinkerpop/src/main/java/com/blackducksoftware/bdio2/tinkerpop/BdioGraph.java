/*
 * Copyright 2017 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2.tinkerpop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.Maps;
import com.hazelcast.util.function.BiConsumer;

/**
 * Details of the BDIO graph model.
 *
 * @author jgustie
 */
class BdioGraph {

    /**
     * Creates a semi-hidden namespace for BDIO internals.
     */
    public static final class Hidden {

        /**
         * Use "_" to identify internal BDIO properties.
         */
        private static final String HIDDEN_PREFIX = "_";

        /**
         * Hides a key.
         */
        public static String hide(String key) {
            return isHidden(key) ? key : HIDDEN_PREFIX.concat(key);
        }

        /**
         * Checks if a key is hidden.
         */
        public static boolean isHidden(final String key) {
            return key.startsWith(HIDDEN_PREFIX);
        }
    }

    /**
     * Preserving JSON-LD properties that are unrecognizable in the current context.
     */
    public static final class Unknown {

        /**
         * Returns all of the unknown properties, serialized as a single string. If there are no unknown properties in
         * the supplied node map, then the optional will be empty.
         */
        public static Optional<String> preserveUnknownProperties(Map<String, Object> node) {
            return Optional.of(Maps.filterKeys(node, Unknown::isUnknownKey))
                    .filter(m -> !m.isEmpty())
                    .map(m -> {
                        try {
                            return JsonUtils.toString(m);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }

        /**
         * Given a preserved serialization of unknown properties, replay them back to the supplied consumer.
         */
        public static void restoreUnknownProperties(@Nullable Object value, BiConsumer<String, Object> unknownPropertyConsumer) {
            if (value != null) {
                try {
                    Object unknown = JsonUtils.fromString(value.toString());
                    if (unknown instanceof Map<?, ?>) {
                        for (Map.Entry<?, ?> unknownProperty : ((Map<?, ?>) unknown).entrySet()) {
                            unknownPropertyConsumer.accept((String) unknownProperty.getKey(), unknownProperty.getValue());
                        }
                    }
                } catch (IOException e) {
                    // Ignore this...
                }
            }
        }

        /**
         * Check if a key represents an unknown property.
         */
        private static boolean isUnknownKey(String key) {
            // If framing did not recognize the attribute, it will still have a scheme or prefix separator
            return key.indexOf(':') >= 0;
        }
    }

    /**
     * (B)DIO tokens.
     */
    public static final class B {

        /**
         * Node identifier ({@code @id} property).
         */
        public static final String id = Hidden.hide("id");

        /**
         * Preservation of unknown properties.
         */
        public static final String unknown = Hidden.hide("unknown");

    }

}
