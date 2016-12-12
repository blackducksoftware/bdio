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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio.Container;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.Lists;

/**
 * Base class used to help model the BDIO JSON-LD classes.
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
        return (String) get(JsonLdConsts.ID);
    }

    /**
     * Appends a new value to a data property, returning the new value or {@code null} if the property was not
     * previously mapped and the supplied value is {@code null}.
     */
    public final Object putData(Bdio.DataProperty key, @Nullable Object value) {
        if (value != null) {
            return merge(key.toString(), checkType(key.type(), value), mergeValue(key.container()));
        } else {
            return compute(key.toString(), computeNullValue(key.container()));
        }
    }

    /**
     * Appends a new identifier for a related object, returning the new value or {@code null} if the property was not
     * previously mapped and the supplied value is {@code null}.
     */
    public final Object putObject(Bdio.ObjectProperty key, @Nullable String value) {
        if (value != null) {
            return merge(key.toString(), value, mergeValue(key.container()));
        } else {
            return compute(key.toString(), computeNullValue(key.container()));
        }
    }

    /**
     * Make sure the incoming value has the appropriate Java type. If minor corrections are necessary, the result may
     * not be the same as supplied value; however the expectation is that {@code value} will pass through this method.
     */
    @Nullable
    private static Object checkType(Bdio.Datatype type, @Nullable Object value) {
        if (value != null) {
            checkArgument(type.javaType().isInstance(value), "was expecting %s, got: %s",
                    type.javaType().getName(), value.getClass().getName());

            // DateTime is a special case because the Instant class isn't handled in JSON-LD's ObjectMapper
            return type != Bdio.Datatype.DateTime ? value : value.toString();
        } else {
            return null;
        }
    }

    /**
     * Function for merging two values.
     */
    private static BiFunction<Object, Object, Object> mergeValue(Bdio.Container container) {
        if (container != Container.single) {
            // If the container type is a list we want to merge the old and new values into a list
            return (oldValue, newValue) -> {
                @SuppressWarnings("unchecked")
                List<Object> values = oldValue instanceof List<?> ? (List<Object>) oldValue : Lists.newArrayList(oldValue);
                if (newValue instanceof List<?>) {
                    values.addAll((List<?>) newValue);
                } else {
                    values.add(newValue);
                }
                return values;
            };
        } else {
            // If the container type is 'single' always just take the new value
            return (oldValue, value) -> value;
        }
    }

    /**
     * Function for computing the replacement for a {@code null} value.
     */
    private static BiFunction<String, Object, Object> computeNullValue(Bdio.Container container) {
        if (container != Container.single) {
            // If the container type is a list we just don't want to add anything to it
            return (key, oldValue) -> oldValue;
        } else {
            // If the container type is 'single' we want to remove the existing mapping
            return (key, oldValue) -> null;
        }
    }

}
