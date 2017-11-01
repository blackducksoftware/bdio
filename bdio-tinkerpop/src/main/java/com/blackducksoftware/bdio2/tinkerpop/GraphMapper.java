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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationConverter;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.Bdio.Container;
import com.blackducksoftware.bdio2.BdioOptions;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdProcessor;
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

    /**
     * The JSON-LD value object mapper to use.
     */
    private final ValueObjectMapper valueObjectMapper;

    /**
     * Options for creating BDIO documents.
     */
    private final BdioOptions options;

    /**
     * The mapping of vertex labels to {@code @type} IRIs. Does not include the metadata vertex label.
     */
    private final ImmutableMap<String, String> classes;

    /**
     * The mapping of vertex labels to {@code @type} IRIs for classes that should be embedded.
     */
    private final ImmutableMap<String, String> embeddedClasses;

    /**
     * The mapping of vertex property names or terms to IRIs.
     */
    private final ImmutableMap<String, String> dataProperties;

    /**
     * The mapping of vertex property names or terms to IRIs.
     */
    private final ImmutableMap<String, String> objectProperties;

    /**
     * The vertex label used to persist JSON-LD named graph metadata.
     */
    private final Optional<String> metadataLabel;

    /**
     * The edge label used to connect metadata to a root vertex.
     */
    private final Optional<String> rootLabel;

    /**
     * The property key used to persist JSON-LD node identifiers.
     */
    // TODO Can we use ElementIdStrategy instead?
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
     * The partitioning strategy used isolate JSON-LD sub-graphs.
     */
    private final Optional<PartitionStrategy> partitionStrategy;

    private GraphMapper(Builder builder) {
        classes = ImmutableMap.copyOf(builder.classes);
        embeddedClasses = ImmutableMap.copyOf(builder.embeddedClasses);
        dataProperties = ImmutableMap.copyOf(builder.dataProperties);
        objectProperties = ImmutableMap.copyOf(builder.objectProperties);
        metadataLabel = Objects.requireNonNull(builder.metadataLabel);
        rootLabel = Objects.requireNonNull(builder.rootLabel);
        identifierKey = Objects.requireNonNull(builder.identifierKey);
        unknownKey = Objects.requireNonNull(builder.unknownKey);
        implicitKey = Objects.requireNonNull(builder.implicitKey);
        partitionStrategy = Objects.requireNonNull(builder.partitionStrategy);

        // Construct the value object mapper
        ValueObjectMapper.Builder valueObjectMapperBuilder = new ValueObjectMapper.Builder();
        builder.datatypes.forEach(valueObjectMapperBuilder::useDatatypeHandler);
        builder.embeddedClasses.forEach((k, v) -> {
            // Normally the ValueObjectMapper only tracks fully qualified types but we need to use it prior to
            // JSON-LD expansion so we need to tell it to recognize the vertex labels as well
            valueObjectMapperBuilder.addEmbeddedType(k);
            valueObjectMapperBuilder.addEmbeddedType(v);
        });
        builder.multiValueProperties.forEach(valueObjectMapperBuilder::addMultiValueKey);
        builder.multiValueCollector.ifPresent(valueObjectMapperBuilder::multiValueCollector);
        valueObjectMapper = valueObjectMapperBuilder.build();
        ValueObjectMapper.setContextValueObjectMapper(valueObjectMapper);

        // Construct the BDIO options
        BdioOptions.Builder optionsBuilder = new BdioOptions.Builder();
        builder.contentType.ifPresent(contentType -> optionsBuilder.forContentType(contentType, builder.expandContext.orElse(null)));
        optionsBuilder.applicationContext(applicationContext());
        options = optionsBuilder.build();
    }

    public static Stream<?> streamVertexPropertyValue(VertexProperty<?> vp) {
        Object value = vp.orElse(null);
        if (value == null) {
            return Stream.empty();
        } else if (value instanceof List<?>) {
            return ((List<?>) value).stream();
        } else if (value.getClass().isArray()) {
            return Stream.of((Object[]) value);
        } else {
            return Stream.of(value);
        }
    }

    public ValueObjectMapper valueObjectMapper() {
        return valueObjectMapper;
    }

    public RxJavaBdioDocument newBdioDocument() {
        return new RxJavaBdioDocument(options);
    }

    /**
     * Iterates over the known type labels.
     */
    public void forEachTypeLabel(Consumer<String> typeLabelConsumer) {
        classes.keySet().forEach(typeLabelConsumer);
    }

    /**
     * Iterates over the known embedded type labels.
     */
    public void forEachEmbeddedLabel(Consumer<String> typeLabelConsumer) {
        embeddedClasses.keySet().forEach(typeLabelConsumer);
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

    /**
     * Checks to see if the specified key represents a special property internal to the graph.
     */
    public boolean isSpecialKey(String key) {
        // TODO All keys that start with "_"?
        Predicate<String> isKey = Predicate.isEqual(key);
        return identifierKey().filter(isKey).isPresent()
                || implicitKey().filter(isKey).isPresent()
                || partitionStrategy().map(PartitionStrategy::getPartitionKey).filter(isKey).isPresent();
    }

    /**
     * Checks to see if the specified label is an embedded class.
     */
    public boolean isEmbeddedLabel(String label) {
        return embeddedClasses.containsKey(label);
    }

    public Optional<String> metadataLabel() {
        return metadataLabel;
    }

    public Optional<String> rootLabel() {
        return rootLabel;
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

    public Optional<PartitionStrategy> partitionStrategy() {
        return partitionStrategy;
    }

    public Map<String, Object> compact(Map<String, Object> input) throws JsonLdError {
        return JsonLdProcessor.compact(input, expandContext(), options.jsonLdOptions());
    }

    public List<Object> expand(Object input) throws JsonLdError {
        return JsonLdProcessor.expand(input, options.jsonLdOptions());
    }

    private Map<String, Object> expandContext() {
        // This must construct a new mutable structure for the JSON-LD API (and for our own use)
        Map<String, Object> context = new LinkedHashMap<>();
        context.putAll(classes);
        context.putAll(embeddedClasses);
        context.putAll(dataProperties);
        context.putAll(objectProperties);
        return context;
    }

    public Map<String, Object> frame() {
        List<String> type = new ArrayList<>();
        type.addAll(classes.values());
        type.addAll(embeddedClasses.values());

        // TODO Should we be generating @id/@type maps for data properties in the context?
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put(JsonLdConsts.CONTEXT, expandContext());
        frame.put(JsonLdConsts.TYPE, type);
        return frame;
    }

    @Nullable
    private Map<String, Object> applicationContext() {
        Map<String, Object> context = expandContext();

        // Remove everything that could come from BDIO
        removeKeysAndValues(Bdio.Class.class, context);
        removeKeysAndValues(Bdio.DataProperty.class, context);
        removeKeysAndValues(Bdio.ObjectProperty.class, context);

        // Remove everything that is for internal use
        // NOTE: These should all be no-ops if pre-build validation is working correctly
        metadataLabel.ifPresent(context::remove);
        context.keySet().removeIf(this::isSpecialKey);

        return context.isEmpty() ? null : context;
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

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, DatatypeHandler<?>> datatypes;

        private final Map<String, String> classes;

        private final Map<String, String> embeddedClasses;

        private final Map<String, String> dataProperties;

        private final Map<String, String> objectProperties;

        private final Set<String> multiValueProperties;

        private Optional<Collector<? super Object, ?, ?>> multiValueCollector = Optional.empty();

        private Optional<Bdio.ContentType> contentType = Optional.empty();

        private Optional<Object> expandContext = Optional.empty();

        private Optional<String> metadataLabel = Optional.empty();

        private Optional<String> rootLabel = Optional.empty();

        private Optional<String> identifierKey = Optional.empty();

        private Optional<String> unknownKey = Optional.empty();

        private Optional<String> implicitKey = Optional.empty();

        private Optional<PartitionStrategy> partitionStrategy = Optional.empty();

        private Builder() {
            datatypes = new LinkedHashMap<>();

            classes = new LinkedHashMap<>();
            embeddedClasses = new LinkedHashMap<>();
            for (Bdio.Class bdioClass : Bdio.Class.values()) {
                if (bdioClass.embedded()) {
                    embeddedClasses.put(bdioClass.name(), bdioClass.toString());
                } else {
                    classes.put(bdioClass.name(), bdioClass.toString());
                }
            }

            dataProperties = new LinkedHashMap<>();
            multiValueProperties = new LinkedHashSet<>();
            for (Bdio.DataProperty bdioDataProperty : Bdio.DataProperty.values()) {
                dataProperties.put(bdioDataProperty.name(), bdioDataProperty.toString());
                if (bdioDataProperty.container() != Container.single) {
                    multiValueProperties.add(bdioDataProperty.name());
                }
            }

            objectProperties = new LinkedHashMap<>();
            for (Bdio.ObjectProperty bdioObjectProperty : Bdio.ObjectProperty.values()) {
                objectProperties.put(bdioObjectProperty.name(), bdioObjectProperty.toString());
            }
        }

        public Builder multiValueCollector(@Nullable Collector<? super Object, ?, ?> multiValueCollector) {
            this.multiValueCollector = Optional.ofNullable(multiValueCollector);
            return this;
        }

        public Builder forContentType(@Nullable Bdio.ContentType contentType, @Nullable Object expandContext) {
            this.contentType = Optional.ofNullable(contentType);
            this.expandContext = Optional.ofNullable(expandContext);
            return this;
        }

        public Builder addDatatype(String iri, DatatypeHandler<?> handler) {
            datatypes.put(Objects.requireNonNull(iri), Objects.requireNonNull(handler));
            return this;
        }

        public Builder addClass(String label, String iri) {
            checkUserSuppliedVertexLabel(label, "class label '%s' is reserved");
            classes.put(Objects.requireNonNull(label), Objects.requireNonNull(iri));
            return this;
        }

        public Builder addEmbeddedClass(String label, String iri) {
            checkUserSuppliedVertexLabel(label, "embedded class label '%s' is reserved");
            embeddedClasses.put(label, iri);
            return this;
        }

        public Builder addDataProperty(String term, String iri) {
            checkUserSuppliedKey(term, "data property '%s' is reserved");
            dataProperties.put(Objects.requireNonNull(term), Objects.requireNonNull(iri));
            return this;
        }

        public Builder addMultiValueDataProperty(String term, String iri) {
            addDataProperty(term, iri);
            multiValueProperties.add(term);
            return this;
        }

        public Builder addObjectProperty(String term, String iri) {
            checkUserSuppliedKey(term, "object property '%s' is reserved");
            objectProperties.put(Objects.requireNonNull(term), Objects.requireNonNull(iri));
            return this;
        }

        public Builder metadataLabel(@Nullable String metadataLabel) {
            this.metadataLabel = checkUserSuppliedVertexLabel(metadataLabel, "metadataLabel '%s' is reserved");
            return this;
        }

        public Builder rootLabel(@Nullable String rootLabel) {
            this.rootLabel = checkUserSuppliedEdgeLabel(rootLabel, "rootProjectKey '%s' is reserved");
            return this;
        }

        public Builder identifierKey(@Nullable String identifierKey) {
            this.identifierKey = checkUserSuppliedKey(identifierKey, "identifierKey '%s' is reserved");
            return this;
        }

        public Builder unknownKey(@Nullable String unknownKey) {
            this.unknownKey = checkUserSuppliedKey(unknownKey, "unknownKey '%s' is reserved");
            return this;
        }

        public Builder implicitKey(@Nullable String implicitKey) {
            this.implicitKey = checkUserSuppliedKey(implicitKey, "implicitKey '%s' is reserved");
            return this;
        }

        public Builder partitionStrategy(@Nullable PartitionStrategy partitionStrategy) {
            if (partitionStrategy != null) {
                checkUserSuppliedKey(partitionStrategy.getPartitionKey(), "partitionKey '%s' is reserved");
                this.partitionStrategy = Optional.of(partitionStrategy);
            } else {
                this.partitionStrategy = Optional.empty();
            }
            return this;
        }

        public Builder partitionStrategy(String partitionKey, String value) {
            return partitionStrategy(PartitionStrategy.build()
                    .partitionKey(partitionKey)
                    .writePartition(value)
                    .readPartitions(value)
                    .create());
        }

        public Builder withConfiguration(Configuration configuration) {
            // TODO You still cannot configure the datatypeHandlers...
            Configuration config = configuration.subset("bdio");
            ConfigurationConverter.getMap(config.subset("embeddedClass"))
                    .forEach((k, v) -> addEmbeddedClass(k.toString(), v.toString()));
            ConfigurationConverter.getMap(config.subset("class"))
                    .forEach((k, v) -> addClass(k.toString(), v.toString()));
            ConfigurationConverter.getMap(config.subset("dataProperties"))
                    .forEach((k, v) -> addDataProperty(k.toString(), v.toString()));
            ConfigurationConverter.getMap(config.subset("objectProperties"))
                    .forEach((k, v) -> addObjectProperty(k.toString(), v.toString()));
            if (config.containsKey("partitionStrategy.partitionKey")) {
                partitionStrategy(PartitionStrategy.create(config.subset("partitionStrategy")));
            }
            return metadataLabel(config.getString("metadataLabel", null))
                    .rootLabel(config.getString("rootLabel", null))
                    .identifierKey(config.getString("identifierKey", null))
                    .unknownKey(config.getString("unknownKey", null))
                    .implicitKey(config.getString("implicitKey", null));
        }

        public GraphMapper create() {
            // We can't make edges from metadata if we aren't recording metadata
            checkState(metadataLabel.isPresent() || !rootLabel.isPresent(), "rootLabel cannot be specified without metadataLabel");

            // NOTE: This is not case-insensitive like the `checkUserSupplied...` methods
            checkState(!metadataLabel.filter(classes::containsKey).isPresent(), "metadataLabel confict");
            checkState(!rootLabel.filter(objectProperties::containsKey).isPresent(), "rootLabel conflict");
            checkState(!identifierKey.filter(dataProperties::containsKey).isPresent(), "identifierKey conflict");
            checkState(!unknownKey.filter(dataProperties::containsKey).isPresent(), "unknownKey conflict");
            checkState(!implicitKey.filter(dataProperties::containsKey).isPresent(), "implicitKey conflict");
            checkState(!partitionStrategy.map(PartitionStrategy::getPartitionKey).filter(dataProperties::containsKey).isPresent(), "partitionKey conflict");

            return new GraphMapper(this);
        }

    }

    /**
     * Checks to ensure the user supplied label does not conflict with any known reserved vertex labels.
     */
    private static Optional<String> checkUserSuppliedVertexLabel(@Nullable String label, String message) {
        if (label != null) {
            // Check all of the BDIO Class names
            for (Bdio.Class bdioClass : Bdio.Class.values()) {
                checkArgument(!label.equalsIgnoreCase(bdioClass.name()), message, label);
            }

            // TODO Warn if label starts with Topology.VERTEX_PREFIX

            return Optional.of(label);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Checks to ensure the user supplied label does not conflict with any known reserved edge labels.
     */
    private static Optional<String> checkUserSuppliedEdgeLabel(@Nullable String label, String message) {
        if (label != null) {
            // Check all of the BDIO Object Property names
            for (Bdio.ObjectProperty bdioOjectProperty : Bdio.ObjectProperty.values()) {
                checkArgument(!label.equalsIgnoreCase(bdioOjectProperty.name()), message, label);
            }

            // TODO Warn if label starts with Topology.EDGE_PREFIX

            return Optional.of(label);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Checks to ensure the user supplied property key does not conflict with an known reserved labels.
     */
    private static Optional<String> checkUserSuppliedKey(@Nullable String key, String message) {
        if (key != null) {
            // TinkerPop reserves hidden properties
            checkArgument(!Graph.Hidden.isHidden(key), message, key);

            // We use keys with ":" in them to identify "unknown" keys coming through JSON-LD framing
            checkArgument(key.indexOf(':') < 0, message, key);

            // Check all of the BDIO Data Property names
            for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
                checkArgument(!key.equalsIgnoreCase(dataProperty.name()), message, key);
            }

            // TODO Does `return checkUserSuppliedEdgeLabel(key, message)` make sense?

            return Optional.of(key);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Removes all of the enumeration names from the supplied map's key set and removes all occurrences the enumeration
     * string representations from the supplied map's value collection.
     */
    private static <E extends Enum<E>> void removeKeysAndValues(Class<E> enumType, Map<String, Object> map) {
        for (E e : enumType.getEnumConstants()) {
            map.remove(e.name());
            map.values().removeIf(Predicate.isEqual(e.toString()));
        }
    }
}
