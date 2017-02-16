/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio2.datatype;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.common.base.ExtraCollectors;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableMap;

/**
 * A mapper for converting between JSON-LD value objects and Java objects.
 *
 * @author jgustie
 */
public class ValueObjectMapper {

    // TODO We need a builder to make these constants per instance and configurable/extendable...

    /**
     * Check to see if a Java object can be represented as a JSON primitive.
     */
    private static final Predicate<Object> JSON_PRIMITIVE = x -> x == null
            || x instanceof String
            || x instanceof Number
            || x instanceof Boolean;

    /**
     * Parsers (string to object) for non-identity data types.
     */
    private static final Map<String, Function<String, Object>> PARSERS = ImmutableMap.<String, Function<String, Object>> builder()
            .put(Bdio.Datatype.DateTime.toString(), Instant::parse)
            .put(Bdio.Datatype.Fingerprint.toString(), Fingerprint::valueOf)
            .put(Bdio.Datatype.Long.toString(), Long::valueOf)
            .put(Bdio.Datatype.Products.toString(), Products::valueOf)
            .build();

    /**
     * Type checks for non-default data types.
     */
    private static final Map<String, Predicate<Object>> TYPE_CHECKS = ImmutableMap.<String, Predicate<Object>> builder()
            .put(Bdio.Datatype.DateTime.toString(), Instant.class::isInstance)
            .put(Bdio.Datatype.Fingerprint.toString(), Fingerprint.class::isInstance)
            .put(Bdio.Datatype.Long.toString(), Number.class::isInstance)
            .put(Bdio.Datatype.Products.toString(), Products.class::isInstance)
            .build();

    /**
     * Converters for Java types that cannot be serialized properly by the JSON-LD APIs.
     */
    private static final Map<Class<?>, Function<Object, Object>> CONVERTERS = ImmutableMap.<Class<?>, Function<Object, Object>> builder()
            .put(Instant.class, Objects::toString)
            .put(URI.class, Objects::toString)
            // TODO Do we need to toString other objects as well?
            .build();

    /**
     * Types which can be embedded instead of referenced.
     */
    private static final Set<String> EMBEDDED_TYPES = Stream.of(Bdio.Class.values())
            .filter(Bdio.Class::embed)
            .map(Object::toString)
            .collect(ExtraCollectors.toImmutableSet());

    /**
     * Takes a field value from a JSON-LD node and converts it over to a Java object.
     */
    @Nullable
    public Object fromFieldValue(@Nullable Object input) {
        if (input instanceof List<?> && ((List<?>) input).size() == 1) {
            // Unwrap expansion of single element lists
            // TODO Should this handle multi-valued lists as well?
            return fromFieldValue(((List<?>) input).get(0));
        } else if (mappingOf(input, JsonLdConsts.VALUE).isPresent()) {
            // A map that contains "@value" is value object we can convert to a Java object
            Map<?, ?> valueObject = (Map<?, ?>) input;
            Object type = valueObject.get(JsonLdConsts.TYPE);
            Object value = valueObject.get(JsonLdConsts.VALUE);
            if (TYPE_CHECKS.getOrDefault(type, JSON_PRIMITIVE).test(value)) {
                return value;
            } else if (value instanceof String) {
                return PARSERS.getOrDefault(type, x -> x).apply((String) value);
            } else {
                throw new IllegalArgumentException("unrecognized type: " + value.getClass().getName());
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
        if (JSON_PRIMITIVE.test(value)) {
            return value;
        }
        assert value != null : "null is a primitive";
        for (Map.Entry<String, Predicate<Object>> typeCheck : TYPE_CHECKS.entrySet()) {
            if (typeCheck.getValue().test(value)) {
                // NOTE: We cannot use ImmutableMap for this because the JSON-LD API freaks out
                Map<String, Object> result = new LinkedHashMap<>(2);
                result.put(JsonLdConsts.TYPE, typeCheck.getKey());
                result.put(JsonLdConsts.VALUE, CONVERTERS.getOrDefault(value.getClass(), x -> x).apply(value));
                return result;
            }
        }
        // TODO What about maps representing complex objects?
        throw new IllegalArgumentException("unrecognized type: " + value.getClass().getName());
    }

    /**
     * Creates a JSON-LD value object for a reference to a Java object. Accepts {@link String}, {@link URI} or a
     * {@link Map} that either represents an embedded object or contains an {@value JsonLdConsts#ID} mapping.
     */
    @Nullable
    public Object toReferenceValueObject(@Nullable Object ref) {
        if (ref == null) {
            return null;
        } else if (mappingOf(ref, JsonLdConsts.TYPE).filter(EMBEDDED_TYPES::contains).isPresent()) {
            return ref;
        } else {
            return Optional.of(ref)
                    .flatMap(r -> r instanceof String || r instanceof URI ? Optional.of(r) : mappingOf(r, JsonLdConsts.ID))
                    .map(value -> {
                        Map<String, Object> result = new LinkedHashMap<>(1);
                        result.put(JsonLdConsts.VALUE, value.toString());
                        return result;
                    }).orElseThrow(() -> new IllegalArgumentException("unrecognized reference: " + ref));
        }
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

}
