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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collector;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.Maps;

/**
 * The representation of the BDIO graph schema. This class describes how to perform the mapping between a JSON-LD graph
 * and a TinkerPop graph. It is expected that the TinkerPop graph has a fixed schema while the JSON-LD graph may include
 * extensible data.
 *
 * @author jgustie
 */
public class GraphMapper {

    /**
     * The token definitions to use.
     */
    private final BlackDuckIoTokens tokens;

    /**
     * The JSON-LD value object mapper to use.
     */
    private final ValueObjectMapper valueObjectMapper;

    private GraphMapper(Builder builder) {
        // Wrap the tokens to make sure we get the built in definitions
        tokens = builder.tokens.map(DefaultBlackDuckIoTokens::create).orElseGet(() -> DefaultBlackDuckIoTokens.build().create());

        // Construct the value object mapper
        ValueObjectMapper.Builder valueObjectMapperBuilder = new ValueObjectMapper.Builder();
        builder.datatypes.forEach(valueObjectMapperBuilder::useDatatypeHandler);
        builder.multiValueCollector.ifPresent(valueObjectMapperBuilder::multiValueCollector);
        tokens.multiDataValueProperties().forEach(valueObjectMapperBuilder::addMultiValueKey);
        tokens.embeddedClasses().forEach(label -> {
            // Normally the ValueObjectMapper only tracks fully qualified types but we need to use it prior to
            // JSON-LD expansion so we need to tell it to recognize the vertex labels as well
            valueObjectMapperBuilder.addEmbeddedType(label);
            valueObjectMapperBuilder.addEmbeddedType(tokens.classes().get(label));
        });
        valueObjectMapper = valueObjectMapperBuilder.build();

        // Set our value object mapper as the context mapper
        ValueObjectMapper.setContextValueObjectMapper(valueObjectMapper);
    }

    /**
     * Returns the currently configured value object mapper.
     */
    public ValueObjectMapper valueObjectMapper() {
        return valueObjectMapper;
    }

    /**
     * Checks to see if the specified label is an embedded class.
     */
    public boolean isEmbeddedLabel(String label) {
        return tokens.embeddedClasses().contains(label);
    }

    /**
     * Checks to see if the specified key is a data property.
     */
    public boolean isDataPropertyKey(String key) {
        return tokens.dataProperties().containsKey(key);
    }

    /**
     * Checks to see if the specified key is an object property.
     */
    public boolean isObjectPropertyKey(String key) {
        return tokens.objectProperties().containsKey(key);
    }

    /**
     * Returns the metadata vertex label, if configured.
     */
    public Optional<String> metadataLabel() {
        return Optional.ofNullable(tokens.metadataLabel());
    }

    /**
     * Returns the root edge label, if configured.
     */
    public Optional<String> rootLabel() {
        return Optional.ofNullable(tokens.rootLabel());
    }

    /**
     * Returns the alternate identifier storage property key, if configured.
     */
    public Optional<String> identifierKey() {
        return Optional.ofNullable(tokens.identifierKey());
    }

    /**
     * Returns the implicit creation property key, if configured.
     */
    public Optional<String> implicitKey() {
        return Optional.ofNullable(tokens.implicitKey());
    }

    /**
     * Returns the unknown value storage property key, if configured.
     */
    public Optional<String> unknownKey() {
        return Optional.ofNullable(tokens.unknownKey());
    }

    /**
     * Returns the labels which should be excluded from writing.
     */
    public Set<String> excludedLabels() {
        Set<String> excludedLabels = new LinkedHashSet<>();
        excludedLabels.addAll(tokens.embeddedClasses());
        metadataLabel().ifPresent(excludedLabels::add);
        return excludedLabels;
    }

    /**
     * Returns the JSON-LD {@code @context} value.
     */
    public Map<String, Object> context() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.putAll(tokens.classes());
        context.putAll(tokens.dataProperties());
        context.putAll(tokens.objectProperties());
        return context;
    }

    /**
     * Returns the JSON-LD frame value.
     */
    public Map<String, Object> frame() {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put(JsonLdConsts.CONTEXT, context());
        frame.put(JsonLdConsts.TYPE, new ArrayList<>(tokens.classes().values()));
        return frame;
    }

    // TODO Unknown property preservation should be using native JSON types for storage

    /**
     * Preserves all of the unknown properties, serialized as a single string. If there are no unknown properties in
     * the supplied node map, then the supplied action is not invoked.
     */
    public void preserveUnknownProperties(Map<String, Object> node, BiConsumer<String, Object> propertyConsumer) {
        if (tokens.unknownKey() != null) {
            // If framing did not recognize the attribute, it will still have a scheme or prefix separator
            // This implementation of the check is probably a lot easier then looking at all the possible keys
            Map<String, Object> unknownData = Maps.filterKeys(node, key -> key.indexOf(':') >= 0);
            if (!unknownData.isEmpty()) {
                try {
                    propertyConsumer.accept(tokens.unknownKey(), JsonUtils.toString(unknownData));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    /**
     * Given a preserved serialization of unknown properties, replay them back to the supplied consumer.
     */
    public void restoreUnknownProperties(@Nullable Object value, BiConsumer<String, Object> unknownPropertyConsumer) {
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

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {

        private Optional<BlackDuckIoTokens> tokens = Optional.empty();

        private final Map<String, DatatypeHandler<?>> datatypes = new LinkedHashMap<>();

        private Optional<Collector<? super Object, ?, ?>> multiValueCollector = Optional.empty();

        private Builder() {
        }

        public Builder tokens(@Nullable BlackDuckIoTokens tokens) {
            this.tokens = Optional.ofNullable(tokens);
            return this;
        }

        public Builder addDatatype(String iri, DatatypeHandler<?> handler) {
            datatypes.put(Objects.requireNonNull(iri), Objects.requireNonNull(handler));
            return this;
        }

        public Builder multiValueCollector(@Nullable Collector<? super Object, ?, ?> multiValueCollector) {
            this.multiValueCollector = Optional.ofNullable(multiValueCollector);
            return this;
        }

        public GraphMapper create() {
            return new GraphMapper(this);
        }
    }

}
