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
package com.blackducksoftware.bdio2.datatype;

import static com.google.common.base.Preconditions.checkState;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.common.base.ExtraOptionals;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;

/**
 * A mapper for converting between JSON-LD value objects and Java objects.
 *
 * @author jgustie
 */
public class ValueObjectMapper {

    /**
     * The context mapper to use.
     */
    private static final ThreadLocal<ValueObjectMapper> contextMapper = new InheritableThreadLocal<ValueObjectMapper>() {
        @Override
        protected ValueObjectMapper initialValue() {
            return new ValueObjectMapper.Builder().build();
        }
    };

    public static ValueObjectMapper getContextValueObjectMapper() {
        return contextMapper.get();
    }

    public static void setContextValueObjectMapper(ValueObjectMapper mapper) {
        contextMapper.set(mapper);
    }

    /**
     * Handler for an individual datatype.
     */
    public interface DatatypeHandler<T> {
        /**
         * Test to see if a particular value conforms to the datatype.
         * <p>
         * Generally this is just a {@code value instanceof T} check.
         */
        boolean isInstance(@Nullable Object value);

        /**
         * Serialize a conforming object.
         * <p>
         * Generally this is just returns the supplied value, assuming that JSON serialization will handle the value
         * correctly. Since customization of the JSON serialization behavior within the JSON-LD library is not possible,
         * this method provides a hook for changing the serialization behavior.
         */
        @Nullable
        Object serialize(@Nullable Object value);

        /**
         * Deserialize a value into a conforming object.
         * <p>
         * Ensures that arbitrary objects are transformed into conforming objects. Failures due to invalid input should
         * be raised in a consistent fashion.
         */
        @Nullable
        T deserialize(@Nullable Object value);

        /**
         * Allows for construction of a datatype handler from lambda expressions.
         */
        static <T> DatatypeHandler<T> from(Predicate<Object> isInstance, Function<Object, Object> serialize, Function<Object, T> deserialize) {
            return new DatatypeHandler<T>() {
                @Override
                public boolean isInstance(Object value) {
                    return isInstance.test(value);
                }

                @Override
                public Object serialize(Object value) {
                    return serialize.apply(value);
                }

                @Override
                public T deserialize(Object value) {
                    return deserialize.apply(value);
                }
            };
        }
    }

    /**
     * The mapping of fully qualified datatype identifiers to handlers used by this mapper.
     */
    private final ImmutableMap<String, DatatypeHandler<?>> handlers;

    /**
     * The types which should be embedded. Ensures that the full object is accessible instead of just the identifier.
     */
    private final ImmutableSet<String> embeddedTypes;

    /**
     * Function mapping of property keys to the collector used to aggregate the values.
     */
    private final Function<String, Collector<? super Object, ?, ?>> valueCollector;

    private ValueObjectMapper(Builder builder) {
        handlers = ImmutableMap.copyOf(builder.handlers);
        embeddedTypes = ImmutableSet.copyOf(builder.embeddedTypes);

        // Build the value collector with a customized multi-value collector
        Predicate<String> multiValueKeys = ImmutableSet.copyOf(builder.multiValueKeys)::contains;
        Collector<? super Object, ?, ?> multiValueCollector = Objects.requireNonNull(builder.multiValueCollector);
        Collector<? super Object, ?, ?> singleValueCollector = Objects.requireNonNull(builder.singleValueCollector);
        valueCollector = key -> multiValueKeys.test(key) ? multiValueCollector : singleValueCollector;
    }

    /**
     * Takes a field value from a JSON-LD node and converts it over to a Java object.
     */
    @Nullable
    public Object fromFieldValue(String key, @Nullable Object input) {
        return fromFieldValue(input).collect(valueCollector.apply(key));
    }

    private Stream<Object> fromFieldValue(@Nullable Object input) {
        if (input instanceof List<?>) {
            // Recursively process list elements
            return ((List<?>) input).stream().flatMap(x -> fromFieldValue(x));
        } else if (input instanceof Map<?, ?>) {
            Map<?, ?> mapInput = (Map<?, ?>) input;

            // Simple references
            if (mapInput.size() == 1 && mapInput.containsKey(JsonLdConsts.ID)) {
                return Stream.of(mapInput.get(JsonLdConsts.ID));
            }

            // Value objects
            Object value = mapInput.get(JsonLdConsts.VALUE);
            if (value != null) {
                DatatypeHandler<?> handler = handlerForType(mapInput.get(JsonLdConsts.TYPE));
                return Stream.of(handler.isInstance(value) ? value : handler.deserialize(value));
            }
        }

        // It was a JSON literal or embedded object
        return Stream.of(input);
    }

    /**
     * Takes a Java object and converts it over to a JSON-LD value object.
     */
    @Nullable
    public Object toValueObject(@Nullable Object value) {
        if (handlerForType(null).isInstance(value)) {
            return value;
        } else {
            assert value != null : "null is a primitive";
            for (Map.Entry<String, DatatypeHandler<?>> datatype : handlers.entrySet()) {
                if (datatype.getValue().isInstance(value)) {
                    if (datatype.getKey().isEmpty()) {
                        // Special case for overridden 'Default' handlers
                        return datatype.getValue().serialize(value);
                    } else {
                        // Construct a map with an '@value' key
                        return newValueObject(datatype.getKey(), datatype.getValue().serialize(value));
                    }
                }
            }
            // TODO What about maps representing complex objects?
            throw new IllegalArgumentException("unrecognized type: " + value.getClass().getName());
        }
    }

