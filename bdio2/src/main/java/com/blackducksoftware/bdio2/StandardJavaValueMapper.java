/*
 * Copyright 2018 Synopsys, Inc.
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

import static java.util.Collections.emptyList;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.blackducksoftware.common.value.ContentRange;
import com.blackducksoftware.common.value.ContentType;
import com.blackducksoftware.common.value.Digest;
import com.blackducksoftware.common.value.ProductList;
import com.github.jsonldjava.core.JsonLdConsts;

/**
 * A value mapper for the standard BDIO datatypes using their preferred Java types.
 *
 * @author jgustie
 */
public class StandardJavaValueMapper implements BdioValueMapper {

    private static final StandardJavaValueMapper INSTANCE = new StandardJavaValueMapper();

    public static StandardJavaValueMapper getInstance() {
        return INSTANCE;
    }

    protected StandardJavaValueMapper() {
    }

    @Override
    public Object fromFieldValue(Map<?, ?> fieldValue) {
        // Object property (JsonLdUtils.isNodeReference)
        Object id = fieldValue.get(JsonLdConsts.ID);
        if (id != null && fieldValue.size() == 1) {
            return id;
        }

        // Data property
        Object value = fieldValue.get(JsonLdConsts.VALUE);
        if (value != null) {
            Object type = fieldValue.get(JsonLdConsts.TYPE);
            if (Objects.equals(type, JsonLdConsts.ID)) {
                return value;
            } else {
                return fromValue(determineStandardType(type), value);
            }
        }

        // Embedded object
        return fieldValue;
    }

    @Override
    public Object toFieldValue(@Nullable Object definedType, Object input, Predicate<Object> isEmbeddedType) {
        if (input instanceof Map<?, ?>) {
            Object type = ((Map<?, ?>) input).get(JsonLdConsts.TYPE);
            Object id = ((Map<?, ?>) input).get(JsonLdConsts.ID);
            if (type != null && isEmbeddedType.test(type)) {
                // Embedded object property
                return input;
            } else if (id != null) {
                // Reference object property
                Map<String, Object> valueMap = new LinkedHashMap<>(3);
                if (Objects.equals(definedType, JsonLdConsts.ID)) {
                    valueMap.put(JsonLdConsts.ID, id);
                } else {
                    // TODO Is it ever the case where we want this?
                    valueMap.put(JsonLdConsts.VALUE, id);
                }
                return valueMap;
            } else {
                throw new IllegalArgumentException("unrecognized input (" + input.getClass().getName() + "): " + input);
            }
        } else {
            Object value = toValue(determineStandardType(definedType), input);
            if (definedType != null && !definedType.equals("") && !definedType.equals(emptyList())) {
                // Typed value
                Map<String, Object> valueMap = new LinkedHashMap<>(3);
                valueMap.put(JsonLdConsts.TYPE, definedType);
                valueMap.put(JsonLdConsts.VALUE, value);
                return valueMap;
            } else {
                // Value
                return value;
            }
        }
    }

    @Override
    public Collector<? super Object, ?, ?> getCollector(String container) {
        switch (determineStandardContainer(container)) {
        case single:
            return Collectors.reducing(null, (a, b) -> a != null ? a : b);
        case ordered:
            return Collectors.toList(); // TODO Should this filter out null values?
        case unordered:
            // Use `List` to be consistent with the JSON-LD API
            return Collectors.toList(); // TODO Should this filter out null values?
        default:
            throw new IllegalArgumentException("unknown container: " + container);
        }
    }

    @Override
    public Stream<?> split(Object value) {
        if (value instanceof Collection<?>) {
            return ((Collection<?>) value).stream();
        } else {
            return Stream.of(value);
        }
    }

    /**
     * Given a JSON-LD type, returns the standard BDIO datatype.
     */
    protected Bdio.Datatype determineStandardType(@Nullable Object type) {
        if (type == null || type.equals("") || type.equals(emptyList())) {
            return Bdio.Datatype.Default;
        } else if (type instanceof List<?>) {
            // If there are multiple types, just take the first one we recognize
            for (Object t : ((List<?>) type)) {
                try {
                    return determineStandardType(t);
                } catch (IllegalArgumentException e) {
                    continue;
                }
            }
        } else if (type instanceof String) {
            for (Bdio.Datatype datatype : Bdio.Datatype.values()) {
                if (datatype.toString().equals(type)) {
                    return datatype;
                }
            }
        }
        throw new IllegalArgumentException("unknown datatype: " + type);
    }

    /**
     * Given a JSON-LD container, returns the standard BDIO container.
     */
    protected Bdio.Container determineStandardContainer(@Nullable String container) {
        if (container == null || container.isEmpty() || container.equals(JsonLdConsts.NONE) || container.equals(JsonLdConsts.ID)) {
            return Bdio.Container.single;
        } else if (container.equals(JsonLdConsts.LIST)) {
            return Bdio.Container.ordered;
        } else if (container.equals(JsonLdConsts.SET)) {
            return Bdio.Container.unordered;
        } else {
            throw new IllegalArgumentException("unknown continer: " + container);
        }
    }

    /**
     * Normalizes or parses values to a specific standard BDIO datatype.
     */
    protected Object fromValue(Bdio.Datatype datatype, Object value) {
        switch (datatype) {
        case ContentRange:
            return ContentRange.from(value);
        case ContentType:
            return ContentType.from(value);
        case DateTime:
            return zonedDateTimeFrom(value);
        case Default:
            return defaultFrom(value);
        case Digest:
            return Digest.from(value);
        case Long:
            return longFrom(value);
        case Products:
            return ProductList.from(value);
        default:
            throw new IllegalArgumentException("unknown datatype: " + datatype);
        }
    }

    protected Object toValue(Bdio.Datatype datatype, Object value) {
        // TODO Should we look for datatype == Long to force numeric value?
        // TODO If we get a ZonedDateTime should we format it as just an offset? Convert to java.util.Date?
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        } else {
            return value.toString();
        }
    }

    /**
     * Datatype specific handling for the {@link Bdio.Datatype#Default} type.
     */
    private static Object defaultFrom(Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        } else {
            throw new IllegalArgumentException("unexpected input: " + value);
        }
    }

    /**
     * Datatype specific handling for the {@link Bdio.Datatype#DateTime} type.
     */
    private static ZonedDateTime zonedDateTimeFrom(Object value) {
        if (value instanceof ZonedDateTime) {
            return (ZonedDateTime) value;
        } else if (value instanceof Temporal) {
            return ZonedDateTime.from(((Temporal) value));
        } else if (value instanceof Date) {
            return ZonedDateTime.ofInstant(((Date) value).toInstant(), ZoneOffset.UTC);
        } else if (value instanceof CharSequence) {
            return ZonedDateTime.parse(((CharSequence) value).toString());
        } else {
            throw new IllegalArgumentException("unexpected input: " + value);
        }
    }

    /**
     * Datatype specific handling for the {@link Bdio.Datatype#Long} type.
     */
    private static Long longFrom(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        } else if (value instanceof CharSequence) {
            return Long.valueOf(((CharSequence) value).toString());
        } else {
            throw new IllegalArgumentException("unexpected input: " + value);
        }
    }

}
