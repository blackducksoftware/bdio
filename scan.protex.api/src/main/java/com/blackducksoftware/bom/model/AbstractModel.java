/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bom.model;

import static com.google.common.base.Objects.toStringHelper;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.Node;
import com.blackducksoftware.bom.Term;
import com.blackducksoftware.bom.Type;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

/**
 * Base class for the models.
 *
 * @author jgustie
 */
public abstract class AbstractModel implements Node {

    /**
     * A live map backed by the enclosing model.
     */
    private final class ModelMap extends AbstractMap<Term, Object> {
        private final Set<Term> terms;

        private ModelMap(Term[] terms) {
            this.terms = ImmutableSet.copyOf(terms);
        }

        @Override
        public Set<Term> keySet() {
            return terms;
        }

        @Override
        public boolean containsKey(Object key) {
            return terms.contains(key);
        }

        @Override
        public int size() {
            return terms.size();
        }

        @Override
        public Set<Entry<Term, Object>> entrySet() {
            return Maps.asMap(terms, Functions.forMap(this)).entrySet();
        }

        @Override
        public Object get(Object key) {
            return key instanceof Term ? lookup((Term) key) : null;
        }

        @Override
        public Object remove(Object key) {
            return key instanceof Term ? store((Term) key, null) : null;
        }

        @Override
        public Object put(Term key, Object value) {
            return store(key, value);
        }
    }

    /**
     * The current identifier for this model.
     */
    private String id;

    /**
     * The type of this model. Cannot change, stored as an immutable set to match the expected return type.
     */
    private final Set<Type> types;

    /**
     * A live view of this model that delegates to the {@link #lookup(Term)} and {@link #store(Term, Object)} methods.
     */
    private final Map<Term, Object> data;

    AbstractModel(Type type, Term... terms) {
        types = ImmutableSet.of(type);
        data = Maps.filterValues(new ModelMap(terms), Predicates.notNull());
    }

    /**
     * Returns the value of a term from a model.
     */
    @Nullable
    protected abstract Object lookup(Term term);

    /**
     * Puts the value of of a term to to model, returning the previous value.
     */
    @Nullable
    protected abstract Object store(Term term, @Nullable Object value);

    /**
     * Proper accessor for the identifier.
     */
    public final String getId() {
        return id;
    }

    /**
     * Proper mutator for the identifier.
     */
    public final void setId(String id) {
        this.id = id;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final Set<Type> types() {
        return types;
    }

    // THIS MAP IS NOT NULL-SAFE! No values will come back null, no values can go in null
    @Override
    public final Map<Term, Object> data() {
        return data;
    }

    // Stay consistent with other node implementations

    @Override
    public final int hashCode() {
        return Objects.hash(id, types, data);
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof Node) {
            Node other = (Node) obj;
            return id.equals(other.id()) && types.equals(other.types()) && data.equals(other.data());
        } else {
            return false;
        }
    }

    @Override
    public final String toString() {
        return toStringHelper(this)
                .add("id", id)
                .add("types", types)
                .add("data", data)
                .toString();
    }

    // Helpers to implement the store method

    /**
     * Helper to coerce a value into a string.
     */
    @Nullable
    protected static String valueToString(@Nullable Object value) {
        return value != null ? value.toString() : null;
    }

    /**
     * Helper to coerce a value into a boolean.
     */
    @Nullable
    protected static Boolean valueToBoolean(@Nullable Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value != null) {
            return Boolean.valueOf(valueToString(value));
        } else {
            return null;
        }
    }

    /**
     * Helper to coerce a value into an integer.
     */
    @Nullable
    protected static Integer valueToInteger(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value != null) {
            return Integer.valueOf(valueToString(value));
        } else {
            return null;
        }
    }

    /**
     * Helper to coerce a value into a long.
     */
    @Nullable
    protected static Long valueToLong(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value != null) {
            return Long.valueOf(valueToString(value));
        } else {
            return null;
        }
    }
}
