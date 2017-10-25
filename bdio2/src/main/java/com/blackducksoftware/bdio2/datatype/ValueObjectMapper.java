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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.common.base.ExtraCollectors;
import com.blackducksoftware.common.base.ExtraOptionals;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * A mapper for converting between JSON-LD value objects and Java objects.
 *
 * @author jgustie
 */
public class ValueObjectMapper {

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
     * Function for producing a collector when dealing with multi-valued fields.
     */
    private final IntFunction<Collector<? super Object, ?, ?>> multiValueCollector;

    private ValueObjectMapper(Builder builder) {
        handlers = ImmutableMap.copyOf(builder.handlers);
        embeddedTypes = ImmutableSet.copyOf(builder.embeddedTypes);
        multiValueCollector = Objects.requireNonNull(builder.multiValueCollector);
    }

    /**
     * Takes a field value from a JSON-LD node and converts it over to a Java object.
     */
    @Nullable
    public Object fromFieldValue(@Nullable Object input) {
        if (input instanceof List<?>) {
            // Recursively process list elements
            List<?> valueObjects = (List<?>) input;
            return valueObjects.stream().map(this::fromFieldValue).collect(multiValueCollector.apply(valueObjects.size()));
        } else if (mappingOf(input, JsonLdConsts.VALUE).isPresent()) {
            // A map that contains "@value" is a value object we can convert to a Java object
            Map<?, ?> valueObject = (Map<?, ?>) input;
            String type = (String) valueObject.get(JsonLdConsts.TYPE);
            Object value = valueObject.get(JsonLdConsts.VALUE);
            if (hasCorrectType(type, value)) {
                // Likely a primitive, JSON-LD deserialization doesn't use any Java type information
                return value;
            } else {
                // Needs coercion to the desired Java type
                return handlers.getOrDefault(type, DatatypeSupport.Default()).deserialize(value);
            }
        } else {
            Optional<Object> id = mappingOf(input, JsonLdConsts.ID);
            if (id.isPresent() && ((Map<?, ?>) input).size() == 1) {
                // It was simple reference
                return id.get();
            } else {
                // It was a JSON literal or embedded object
                return input;
            }
        }
    }

    /**
     * Takes a Java object and converts it over to a JSON-LD value object.
     */
    @Nullable
    public Object toValueObject(@Nullable Object value) {
        if (hasCorrectType(null, value)) {
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
            // Find the identifier, otherwise just return the input as a string
            // TODO Clean up in Java 9...
            return ExtraOptionals.stream(ExtraOptionals.or(
                    mappingOf(input, JsonLdConsts.ID),
                    () -> Optional.ofNullable(input)).map(Objects::toString));
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
     * Checks to make sure a value conforms to the expected type.
     */
    private boolean hasCorrectType(@Nullable String type, @Nullable Object value) {
        return handlers.getOrDefault(type, DatatypeSupport.Default()).isInstance(value);
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

        private IntFunction<Collector<? super Object, ?, ?>> multiValueCollector;

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

            // Unwrap single element collections
            multiValueCollector = size -> size == 1 ? Collectors.collectingAndThen(ExtraCollectors.getOnly(), Optional::get) : Collectors.toList();
        }

        public Builder useDatatypeHandler(String type, DatatypeHandler<?> handler) {
            handlers.put(Objects.requireNonNull(type), Objects.requireNonNull(handler));
            return this;
        }

        public Builder addEmbeddedType(String type) {
            embeddedTypes.add(Objects.requireNonNull(type));
            return this;
        }

        public Builder multiValueCollector(IntFunction<Collector<? super Object, ?, ?>> multiValueCollector) {
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
