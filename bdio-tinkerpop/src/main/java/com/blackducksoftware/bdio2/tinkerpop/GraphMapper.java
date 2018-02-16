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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collector;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioOptions;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdProcessor;
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
     * The JSON-LD context to use.
     */
    private final BlackDuckIoContext context;

    /**
     * The JSON-LD value object mapper to use.
     */
    private final ValueObjectMapper valueObjectMapper;

    /**
     * Options for creating BDIO documents.
     */
    private final BdioOptions bdioOptions;

    private GraphMapper(Builder builder) {
        context = Optional.ofNullable(builder.tokens).map(BlackDuckIoContext::create)
                .orElseGet(() -> BlackDuckIoContext.build().create());

        // Construct the value object mapper
        ValueObjectMapper.Builder valueObjectMapperBuilder = new ValueObjectMapper.Builder();
        context.forEachEmbeddedType((label, id) -> {
            // Normally the ValueObjectMapper only tracks fully qualified types but we need to use it prior to
            // JSON-LD expansion so we need to tell it to recognize the vertex labels as well
            valueObjectMapperBuilder.addEmbeddedType(label);
            valueObjectMapperBuilder.addEmbeddedType(id);
        });
        context.multiDataValueProperties().forEach(valueObjectMapperBuilder::addMultiValueKey);
        builder.datatypes.forEach(valueObjectMapperBuilder::useDatatypeHandler);
        builder.multiValueCollector.ifPresent(valueObjectMapperBuilder::multiValueCollector);
        valueObjectMapper = valueObjectMapperBuilder.build();

        // Set our value object mapper as the context mapper
        ValueObjectMapper.setContextValueObjectMapper(valueObjectMapper);

        // Construct the BDIO options
        BdioOptions.Builder optionsBuilder = new BdioOptions.Builder();
        optionsBuilder.forContentType(builder.contentType, builder.expandContext);
        optionsBuilder.applicationContext(context.applicationContext());
        builder.injectedDocuments.forEach(optionsBuilder::injectDocument);
        bdioOptions = optionsBuilder.build();
    }

    /**
     * Returns the configured value object mapper.
     */
    public ValueObjectMapper valueObjectMapper() {
        return valueObjectMapper;
    }

    /**
     * Creates a new BDIO document.
     */
    public RxJavaBdioDocument newDocument() {
        return new RxJavaBdioDocument(bdioOptions);
    }

    public Optional<String> metadataLabel() {
        return Optional.ofNullable(context.metadataLabel());
    }

    public Optional<String> rootLabel() {
        return Optional.ofNullable(context.rootLabel());
    }

    public Optional<String> identifierKey() {
        return Optional.ofNullable(context.identifierKey());
    }

    public Optional<String> implicitKey() {
        return Optional.ofNullable(context.implicitKey());
    }

    public Optional<String> partitionKey() {
        return Optional.ofNullable(context.partitionKey());
    }

    public Optional<String> unknownKey() {
        return Optional.ofNullable(context.unknownKey());
    }

    /**
     * Checks to see if the specified key represents a data property.
     */
    public boolean isDataPropertyKey(String key) {
        return context.dataProperties().containsKey(key);
    }

    /**
     * Checks to see if the specified key represents an object property.
     */
    public boolean isObjectPropertyKey(String key) {
        return context.objectProperties().containsKey(key);
    }

    public boolean isUnknownKey(String key) {
        return Objects.equals(context.unknownKey(), key);
    }

    public boolean isImplicitKey(String key) {
        return Objects.equals(context.implicitKey(), key);
    }

    public boolean isSpecialKey(String key) {
        return context.isSpecialKey(key);
    }

    /**
     * Checks to see if the specified label is an embedded class.
     */
    public boolean isEmbeddedLabel(String label) {
        return context.embeddedClasses().contains(label);
    }

    public Set<String> excludedLabels() {
        return context.excludedLabels();
    }

    public Map<String, Object> compact(Map<String, Object> input) throws JsonLdError {
        return JsonLdProcessor.compact(input, context.serialize(), bdioOptions.jsonLdOptions());
    }

    public List<Object> expand(Object input) throws JsonLdError {
        return JsonLdProcessor.expand(input, bdioOptions.jsonLdOptions());
    }

    public Map<String, Object> frame() {
        return context.frame();
    }

    /**
     * Preserves all of the unknown properties, serialized as a single string. If there are no unknown properties in
     * the supplied node map, then the supplied action is not invoked.
     */
    public void preserveUnknownProperties(Map<String, Object> node, BiConsumer<String, Object> propertyConsumer) {
        if (context.unknownKey() != null) {
            Map<String, Object> unknownData = Maps.filterKeys(node, context::isUnknownKey);
            if (!unknownData.isEmpty()) {
                try {
                    propertyConsumer.accept(context.unknownKey(), JsonUtils.toString(unknownData));
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

        @Nullable
        private BlackDuckIoTokens tokens;

        private final Map<String, DatatypeHandler<?>> datatypes = new LinkedHashMap<>();

        private Optional<Collector<? super Object, ?, ?>> multiValueCollector = Optional.empty();

        @Nullable
        private Bdio.ContentType contentType;

        @Nullable
        private Object expandContext;

        private final Map<String, CharSequence> injectedDocuments = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder tokens(@Nullable BlackDuckIoTokens tokens) {
            this.tokens = tokens;
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

        public Builder forContentType(@Nullable Bdio.ContentType contentType, @Nullable Object expandContext) {
            this.contentType = contentType;
            this.expandContext = expandContext;
            return this;
        }

        public Builder injectDocument(String iri, CharSequence content) {
            injectedDocuments.put(Objects.requireNonNull(iri), Objects.requireNonNull(content));
            return this;
        }

        public GraphMapper create() {
            return new GraphMapper(this);
        }
    }

}
