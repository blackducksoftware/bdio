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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.T;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * The representation of the BDIO graph schema. This class describes how to perform the mapping between a JSON-LD graph
 * and a TinkerPop graph. It is expected that the TinkerPop graph has a fixed schema while the JSON-LD graph may include
 * extensible data.
 *
 * @author jgustie
 */
// TODO Should this be in a different package and broken into smaller pieces?
public class GraphMapper {

    // TODO Do we need a sort order that is stable across BDIO versions?
    // TODO Do we need to promote the File's HID column?
    private static Comparator<Map.Entry<String, Object>> DATA_PROPERTY_ORDER = Comparator.<Map.Entry<String, Object>, String> comparing(Map.Entry::getKey)
            .reversed();

    // TODO It seems like there is connection here with JsonLdOptions, they are created by the documentBuilder but only
    // when the document is built...THEREFORE we would need private ownership of the BdioDocument.Builder in this class
    // If we were to offer compact and expand JSON-LD operations here, it wouldn't matter

    // TODO The BlackDuckIoOperations.ROOT_PROJECT should be a "rootProjectLabel" in here...

    /**
     * The JSON-LD value object mapper to use.
     */
    private final ValueObjectMapper valueObjectMapper;

    /**
     * Builder for creating BDIO documents.
     */
    private final BdioDocument.Builder documentBuilder;

    /**
     * The mapping of vertex labels to {@code @type} IRIs. Does not include the metadata vertex label.
     */
    private final ImmutableMap<String, String> classes;

    /**
     * The mapping of vertex property names or terms to IRIs.
     */
    private final ImmutableMap<String, String> dataProperties;

    /**
     * The mapping of vertex property names or terms to IRIs.
     */
    private final ImmutableMap<String, String> objectProperties;

    /**
     * The property key used to persist JSON-LD node identifiers.
     */
    private final Optional<String> identifierKey;

    /**
     * The property key used to persist "unknown" JSON-LD node properties.
     */
    private final Optional<String> unknownKey;

    /**
     * The property key used to persist a flag indicating implicit creation of missing BDIO data.
     */
    private final Optional<String> implicitKey;

    /**
     * The vertex label used to persist JSON-LD named graph metadata.
     */
    private final Optional<String> metadataLabel;

    /**
     * The partitioning strategy used isolate JSON-LD sub-graphs.
     */
    private final Optional<PartitionStrategy> partitionStrategy;

    private GraphMapper(Builder builder) {
        valueObjectMapper = builder.valueObjectMapperBuilder.build();
        documentBuilder = builder.documentBuilder.orElseGet(BdioDocument.Builder::new);
        classes = ImmutableMap.copyOf(builder.classes);
        dataProperties = ImmutableMap.copyOf(builder.dataProperties);
        objectProperties = ImmutableMap.copyOf(builder.objectProperties);
        identifierKey = Objects.requireNonNull(builder.identifierKey);
        unknownKey = Objects.requireNonNull(builder.unknownKey);
        implicitKey = Objects.requireNonNull(builder.implicitKey);
        metadataLabel = Objects.requireNonNull(builder.metadataLabel);
        partitionStrategy = Objects.requireNonNull(builder.partitionStrategy);
    }

    public ValueObjectMapper valueObjectMapper() {
        return valueObjectMapper;
    }

    public <D extends BdioDocument> D newBdioDocument(Class<D> type) {
        // Do not expose the whole builder as it is mutable
        return documentBuilder.build(type);
    }

    /**
     * Iterates over the known type labels.
     */
    public void forEachTypeLabel(Consumer<String> typeLabelConsumer) {
        classes.keySet().forEach(typeLabelConsumer);
    }

    /**
     * Checks to see if the specified key represents a data property.
     */
    public boolean isDataPropertyKey(String key) {
        return dataProperties.containsKey(key);
    }

    /**
     * Checks to see if the specified key represents an object property.
     */
    public boolean isObjectPropertyKey(String key) {
        return objectProperties.containsKey(key);
    }

    /**
     * Check to see if the specified key represents an unknown property.
     */
    public boolean isUnknownKey(String key) {
        // If framing did not recognize the attribute, it will still have a scheme or prefix separator
        return key.indexOf(':') >= 0;
    }

    public Optional<String> identifierKey() {
        return identifierKey;
    }

    public Optional<String> unknownKey() {
        return unknownKey;
    }

    public Optional<String> implicitKey() {
        return implicitKey;
    }

    public Optional<String> metadataLabel() {
        return metadataLabel;
    }

    public Optional<PartitionStrategy> partitionStrategy() {
        return partitionStrategy;
    }

    public Map<String, Object> frame() {
        // This must construct a new mutable structure for the JSON-LD API
        Map<String, Object> context = new LinkedHashMap<>();
        context.putAll(classes);
        // TODO Should we be generating @id/@type maps for data properties?
        context.putAll(dataProperties);
        context.putAll(objectProperties);

        List<String> type = new ArrayList<>();
        type.addAll(classes.values());

        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put(JsonLdConsts.CONTEXT, context);
        frame.put(JsonLdConsts.TYPE, type);
        return frame;
    }

