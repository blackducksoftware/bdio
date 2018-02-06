/*
 * Copyright 2016 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2;

import static com.google.common.base.Preconditions.checkState;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.common.base.ExtraStrings;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.Streams;

/**
 * Base class used to help model the BDIO JSON-LD classes. This map does not allow {@code null} keys, attempts to map
 * {@code null} values will result in the removal of the mapping. It is expected that the map represents expanded
 * JSON-LD content, that is keys are string representations of fully qualified IRIs and typed values are represented as
 * value objects (i.e. including their value and type).
 *
 * @author jgustie
 */
public class BdioObject extends AbstractMap<String, Object> {

    /**
     * The backing node data for this BDIO object.
     * <p>
     * Especially with the explosion in size of the {@code Map} API in Java 8, it does not make sense to directly
     * extends a specific map implementation and attempt to enforce constraints on it: too much code needs special
     * consideration and it is prone to changing more frequently because of default method implementations. If the
     * default {@code AbstractMap} implementations prove to be too slow we can provide optimized implementations calling
     * through to the value of this field as needed.
     */
    private final Map<String, Object> data;

    /**
     * Constructor for types with an identifier.
     */
    protected BdioObject(String id, Bdio.Class bdioClass) {
        data = new LinkedHashMap<>();
        put(JsonLdConsts.ID, Objects.requireNonNull(id));
        put(JsonLdConsts.TYPE, bdioClass.toString());
    }

    /**
     * Constructor for types which are embedded and leverage blank node identifiers.
     */
    protected BdioObject(Bdio.Class bdioClass) {
        data = new LinkedHashMap<>();
        put(JsonLdConsts.TYPE, bdioClass.toString());
    }

    /**
     * Specialty copy constructor for internal use.
     */
    BdioObject(Map<String, Object> initialValues) {
        data = new LinkedHashMap<>(initialValues);
    }

    /**
     * Returns a new random identifier.
     */
    public static String randomId() {
        return "urn:uuid:" + UUID.randomUUID();
    }

    @Override
    public final Set<Entry<String, Object>> entrySet() {
        return data.entrySet();
    }

    @Override
    public final Object put(String key, @Nullable Object value) {
        Objects.requireNonNull(key, "key must not be null");
        return value != null ? data.put(key, value) : data.remove(key);
    }

    @Override
    public final Object get(@Nullable Object key) {
        return data.get(key);
    }

    /**
     * Returns the identifier for this object.
     */
    @Nullable
    public final String id() {
        Object value = get(JsonLdConsts.ID);
        checkState(value == null || value instanceof String, "identifier is not mapped to a string");
        return ExtraStrings.beforeLast((String) value, '#');
    }

    /**
     * Appends a new value to a data property, returning the new value or {@code null} if the property was not
     * previously mapped and the supplied value is {@code null}.
     */
    protected final Object putData(Bdio.DataProperty key, @Nullable Object value) {
        return putJsonLd(key, value, mapper()::toValueObject);
    }

    /**
     * Appends a new identifier for a related object, returning the new value or {@code null} if the property was not
     * previously mapped and the supplied value is {@code null}.
     */
    protected final Object putObject(Bdio.ObjectProperty key, @Nullable Object value) {
        return putJsonLd(key, value, mapper()::toReferenceValueObject);
    }

    /**
     * Returns the mapper to use for doing JSON-LD value object conversions.
     */
    protected ValueObjectMapper mapper() {
        return ValueObjectMapper.getContextValueObjectMapper();
    }

    /**
     * Generic implementation of {@code put} for JSON-LD values.
     */
    private Object putJsonLd(Object key, @Nullable Object value, Function<Object, Object> toValueObject) {
        Bdio.Container container = container(key);
        if (container == Bdio.Container.single) {
            // Use `compute` instead of `put` because `put` returns the previous value
            return compute(key.toString(), (k, v) -> toValueObject.apply(value));
        } else if (value != null) {
            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) computeIfAbsent(key.toString(), k -> new ArrayList<>());
            Stream<?> values = streamValue(value).map(toValueObject);
            if (container == Bdio.Container.ordered) {
                values.forEachOrdered(result::add);
            } else {
                values.forEach(result::add);
            }
            return result;
        } else {
            // Current value is unmodified
            return get(key.toString());
        }
    }

    /**
     * Returns the container associated with the specified map key. A container of {@link Bdio.Container#single} behaves
     * like a normal map, all other containers behave like a list multimap.
     */
    private static Bdio.Container container(Object key) {
        if (key instanceof Bdio.DataProperty) {
            return ((Bdio.DataProperty) key).container();
        } else if (key instanceof Bdio.ObjectProperty) {
            return ((Bdio.ObjectProperty) key).container();
        } else {
            return Bdio.Container.single;
        }
    }

    /**
     * Converts an arbitrary value into a stream of one or more values.
     */
    private Stream<?> streamValue(Object value) {
        Objects.requireNonNull(value);
        if (value instanceof Iterable<?>) {
            return Streams.stream((Iterable<?>) value);
        } else if (value.getClass().isArray()) {
            return Stream.of((Object[]) value);
        } else {
            return Stream.of(value);
        }
    }

}
