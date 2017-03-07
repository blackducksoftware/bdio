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
package com.blackducksoftware.bdio2;

import static com.google.common.base.Preconditions.checkState;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio.Container;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.common.base.ExtraStrings;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.Lists;

/**
 * Base class used to help model the BDIO JSON-LD classes. This map does not allow {@code null} keys, attempts to map
 * {@code null} values will result in the removal of the mapping. It is expected that the map represents expanded
 * JSON-LD content, that is keys are string representations of fully qualified IRIs and typed values are represented as
 * value objects (i.e. including their value and type).
 *
 * @author jgustie
 */
public class BdioObject extends AbstractMap<String, Object> {

    private static ValueObjectMapper VALUE_OBJECT_MAPPER = new ValueObjectMapper();

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
        return putJsonLd(key, mapper().toValueObject(value));
    }

    /**
     * Appends a new identifier for a related object, returning the new value or {@code null} if the property was not
     * previously mapped and the supplied value is {@code null}.
     */
    protected final Object putObject(Bdio.ObjectProperty key, @Nullable Object value) {
        return putJsonLd(key, mapper().toReferenceValueObject(value));
    }

    /**
     * Returns the mapper to use for doing JSON-LD value object conversions.
     */
    protected static ValueObjectMapper mapper() {
        return VALUE_OBJECT_MAPPER;
    }

    /**
     * Generic implementation of {@code put} for JSON-LD values.
     */
    private Object putJsonLd(Object key, @Nullable Object valueObject) {
        Bdio.Container container = container(key);
        if (valueObject != null) {
            // Replace the mapping for a single, otherwise combine the values into a list
            return merge(key.toString(), valueObject, container != Container.single ? BdioObject::combine : (k, v) -> v);
        } else {
            // Remove the mapping for a single, otherwise keep the old value (i.e. don't add anything)
            return compute(key.toString(), (k, v) -> container != Container.single ? v : null);
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
     * Merges two values into a single list value.
     */
    @SuppressWarnings("unchecked")
    private static Object combine(Object oldValue, Object value) {
        if (oldValue instanceof List<?>) {
            ((List<Object>) oldValue).add(value);
            return oldValue;
        } else {
            return Lists.newArrayList(oldValue, value);
        }
    }

}
