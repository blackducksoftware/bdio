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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationConverter;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.umlg.sqlg.structure.topology.Topology;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.Bdio.Container;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Default implementation of the tokens interface.
 *
 * @author jgustie
 */
public class BlackDuckIoContext implements BlackDuckIoTokens {

    /**
     * The mapping of vertex labels to {@code @type} IRIs. Does not include the metadata vertex label.
     */
    private final ImmutableMap<String, String> classes;

    /**
     * The vertex labels for classes that should be embedded.
     */
    private final ImmutableSet<String> embeddedClasses;

    /**
     * The mapping of vertex property names or terms to IRIs.
     */
    private final ImmutableMap<String, String> dataProperties;

    /**
     * The property keys for terms that are multi-valued.
     */
    private final ImmutableSet<String> multiDataValueProperties;

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
     * The partitioning key used isolate JSON-LD sub-graphs.
     */
    private final Optional<String> partitionKey;

    private BlackDuckIoContext(Builder builder) {
        classes = ImmutableMap.copyOf(builder.classes);
        embeddedClasses = ImmutableSet.copyOf(builder.embeddedClasses);
        dataProperties = ImmutableMap.copyOf(builder.dataProperties);
        multiDataValueProperties = ImmutableSet.copyOf(builder.multiValueDataProperties);
        objectProperties = ImmutableMap.copyOf(builder.objectProperties);
        metadataLabel = Optional.ofNullable(builder.metadataLabel);
        rootLabel = Optional.ofNullable(builder.rootLabel);
        identifierKey = Optional.ofNullable(builder.identifierKey);
        unknownKey = Optional.ofNullable(builder.unknownKey);
        implicitKey = Optional.ofNullable(builder.implicitKey);
        partitionKey = Optional.ofNullable(builder.partitionKey);

        // We can't make edges from metadata if we aren't recording metadata
        checkState(metadataLabel.isPresent() || !rootLabel.isPresent(), "rootLabel cannot be specified without metadataLabel");

        // NOTE: This is not case-insensitive like the `checkUserSupplied...` methods
        checkState(!metadataLabel.filter(classes::containsKey).isPresent(), "metadataLabel confict");
        checkState(!rootLabel.filter(objectProperties::containsKey).isPresent(), "rootLabel conflict");
        checkState(!identifierKey.filter(dataProperties::containsKey).isPresent(), "identifierKey conflict");
        checkState(!unknownKey.filter(dataProperties::containsKey).isPresent(), "unknownKey conflict");
        checkState(!implicitKey.filter(dataProperties::containsKey).isPresent(), "implicitKey conflict");
        checkState(!partitionKey.filter(dataProperties::containsKey).isPresent(), "partitionKey conflict");
    }

    @Override
    public Map<String, String> classes() {
        return classes;
    }

    @Override
    public Set<String> embeddedClasses() {
        return embeddedClasses;
    }

    @Override
    public Map<String, String> dataProperties() {
        return dataProperties;
    }

    @Override
    public Set<String> multiDataValueProperties() {
        return multiDataValueProperties;
    }

    @Override
    public Map<String, String> objectProperties() {
        return objectProperties;
    }

    @Override
    public String metadataLabel() {
        return metadataLabel.orElse(null);
    }

    @Override
    public String rootLabel() {
        return rootLabel.orElse(null);
    }

    @Override
    public String identifierKey() {
        return identifierKey.orElse(null);
    }

    @Override
    public String unknownKey() {
        return unknownKey.orElse(null);
    }

    @Override
    public String implicitKey() {
        return implicitKey.orElse(null);
    }

    @Override
    public String partitionKey() {
        return partitionKey.orElse(null);
    }

    /**
     * Checks to see if the specified key represents a special property internal to the graph.
     */
    boolean isSpecialKey(String key) {
        // TODO All keys that start with "_"?
        Predicate<String> isKey = Predicate.isEqual(key);
        return identifierKey.filter(isKey).isPresent()
                || implicitKey.filter(isKey).isPresent()
                || partitionKey.filter(isKey).isPresent();
    }

