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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.blackducksoftware.common.base.ExtraStrings;
import com.blackducksoftware.common.base.ExtraUUIDs;
import com.github.jsonldjava.core.JsonLdConsts;

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
    // TODO Deprecate this
    public static String randomId() {
        return ExtraUUIDs.toUriString(UUID.randomUUID());
    }

    @Override
    public final Set<Entry<String, Object>> entrySet() {
        return data.entrySet();
    }

    @Override
    public final Object get(@Nullable Object key) {
        return data.get(key);
    }

    @Override
    public final Object put(String key, @Nullable Object value) {
        Objects.requireNonNull(key, "key must not be null");
        return value != null ? data.put(key, value) : data.remove(key);
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

    protected Object putFieldValue(Object field, @Nullable Object value) {
        return BdioContext.getActive().putFieldValue(this, field, value);
    }

}
