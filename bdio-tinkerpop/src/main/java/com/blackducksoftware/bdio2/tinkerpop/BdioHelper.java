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
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.T;

import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.Maps;
import com.hazelcast.util.function.BiConsumer;

/**
 * Helper methods for w
 *
 * @author jgustie
 */
final class BdioHelper {

    // TODO Do we need a sort order that is stable across BDIO versions?
    // TODO Do we need to promote the File's HID column?
    private static Comparator<Map.Entry<String, Object>> DATA_PROPERTY_ORDER = Comparator.<Map.Entry<String, Object>, String> comparing(Map.Entry::getKey)
            .reversed();

    /**
     * Returns key/value pairs for the data properties of the specified BDIO node.
     */
    public static Object[] getNodeProperties(Map<String, Object> node, boolean includeSpecial, BlackDuckIoConfig config, BdioFrame frame) {
        // IMPORTANT: Add elements in reverse order of importance (e.g. T.id should be last!)
        // TODO Or does it matter because Sqlg pushes them through a ConcurrentHashMap?
        // TODO Should this just use a LinkedHashMap?
        Stream.Builder<Map.Entry<?, ?>> properties = Stream.builder();

        // Unknown properties
        config.unknownKey().ifPresent(key -> {
            preserveUnknownProperties(node)
                    .map(json -> Maps.immutableEntry(key, json))
                    .ifPresent(properties);
        });

        // Sorted data properties
        Maps.transformValues(node, config.valueObjectMapper()::fromFieldValue).entrySet().stream()
                .filter(e -> frame.isDataPropertyKey(e.getKey()))
                .sorted(DATA_PROPERTY_ORDER)
                .forEachOrdered(properties);

        // Special properties that can be optionally included
        if (includeSpecial) {
            // TODO Can we use ElementIdStrategy instead?
            config.identifierKey().ifPresent(key -> {
                Optional.ofNullable(node.get(JsonLdConsts.ID))
                        .map(id -> Maps.immutableEntry(key, id))
                        .ifPresent(properties);
            });

            config.partitionStrategy()
                    .map(s -> Maps.immutableEntry(s.getPartitionKey(), s.getWritePartition()))
                    .ifPresent(properties);

            Optional.ofNullable(node.get(JsonLdConsts.TYPE))
                    .map(label -> Maps.immutableEntry(T.label, label))
                    .ifPresent(properties);

            // NOTE: If the graph does not support user identifiers, this value gets ignored
            // TODO If user identifiers aren't support, skip the computation...
            // NOTE: If the graph supports user identifiers, we need both the JSON-LD identifier
            // and the write partition (since the same identifier can exist in multiple partitions)

            Optional.ofNullable(node.get(JsonLdConsts.ID))
                    .map(id -> generateId(config.partitionStrategy(), id))
                    .map(id -> Maps.immutableEntry(T.id, id))
                    .ifPresent(properties);
        }

        // Convert the whole thing into an array
        return properties.build()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                .toArray();
    }

    public static Object generateId(Optional<PartitionStrategy> partitionStrategy, Object id) {
        // TODO Can we use a list here instead of strings?
        return partitionStrategy
                .map(PartitionStrategy::getWritePartition)
                // TODO Use a query parameter instead of the fragment
                .map(writePartition -> (Object) (id + "#" + writePartition))
                .orElse(id);
    }

    /**
     * Returns all of the unknown properties, serialized as a single string. If there are no unknown properties in
     * the supplied node map, then the optional will be empty.
     */
    public static Optional<String> preserveUnknownProperties(Map<String, Object> node) {
        return Optional.of(Maps.filterKeys(node, BdioHelper::isUnknownKey))
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

    private BdioHelper() {
        assert false;
    }
}
