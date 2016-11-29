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

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Objects;

import javax.annotation.Nullable;

import com.github.jsonldjava.core.JsonLdConsts;

/**
 * Base class used to help model the BDIO JSON-LD classes.
 *
 * @author jgustie
 */
public class BdioObject extends LinkedHashMap<String, Object> {

    // TODO Can we play with the property ordering to make these compress better?

    // TODO Technically properties can almost always be lists, put "cardinality" on the properties so we can see how we
    // should behave; also have getAll/getFirst like variants

    protected BdioObject(String id, Bdio.Class bdioClass) {
        put(JsonLdConsts.ID, Objects.requireNonNull(id));
        put(JsonLdConsts.TYPE, bdioClass.toString());
    }

    /**
     * Constructor for types which are embedded and leverage blank node identifiers.
     */
    protected BdioObject(Bdio.Class bdioClass) {
        put(JsonLdConsts.TYPE, bdioClass.toString());
    }

    @Override
    public final Object remove(Object key) {
        // Silently ignore requests to remove the identifier or type
        if (Objects.equals(key, JsonLdConsts.ID) || Objects.equals(key, JsonLdConsts.TYPE)) {
            return get(key);
        } else {
            return super.remove(key);
        }
    }

    @Override
    public final void clear() {
        // Override clear to keep the identifier and type intact
        keySet().retainAll(Arrays.asList(JsonLdConsts.ID, JsonLdConsts.TYPE));
    }

    @Override
    public Object get(Object key) {
        // Really we want the string representations of our enumerations for keys
        if (key instanceof Bdio.DataProperty || key instanceof Bdio.ObjectProperty) {
            return super.get(key.toString());
        } else {
            return super.get(key);
        }
    }

    /**
     * Returns the identifier of this node.
     */
    @Nullable
    public final String id() {
        // We don't guard against `put("@id", null)`
        return (String) get(JsonLdConsts.ID);
    }

    public String getString(Bdio.DataProperty key) {
        // TODO Get the first string?
        return (String) get(key.toString());
    }

    public Long getLong(Bdio.DataProperty key) {
        // TODO Get the first number?
        Number value = (Number) get(key.toString());
        return value != null ? value.longValue() : null;
    }

    public Instant getInstant(Bdio.DataProperty key) {
        String value = getString(key);
        return value != null ? Instant.parse(value) : null;
    }

    public Object getRelated(Bdio.ObjectProperty key) {
        return get(key.toString());
    }

    public Object put(Bdio.DataProperty key, Object value) {
        // TODO Consider container to determine if we merge or overwrite
        return put(key.toString(), value);
    }

    public Object putString(Bdio.DataProperty key, String value) {
        return put(key.toString(), value);
    }

    public Object putLong(Bdio.DataProperty key, Long value) {
        return put(key.toString(), value);
    }

    public Object putInstant(Bdio.DataProperty key, Instant value) {
        return put(key.toString(), value != null ? value.toString() : null);
    }

    public Object putRelated(Bdio.ObjectProperty key, Object value) {
        return put(key.toString(), value);
    }

}