    /**
     * Returns all of the unknown properties, serialized as a single string. If there are no unknown properties in
     * the supplied node map, then the optional will be empty.
     */
    public Optional<String> preserveUnknownProperties(Map<String, Object> node) {
        return Optional.of(Maps.filterKeys(node, this::isUnknownKey))
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

    public Object generateId(Object id) {
        // TODO Can we use a list here instead of strings?
        return partitionStrategy
                .map(PartitionStrategy::getWritePartition)
                // TODO Use a query parameter instead of the fragment
                .map(writePartition -> (Object) (id + "#" + writePartition))
                .orElse(id);
    }

    /**
     * Returns key/value pairs for the data properties of the specified BDIO node.
     */
    public Object[] getNodeProperties(Map<String, Object> node, boolean includeSpecial) {
        // IMPORTANT: Add elements in reverse order of importance (e.g. T.id should be last!)
        // TODO This could be a restriction from an old version of Sqlg
        // TODO Or does it matter because Sqlg pushes them through a ConcurrentHashMap?
        // TODO Should this just use a LinkedHashMap?
        Stream.Builder<Map.Entry<?, ?>> properties = Stream.builder();

        // Unknown properties
        unknownKey().ifPresent(key -> {
            preserveUnknownProperties(node)
                    .map(json -> Maps.immutableEntry(key, json))
                    .ifPresent(properties);
        });

        // Sorted data properties
        Maps.transformValues(node, valueObjectMapper()::fromFieldValue).entrySet().stream()
                .filter(e -> isDataPropertyKey(e.getKey()))
                .sorted(DATA_PROPERTY_ORDER)
                .forEachOrdered(properties);

        // Special properties that can be optionally included
        if (includeSpecial) {
            // TODO Can we use ElementIdStrategy instead?
            identifierKey().ifPresent(key -> {
                Optional.ofNullable(node.get(JsonLdConsts.ID))
                        .map(id -> Maps.immutableEntry(key, id))
                        .ifPresent(properties);
            });

            partitionStrategy()
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
                    .map(id -> generateId(id))
                    .map(id -> Maps.immutableEntry(T.id, id))
                    .ifPresent(properties);
        }

        // Convert the whole thing into an array
        return properties.build()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                .toArray();
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {

        private final ValueObjectMapper.Builder valueObjectMapperBuilder;

        private Optional<BdioDocument.Builder> documentBuilder = Optional.empty();

        private final Map<String, String> classes;

        private final Map<String, String> dataProperties;

        private final Map<String, String> objectProperties;

        private Optional<String> identifierKey = Optional.empty();

        private Optional<String> unknownKey = Optional.empty();

        private Optional<String> implicitKey = Optional.empty();

        private Optional<String> metadataLabel = Optional.empty();

        private Optional<PartitionStrategy> partitionStrategy = Optional.empty();

        public Builder() {
            valueObjectMapperBuilder = new ValueObjectMapper.Builder();

            classes = new LinkedHashMap<>();
            for (Bdio.Class bdioClass : Bdio.Class.values()) {
                classes.put(bdioClass.name(), bdioClass.toString());
            }

            dataProperties = new LinkedHashMap<>();
            for (Bdio.DataProperty bdioDataProperty : Bdio.DataProperty.values()) {
                dataProperties.put(bdioDataProperty.name(), bdioDataProperty.toString());
            }

            objectProperties = new LinkedHashMap<>();
            for (Bdio.ObjectProperty bdioObjectProperty : Bdio.ObjectProperty.values()) {
                objectProperties.put(bdioObjectProperty.name(), bdioObjectProperty.toString());
            }
        }

        public Builder documentBuilder(@Nullable BdioDocument.Builder documentBuilder) {
            this.documentBuilder = Optional.ofNullable(documentBuilder);
            return this;
        }

        public Builder addDatatype(String iri, DatatypeHandler<?> handler) {
            valueObjectMapperBuilder.useDatatypeHandler(iri, handler);
            return this;
        }

        public Builder addEmbeddedClass(String label, String iri) {
            valueObjectMapperBuilder.addEmbeddedType(iri);
            return addClass(label, iri);
        }

        public Builder addClass(String label, String iri) {
            classes.put(Objects.requireNonNull(label), Objects.requireNonNull(iri));
            return this;
        }

        public Builder addDataProperty(String term, String iri) {
            dataProperties.put(Objects.requireNonNull(term), Objects.requireNonNull(iri));
            return this;
        }

        public Builder addObjectProperty(String term, String iri) {
            objectProperties.put(Objects.requireNonNull(term), Objects.requireNonNull(iri));
            return this;
        }

        public Builder identifierKey(@Nullable String identifierKey) {
            this.identifierKey = Optional.ofNullable(identifierKey);
            return this;
        }

        public Builder unknownKey(@Nullable String unknownKey) {
            this.unknownKey = Optional.ofNullable(unknownKey);
            return this;
        }

        public Builder implicitKey(@Nullable String implicitKey) {
            this.implicitKey = Optional.ofNullable(implicitKey);
            return this;
        }

        public Builder metadataLabel(@Nullable String metadataLabel) {
            this.metadataLabel = Optional.ofNullable(metadataLabel);
            return this;
        }

        public Builder partitionStrategy(@Nullable PartitionStrategy partitionStrategy) {
            this.partitionStrategy = Optional.ofNullable(partitionStrategy);
            return this;
        }

        public GraphMapper create() {
            return new GraphMapper(this);
        }
    }
}