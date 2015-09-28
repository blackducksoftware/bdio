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
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.Node;
import com.blackducksoftware.bom.Term;
import com.blackducksoftware.bom.Type;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

/**
 * Base class for the models.
 *
 * @author jgustie
 */
public abstract class AbstractModel<M extends AbstractModel<M>> implements Node {

    /**
     * Abstraction over manipulating a bean field from a {@code Map}. Primarily exists so we don't need to use
     * reflection which can be error prone in these mapping/conversion scenarios.
     */
    protected static abstract class ModelField<M extends AbstractModel<M>> {

        /**
         * The term the field maps to.
         */
        private final Term term;

        protected ModelField(Term term) {
            this.term = checkNotNull(term);
        }

        /**
         * Returns the current value of this field from the model.
         * <p>
         * The default implementation returns {@code null}.
         */
        @Nullable
        protected Object get(M model) {
            checkNotNull(model);
            return null;
        }

        /**
         * Sets the new value of this field on the model. May perform validation or conversion as necessary.
         * <p>
         * The default implementation throws an unsupported operation exception.
         */
        protected void set(M model, @Nullable Object value) {
            checkNotNull(model);
            throw new UnsupportedOperationException();
        }

        /**
         * Removes this field from the model.
         * <p>
         * The default implementation sets the value to {@code null}.
         */
        protected void remove(M model) {
            set(checkNotNull(model), null);
        }
    }

    /**
     * A live map backed by the enclosing model.
     */
    private static final class ModelMap<M extends AbstractModel<M>> extends AbstractMap<Term, Object> {
        private final M model;

        private final Map<Term, ModelField<M>> fields;

        private ModelMap(M model, ModelField<M>[] fields) {
            this.model = model;
            this.fields = Maps.uniqueIndex(Arrays.asList(fields), new Function<ModelField<?>, Term>() {
                @Override
                public Term apply(ModelField<?> field) {
                    return field.term;
                }
            });
        }

        @Override
        public Set<Term> keySet() {
            return fields.keySet();
        }

        @Override
        public boolean containsKey(Object key) {
            return fields.containsKey(key);
        }

        @Override
        public int size() {
            return fields.size();
        }

        @Override
        public Set<Entry<Term, Object>> entrySet() {
            // TODO Is this right?
            return Maps.asMap(fields.keySet(), Functions.forMap(this)).entrySet();
        }

        @Override
        public Object get(Object key) {
            ModelField<M> field = fields.get(key);
            return field != null ? field.get(model) : null;
        }

        @Override
        public Object remove(Object key) {
            ModelField<M> field = fields.get(key);
            if (field != null) {
                Object originalValue = field.get(model);
                field.remove(model);
                return originalValue;
            } else {
                return null;
            }
        }

        @Override
        public Object put(Term key, Object value) {
            ModelField<M> field = fields.get(key);
            if (field != null) {
                Object originalValue = field.get(model);
                field.set(model, value);
                return originalValue;
            } else {
                return null;
            }
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

    AbstractModel(Type type, ModelField<M>... fields) {
        types = ImmutableSet.of(type);
        data = Maps.filterValues(new ModelMap<M>((M) this, fields), Predicates.notNull());
    }

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
