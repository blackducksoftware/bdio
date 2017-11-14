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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationConverter;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.umlg.sqlg.structure.Topology;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.Bdio.Container;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * The topology defines common properties used by BDIO.
 *
 * @author jgustie
 */
public class GraphTopology {

    /**
     * The list of initializers.
     */
    private final ImmutableList<Consumer<GraphTopology>> initializers;

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

    private GraphTopology(Builder builder) {
        initializers = ImmutableList.copyOf(builder.initializers);
        classes = ImmutableMap.copyOf(builder.classes);
        embeddedClasses = ImmutableSet.copyOf(builder.embeddedClasses);
        dataProperties = ImmutableMap.copyOf(builder.dataProperties);
        multiDataValueProperties = ImmutableSet.copyOf(builder.multiValueDataProperties);
        objectProperties = ImmutableMap.copyOf(builder.objectProperties);
        metadataLabel = Objects.requireNonNull(builder.metadataLabel);
        rootLabel = Objects.requireNonNull(builder.rootLabel);
        identifierKey = Objects.requireNonNull(builder.identifierKey);
        unknownKey = Objects.requireNonNull(builder.unknownKey);
        implicitKey = Objects.requireNonNull(builder.implicitKey);
        partitionStrategy = Objects.requireNonNull(builder.partitionStrategy);

        // We can't make edges from metadata if we aren't recording metadata
        checkState(metadataLabel.isPresent() || !rootLabel.isPresent(), "rootLabel cannot be specified without metadataLabel");

        // NOTE: This is not case-insensitive like the `checkUserSupplied...` methods
        checkState(!metadataLabel.filter(classes::containsKey).isPresent(), "metadataLabel confict");
        checkState(!rootLabel.filter(objectProperties::containsKey).isPresent(), "rootLabel conflict");
        checkState(!identifierKey.filter(dataProperties::containsKey).isPresent(), "identifierKey conflict");
        checkState(!unknownKey.filter(dataProperties::containsKey).isPresent(), "unknownKey conflict");
        checkState(!implicitKey.filter(dataProperties::containsKey).isPresent(), "implicitKey conflict");
        checkState(!partitionStrategy.map(PartitionStrategy::getPartitionKey).filter(dataProperties::containsKey).isPresent(), "partitionKey conflict");
    }

    /**
     * Initializes the supplied graph for this topology. Initialization is generally performed before any long running
     * operation that writes to the database.
     */
    public void initialize() {
        // TODO Should we do this once over the life of the topology?
        // In general, this seems to take a few hundred milliseconds. If that number grows or if that is too slow, we
        // can optimize this, but it will require help during configuration to ensure only a single topology instance is
        // created (we could put a log message here with the timing to make it clear how often we are initializing)
        initializers.forEach(c -> c.accept(this));
    }

    /**
     * Iterates over the known vertex labels.
     */
    public void forEachTypeLabel(Consumer<String> typeLabelConsumer) {
        classes.keySet().forEach(typeLabelConsumer);
    }

    /**
     * Iterates over the known embedded type labels.
     */
    public void forEachEmbeddedType(BiConsumer<String, String> embeddedTypeConsumer) {
        classes.entrySet().stream()
                .filter(e -> isEmbeddedLabel(e.getKey()))
                .forEach(e -> embeddedTypeConsumer.accept(e.getKey(), e.getValue()));
    }

    /**
     * Iterates over the known multi-valued data property keys.
     */
    public void forEachMultiValueDataPropertyKey(Consumer<String> propertyKeyConsumer) {
        multiDataValueProperties.forEach(propertyKeyConsumer);
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
        return embeddedClasses.contains(label);
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

    /**
     * Returns the JSON-LD frame {@code @type} value.
     */
    public List<String> type() {
        List<String> type = new ArrayList<>();
        type.addAll(classes.values());
        return type;
    }

    /**
     * Returns the JSON-LD {@code @context} value.
     */
    public Map<String, Object> context() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.putAll(classes);
        context.putAll(dataProperties);
        context.putAll(objectProperties);
        return context;
    }

    /**
     * Returns the application context: i.e. a context that does not describe BDIO.
     */
    @Nullable
    public Map<String, Object> applicationContext() {
        Map<String, Object> context = context();

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

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {

        private final List<Consumer<GraphTopology>> initializers;

        private final Map<String, String> classes;

        private final Set<String> embeddedClasses;

        private final Map<String, String> dataProperties;

        private final Set<String> multiValueDataProperties;

        private final Map<String, String> objectProperties;

        private Optional<String> metadataLabel = Optional.empty();

        private Optional<String> rootLabel = Optional.empty();

        private Optional<String> identifierKey = Optional.empty();

        private Optional<String> unknownKey = Optional.empty();

        private Optional<String> implicitKey = Optional.empty();

        private Optional<PartitionStrategy> partitionStrategy = Optional.empty();

        private Builder() {
            initializers = new ArrayList<>();

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

        public Builder addInitializer(Consumer<GraphTopology> initializer) {
            initializers.add(Objects.requireNonNull(initializer));
            return this;
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

        public GraphTopology create() {
            return new GraphTopology(this);
        }

    }

    /**
     * Checks to ensure the user supplied label does not conflict with any known reserved vertex labels.
     */
    private static Optional<String> checkUserSuppliedVertexLabel(@Nullable String label, String message) {
        if (label != null) {
            // Sqlg reserves vertex tables
            checkArgument(!label.startsWith(Topology.VERTEX_PREFIX), message, label);

            // Check all of the BDIO Class names
            for (Bdio.Class bdioClass : Bdio.Class.values()) {
                checkArgument(!label.equalsIgnoreCase(bdioClass.name()), message, label);
            }

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
            // Sqlg reserves edge tables
            checkArgument(!label.startsWith(Topology.EDGE_PREFIX), message, label);

            // Check all of the BDIO Object Property names
            for (Bdio.ObjectProperty bdioOjectProperty : Bdio.ObjectProperty.values()) {
                checkArgument(!label.equalsIgnoreCase(bdioOjectProperty.name()), message, label);
            }

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
