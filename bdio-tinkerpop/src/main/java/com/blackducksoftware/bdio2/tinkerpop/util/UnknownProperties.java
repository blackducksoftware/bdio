/*
 * Copyright 2018 Synopsys, Inc.
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
package com.blackducksoftware.bdio2.tinkerpop.util;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;

/**
 * Helper used to preserve unknown properties as JSON blobs.
 *
 * @author jgustie
 */
public class UnknownProperties {

    /**
     * The JSON object mapper used to preserve unknown values.
     */
    private static final ObjectMapper UNKNOWN_DATA_MAPPER = new ObjectMapper();
    static {
        // See `com.github.jsonldjava.utils.JsonUtils`
        UNKNOWN_DATA_MAPPER.getFactory().disable(JsonFactory.Feature.INTERN_FIELD_NAMES);
        UNKNOWN_DATA_MAPPER.getFactory().disable(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES);
    }

    /**
     * Preserves all of the unknown properties. If there are no unknown properties in the supplied node map, then the
     * supplied action is not invoked.
     */
    public static void preserve(Optional<String> unknownKey, Map<String, Object> node, BiConsumer<? super String, ? super Object> propertyConsumer) {
        if (unknownKey.isPresent()) {
            // If framing did not recognize the attribute, it will still have a scheme or prefix separator
            // This implementation of the check is probably a lot easier then looking at all the possible keys
            Map<String, Object> unknownData = Maps.filterKeys(node, key -> key.indexOf(':') >= 0);
            if (!unknownData.isEmpty()) {
                propertyConsumer.accept(unknownKey.get(), UNKNOWN_DATA_MAPPER.valueToTree(unknownData));
            }
        }
    }

    /**
     * Given a preserved serialization of unknown properties, replay them back to the supplied consumer.
     */
    public static void restore(@Nullable Object value, BiConsumer<String, Object> unknownPropertyConsumer) {
        try {
            Map<?, ?> unknownData = Collections.emptyMap();
            if (value instanceof JsonNode) {
                unknownData = UNKNOWN_DATA_MAPPER.treeToValue((JsonNode) value, Map.class);
            } else if (value instanceof CharSequence) {
                unknownData = UNKNOWN_DATA_MAPPER.readValue(value.toString(), Map.class);
            } else if (value != null) {
                throw new IllegalArgumentException("unable to restore unknown properties from: " + value);
            }

            // Does Jackson have better built-in support for Map<String, Object>?
            for (Map.Entry<?, ?> e : unknownData.entrySet()) {
                unknownPropertyConsumer.accept((String) e.getKey(), e.getValue());
            }
        } catch (IOException e) {
            // TODO Should we serialize the failure, e.g. `upc.accept("unknownRestoreError", e.toString())`?
            return;
        }
    }

}
