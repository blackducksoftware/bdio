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
import java.net.URI;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.T;

import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.Maps;

/**
 * Helper methods for w
 *
 * @author jgustie
 */
final class BdioHelper {

    /**
     * Returns key/value pairs for the data properties of the specified BDIO node.
     */
    public static Object[] getNodeProperties(Map<String, Object> node, boolean includeSpecial,
            BdioFrame frame, ValueObjectMapper valueObjectMapper, @Nullable PartitionStrategy partitionStrategy) {
        Stream.Builder<Map.Entry<?, ?>> properties = Stream.builder();

        // Special properties that can be optionally included
        if (includeSpecial) {
            Optional.ofNullable(node.get(JsonLdConsts.ID)).map(id -> URI.create((String) id))
                    .map(id -> Maps.immutableEntry(T.id, id))
                    .ifPresent(properties);

            Optional.ofNullable(node.get(JsonLdConsts.TYPE))
                    .map(label -> Maps.immutableEntry(T.label, label))
                    .ifPresent(properties);

            Optional.ofNullable(partitionStrategy)
                    .map(s -> Maps.immutableEntry(s.getPartitionKey(), s.getWritePartition()))
                    .ifPresent(properties);

            Optional.ofNullable(node.get(JsonLdConsts.ID))
                    .map(id -> Maps.immutableEntry(Tokens.id, id))
                    .ifPresent(properties);
        }

        // Sorted data properties
        // TODO Do we need a sort order that is stable across BDIO versions?
        Maps.transformValues(node, valueObjectMapper::fromFieldValue).entrySet().stream()
                .filter(e -> frame.isDataPropertyKey(e.getKey()))
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEachOrdered(properties);

        // Unknown properties
        Optional.of(Maps.filterKeys(node, BdioHelper::isUnknownKey))
                .filter(m -> !m.isEmpty())
                .map(m -> {
                    try {
                        return JsonUtils.toString(m);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .map(json -> Maps.immutableEntry(Tokens.unknown, json))
                .ifPresent(properties);

        // Convert the whole thing into an array
        return properties.build()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                .toArray();
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