    /**
     * Takes a reference value from a JSON-LD node and converts it over to an identifier.
     */
    public Stream<Object> fromReferenceValueObject(@Nullable Object input) {
        if (input instanceof List<?>) {
            // Recursively process list elements
            return ((List<?>) input).stream().flatMap(this::fromReferenceValueObject);
        } else {
            // Find the identifier or value, otherwise just return the input as a string
            Optional<Object> value = mappingOf(input, JsonLdConsts.ID);
            value = ExtraOptionals.or(value, () -> mappingOf(input, JsonLdConsts.VALUE));
            value = ExtraOptionals.or(value, () -> Optional.ofNullable(input).map(Object::toString));
            return Streams.stream(value);
        }
    }

    /**
     * Creates a JSON-LD value object for a reference to a Java object. Accepts {@link String}, {@link URI} or a
     * {@link Map} that either represents an embedded object or contains an {@value JsonLdConsts#ID} mapping.
     */
    @Nullable
    public Object toReferenceValueObject(@Nullable Object ref) {
        if (ref == null || mappingOf(ref, JsonLdConsts.TYPE).map(String.class::cast).filter(embeddedTypes::contains).isPresent()) {
            return ref;
        } else {
            return Optional.of(ref)
                    .flatMap(r -> r instanceof String || r instanceof URI ? Optional.of(r) : mappingOf(r, JsonLdConsts.ID))
                    .map(value -> newValueObject(null, value.toString()))
                    .orElseThrow(() -> new IllegalArgumentException("unrecognized reference: " + ref));
        }
    }

    /**
     * Returns the datatype handler for the specified type. If the supplied value is a collection, the first type value
     * for which there exists a registered handler will be used. If no handler can be found, the default datatype
     * handler will be returned.
     */
    private DatatypeHandler<?> handlerForType(@Nullable Object type) {
        if (type instanceof String) {
            return handlers.getOrDefault(type, DatatypeSupport.Default());
        } else if (type instanceof Collection<?>) {
            for (Object t : ((Collection<?>) type)) {
                // Note that this will effectively ignore non-string values in the collection
                DatatypeHandler<?> handler = handlers.get(t);
                if (handler != null) {
                    return handler;
                }
            }
        }
        return DatatypeSupport.Default();
    }

    /**
     * Attempts to extract a mapping from an arbitrary object.
     */
    private static Optional<Object> mappingOf(Object obj, Object key) {
        if (obj instanceof Map<?, ?>) {
            return Optional.ofNullable(((Map<?, ?>) obj).get(key));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Constructs a new value object with the supplied type and value.
     */
    private static Map<String, Object> newValueObject(@Nullable String type, Object value) {
        // NOTE: We cannot use ImmutableMap for this because the JSON-LD API freaks out
        Map<String, Object> result = new LinkedHashMap<>(2);
        if (type != null) {
            result.put(JsonLdConsts.TYPE, type);
        }
        result.put(JsonLdConsts.VALUE, Objects.requireNonNull(value));
        return result;
    }

    public static class Builder {

        private final Map<String, DatatypeHandler<?>> handlers = new LinkedHashMap<>();

        private final Set<String> embeddedTypes = new LinkedHashSet<>();

        private final Set<String> multiValueKeys = new LinkedHashSet<>();

        private Collector<? super Object, ?, ?> multiValueCollector;

        private Collector<? super Object, ?, ?> singleValueCollector;

        public Builder() {
            // Add the standard BDIO datatype handlers
            for (Bdio.Datatype datatype : Bdio.Datatype.values()) {
                handlers.put(datatype.toString(), DatatypeSupport.getDatatypeHandler(datatype));
            }

            // These are the types that BDIO expects to be embedded
            for (Bdio.Class bdioClass : Bdio.Class.values()) {
                if (bdioClass.embedded()) {
                    embeddedTypes.add(bdioClass.toString());
                }
            }

            // These are the types that BDIO expects to be multi-valued
            for (Bdio.DataProperty bdioDataProperty : Bdio.DataProperty.values()) {
                if (bdioDataProperty.container() != Bdio.Container.single) {
                    multiValueKeys.add(bdioDataProperty.toString());
                }
            }

            // Configure the collection behavior based on property cardinality
            multiValueCollector = Collectors.toList(); // TODO Should this filter out null values?
            singleValueCollector = Collectors.reducing(null, (a, b) -> a != null ? a : b);
        }

        public Builder useDatatypeHandler(String type, DatatypeHandler<?> handler) {
            handlers.put(Objects.requireNonNull(type), Objects.requireNonNull(handler));
            return this;
        }

        public Builder addEmbeddedType(String type) {
            embeddedTypes.add(Objects.requireNonNull(type));
            return this;
        }

        public Builder addMultiValueKey(String key) {
            multiValueKeys.add(Objects.requireNonNull(key));
            return this;
        }

        public Builder multiValueCollector(Collector<? super Object, ?, ?> multiValueCollector) {
            this.multiValueCollector = Objects.requireNonNull(multiValueCollector);
            return this;
        }

        public ValueObjectMapper build() {
            // Make sure we didn't miss a built-in required type
            checkState(Stream.of(Bdio.Datatype.values()).map(Bdio.Datatype::toString).allMatch(handlers::containsKey),
                    "Value object mapper is missing standard BDIO datatype handler, got {}, expected {}",
                    handlers.keySet(), Arrays.toString(Bdio.Datatype.values()));

            return new ValueObjectMapper(this);
        }
    }

}