    /**
     * Check to see if the specified key represents an unknown property.
     */
    boolean isUnknownKey(String key) {
        // If framing did not recognize the attribute, it will still have a scheme or prefix separator
        // This implementation of the check is probably a lot easier then looking at all the possible keys
        return key.indexOf(':') >= 0;
    }

    /**
     * Iterates over the known embedded type labels.
     */
    void forEachEmbeddedType(BiConsumer<String, String> embeddedTypeConsumer) {
        classes.entrySet().stream()
                .filter(e -> embeddedClasses.contains(e.getKey()))
                .forEach(e -> embeddedTypeConsumer.accept(e.getKey(), e.getValue()));
    }

    /**
     * Returns the labels which should be excluded from writing.
     */
    Set<String> excludedLabels() {
        // TODO Allow other labels to be added?
        // TODO What about rules like "exclude labels that start with _"?
        Set<String> excludedLabels = new LinkedHashSet<>();
        metadataLabel.ifPresent(excludedLabels::add);
        excludedLabels.addAll(embeddedClasses);
        return excludedLabels;
    }

    /**
     * Returns the JSON-LD {@code @context} value.
     */
    Map<String, Object> serialize() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.putAll(classes);
        context.putAll(dataProperties);
        context.putAll(objectProperties);
        return context;
    }

    /**
     * Returns the JSON-LD frame value.
     */
    Map<String, Object> frame() {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put(JsonLdConsts.CONTEXT, serialize());
        frame.put(JsonLdConsts.TYPE, new ArrayList<>(classes.values()));
        return frame;
    }

    /**
     * Returns the application context: i.e. a context that does not describe BDIO.
     */
    @Nullable
    Map<String, Object> applicationContext() {
        Map<String, Object> context = serialize();

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
     * Removes all of the enumeration names from the supplied map's key set and removes all occurrences the enumeration
     * string representations from the supplied map's value collection.
     */
    private static <E extends Enum<E>> void removeKeysAndValues(Class<E> enumType, Map<String, Object> map) {
        for (E e : enumType.getEnumConstants()) {
            map.remove(e.name());
            map.values().removeIf(Predicate.isEqual(e.toString()));
        }
    }

    public static Builder build() {
        return new Builder();
    }

    public static BlackDuckIoContext create(Configuration config) {
        BlackDuckIoContext.Builder builder = BlackDuckIoContext.build();
        ConfigurationConverter.getMap(config.subset("embeddedClass")).forEach((k, v) -> builder.addEmbeddedClass(k.toString(), v.toString()));
        ConfigurationConverter.getMap(config.subset("class")).forEach((k, v) -> builder.addClass(k.toString(), v.toString()));
        ConfigurationConverter.getMap(config.subset("dataProperties")).forEach((k, v) -> builder.addDataProperty(k.toString(), v.toString()));
        ConfigurationConverter.getMap(config.subset("objectProperties")).forEach((k, v) -> builder.addObjectProperty(k.toString(), v.toString()));
        builder.metadataLabel(config.getString("metadataLabel", null));
        builder.rootLabel(config.getString("rootLabel", null));
        builder.identifierKey(config.getString("identifierKey", null));
        builder.unknownKey(config.getString("unknownKey", null));
        builder.implicitKey(config.getString("implicitKey", null));
        builder.partitionKey(config.getString("partitionStrategy.partitionKey", null));
        return builder.create();
    }

    public static BlackDuckIoContext create(BlackDuckIoTokens tokens) {
        if (tokens instanceof BlackDuckIoContext) {
            return (BlackDuckIoContext) tokens;
        }
        BlackDuckIoContext.Builder builder = BlackDuckIoContext.build();
        tokens.classes().forEach(builder::addClass);
        tokens.embeddedClasses().forEach(label -> builder.addEmbeddedClass(label, tokens.classes().get(label)));
        tokens.dataProperties().forEach(builder::addDataProperty);
        tokens.multiDataValueProperties().forEach(term -> builder.addMultiValueDataProperty(term, tokens.dataProperties().get(term)));
        tokens.objectProperties().forEach(builder::addObjectProperty);
        builder.metadataLabel(tokens.metadataLabel());
        builder.rootLabel(tokens.rootLabel());
        builder.identifierKey(tokens.identifierKey());
        builder.unknownKey(tokens.unknownKey());
        builder.implicitKey(tokens.implicitKey());
        builder.partitionKey(tokens.partitionKey());
        return builder.create();
    }

    public static final class Builder {

        private final Map<String, String> classes;

        private final Set<String> embeddedClasses;

        private final Map<String, String> dataProperties;

        private final Set<String> multiValueDataProperties;

        private final Map<String, String> objectProperties;

        @Nullable
        private String metadataLabel;

        @Nullable
        private String rootLabel;

        @Nullable
        private String identifierKey;

        @Nullable
        private String unknownKey;

        @Nullable
        private String implicitKey;

        @Nullable
        private String partitionKey;

        private Builder() {
            classes = new LinkedHashMap<>();
            embeddedClasses = new LinkedHashSet<>();
            for (Bdio.Class bdioClass : Bdio.Class.values()) {
                classes.put(bdioClass.name(), bdioClass.toString());
                if (bdioClass.embedded()) {
                    embeddedClasses.add(bdioClass.name());
                }
            }

            dataProperties = new LinkedHashMap<>();
            multiValueDataProperties = new LinkedHashSet<>();
            for (Bdio.DataProperty bdioDataProperty : Bdio.DataProperty.values()) {
                dataProperties.put(bdioDataProperty.name(), bdioDataProperty.toString());
                if (bdioDataProperty.container() != Container.single) {
                    multiValueDataProperties.add(bdioDataProperty.name());
                }
            }

            objectProperties = new LinkedHashMap<>();
            for (Bdio.ObjectProperty bdioObjectProperty : Bdio.ObjectProperty.values()) {
                objectProperties.put(bdioObjectProperty.name(), bdioObjectProperty.toString());
            }
        }

        public Builder addClass(String label, String iri) {
            checkUserSuppliedVertexLabel(label, "class label '%s' is reserved");
            classes.put(Objects.requireNonNull(label), Objects.requireNonNull(iri));
            return this;
        }

        public Builder addEmbeddedClass(String label, String iri) {
            addClass(label, iri);
            embeddedClasses.add(label);
            return this;
        }

        public Builder addDataProperty(String term, String iri) {
            checkUserSuppliedKey(term, "data property '%s' is reserved");
            dataProperties.put(Objects.requireNonNull(term), Objects.requireNonNull(iri));
            return this;
        }

        public Builder addMultiValueDataProperty(String term, String iri) {
            addDataProperty(term, iri);
            multiValueDataProperties.add(term);
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

        public Builder partitionKey(@Nullable String partitionKey) {
            this.partitionKey = checkUserSuppliedKey(partitionKey, "partitionKey '%s' is reserved");
            return this;
        }

        public BlackDuckIoContext create() {
            return new BlackDuckIoContext(this);
        }

        /**
         * Checks to ensure the user supplied label does not conflict with any known reserved vertex labels.
         */
        @Nullable
        private static String checkUserSuppliedVertexLabel(@Nullable String label, String message) {
            if (label != null) {
                // Sqlg reserves vertex tables
                checkArgument(!label.startsWith(Topology.VERTEX_PREFIX), message, label);

                // Check all of the BDIO Class names
                for (Bdio.Class bdioClass : Bdio.Class.values()) {
                    checkArgument(!label.equalsIgnoreCase(bdioClass.name()), message, label);
                }
            }
            return label;
        }

        /**
         * Checks to ensure the user supplied label does not conflict with any known reserved edge labels.
         */
        @Nullable
        private static String checkUserSuppliedEdgeLabel(@Nullable String label, String message) {
            if (label != null) {
                // Sqlg reserves edge tables
                checkArgument(!label.startsWith(Topology.EDGE_PREFIX), message, label);

                // Check all of the BDIO Object Property names
                for (Bdio.ObjectProperty bdioOjectProperty : Bdio.ObjectProperty.values()) {
                    checkArgument(!label.equalsIgnoreCase(bdioOjectProperty.name()), message, label);
                }
            }
            return label;
        }

        /**
         * Checks to ensure the user supplied property key does not conflict with an known reserved labels.
         */
        @Nullable
        private static String checkUserSuppliedKey(@Nullable String key, String message) {
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
            }
            return key;
        }
    }

}
