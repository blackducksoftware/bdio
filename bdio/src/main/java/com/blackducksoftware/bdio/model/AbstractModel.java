/*
 * Copyright 2015 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio.model;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blackducksoftware.bdio.Node;
import com.blackducksoftware.bdio.Term;
import com.blackducksoftware.bdio.Type;
import com.blackducksoftware.bdio.io.LinkedDataContext;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Base class for the models.
 *
 * @author jgustie
 */
public abstract class AbstractModel<M extends AbstractModel<M>> implements Node {
    /**
     * A function that converts to a specific model implementation.
     */
    private static final class ToModelFunction<M extends AbstractModel<? super M>> implements Function<Node, Iterable<M>> {
        private final Logger logger = LoggerFactory.getLogger(getClass());

        private final Class<M> modelType;

        private ToModelFunction(Class<M> modelType) {
            this.modelType = checkNotNull(modelType);
        }

        @Override
        public Iterable<M> apply(@Nullable Node node) {
            if (node != null) {
                try {
                    M model = modelType.getConstructor().newInstance();
                    if (node.types().containsAll(model.types())) {
                        model.setId(node.id());
                        model.data().putAll(node.data());
                        return ImmutableSet.of(model);
                    }
                } catch (ReflectiveOperationException e) {
                    logger.debug("Failed to create instance of {}", modelType.getName(), e);
                }
            }
            return ImmutableSet.of();
        }
    }

    /**
     * Abstraction over manipulating a bean field from a {@code Map}. Primarily exists so we don't need to use
     * reflection which can be error prone in these mapping/conversion scenarios.
     */
    protected abstract static class ModelField<M extends AbstractModel<M>, T> {
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
        protected T get(M model) {
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

        private final Map<Term, ModelField<M, ?>> fields;

        private final Map<Term, Object> extraData = new LinkedHashMap<>();

        private ModelMap(M model, Iterable<ModelField<M, ?>> fields) {
            this.model = model;
            this.fields = Maps.uniqueIndex(fields, new Function<ModelField<?, ?>, Term>() {
                @Override
                public Term apply(ModelField<?, ?> field) {
                    return field.term;
                }
            });
        }

        @Override
        public Set<Term> keySet() {
            return Sets.union(extraData.keySet(), fields.keySet());
        }

        @Override
        public boolean containsKey(Object key) {
            return fields.containsKey(key) || extraData.containsKey(key);
        }

        @Override
        public int size() {
            return fields.size() + extraData.size();
        }

        @Override
        public Set<Entry<Term, Object>> entrySet() {
            return Maps.asMap(keySet(), Functions.forMap(this)).entrySet();
        }

        @Override
        public Object get(Object key) {
            ModelField<M, ?> field = fields.get(key);
            return field != null ? field.get(model) : extraData.get(key);
        }

        @Override
        public Object remove(Object key) {
            ModelField<M, ?> field = fields.get(key);
            if (field != null) {
                Object originalValue = field.get(model);
                field.remove(model);
                return originalValue;
            } else {
                return extraData.remove(key);
            }
        }

        @Override
        public Object put(Term key, Object value) {
            ModelField<M, ?> field = fields.get(key);
            if (field != null) {
                Object originalValue = field.get(model);
                field.set(model, value);
                return originalValue;
            } else {
                return extraData.put(key, value);
            }
        }
    }

    /**
     * A function that converts an object to a string using the {@code valueToString} function.
     */
    private static Function<Object, String> VALUE_TO_STRING = new Function<Object, String>() {
        @Override
        public String apply(Object value) {
            return valueToString(value);
        }
    };

    /**
     * A function that converts an object to a node using the {@code valueToNode} function.
     */
    private static Function<Object, Node> VALUE_TO_NODE = new Function<Object, Node>() {
        @Override
        public Node apply(Object value) {
            return valueToNode(value);
        }
    };

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

    AbstractModel(Type type, Iterable<ModelField<M, ?>> fields) {
        types = ImmutableSet.of(type);
        data = Maps.filterValues(new ModelMap<>(self(), fields), Predicates.notNull());
    }

    @SuppressWarnings("unchecked")
    protected final M self() {
        // This should be safe right? Because AbstractModel<M extends AbstractModel<M>>...
        return (M) this;
    }

    /**
     * Alternate to clone or copy constructor, both of which are not feasible here. Resets the state of this object to
     * match the other object and returns this object.
     */
    public M copyOf(M other) {
        this.setId(other.getId());
        this.data().clear();
        this.data().putAll(other.data());
        return self();
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
    public final boolean equals(@Nullable Object obj) {
        if (obj instanceof Node) {
            Node other = (Node) obj;
            return Objects.equals(id, other.id())
                    && Objects.equals(types, other.types())
                    && Objects.equals(data, other.data());
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

    // Helpers to implement list methods

    /**
     * Helper to safely add a value to a list. If the current value is {@code null}, a new array list is created.
     */
    protected <T, C extends List<? super T>> M safeAddArrayList(ModelField<M, C> field, T value) {
        final M model = self();
        if (value != null) {
            C currentValue = field.get(model);
            if (currentValue != null) {
                try {
                    currentValue.add(value);
                } catch (UnsupportedOperationException e) {
                    List<Object> newValue = new ArrayList<>(currentValue.size() + 1);
                    newValue.addAll(currentValue);
                    newValue.add(value);
                    field.set(model, newValue);
                }
            } else {
                List<Object> newValue = new ArrayList<>();
                newValue.add(value);
                field.set(model, newValue);
            }
        }
        return model;
    }

    /**
     * Helper to safely stream an iterable. If the current value is {@code null}, an empty stream is returned.
     */
    protected <T> FluentIterable<T> safeGet(ModelField<M, ? extends Iterable<T>> field) {
        Iterable<T> value = field.get(self());
        if (value != null) {
            return FluentIterable.from(value);
        } else {
            return FluentIterable.from(ImmutableList.<T> of());
        }
    }

    // Helpers to implement the store method

    /**
     * Helper to coerce a value into a string.
     */
    @Nullable
    protected static String valueToString(@Nullable Object value) {
        if (value instanceof Node) {
            return ((Node) value).id();
        } else if (value != null) {
            return value.toString();
        } else {
            return null;
        }
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

    /**
     * Helper to coerce a value into a timestamp.
     */
    @Nullable
    protected static DateTime valueToDateTime(@Nullable Object value) {
        String stringValue = valueToString(value);
        return stringValue != null ? DateTime.parse(stringValue) : null;
    }

    /**
     * Helper to coerce a value into a node.
     */
    @Nullable
    protected static Node valueToNode(@Nullable Object value) {
        if (value instanceof Node) {
            return (Node) value;
        } else if (value instanceof Map<?, ?>) {
            // TODO We probably should not create a linked data context here
            return new LinkedDataContext().expandToNode((Map<?, ?>) value);
        } else {
            return null;
        }
    }

    /**
     * Helper to coerce a value into a sequence of values.
     */
    protected static FluentIterable<String> valueToStrings(@Nullable Object value) {
        if (value instanceof Iterable<?>) {
            return FluentIterable.from((Iterable<?>) value).transform(VALUE_TO_STRING);
        } else if (value != null) {
            return FluentIterable.from(ImmutableSet.of(value)).transform(VALUE_TO_STRING);
        } else {
            return FluentIterable.from(ImmutableSet.<String> of());
        }
    }

    /**
     * Helper to coerce a value into a sequence of nodes.
     */
    protected static FluentIterable<Node> valueToNodes(@Nullable Object value) {
        if (value instanceof Iterable<?>) {
            return FluentIterable.from((Iterable<?>) value).transform(VALUE_TO_NODE);
        } else if (value != null) {
            return FluentIterable.from(ImmutableSet.of(value)).transform(VALUE_TO_NODE);
        } else {
            return FluentIterable.from(ImmutableSet.<Node> of());
        }
    }

    /**
     * Reduce an empty iterable to a {@code null} value. This is useful when using methods like {@link #valueToStrings}
     * or {@link #valueToNodes} where the final value should be {@code null} instead of empty.
     */
    @Nullable
    protected static <T, C extends Iterable<? super T>> C emptyToNull(C iterable) {
        return Iterables.isEmpty(iterable) ? null : iterable;
    }

    /**
     * A function that converts a node to a compatible model node.
     */
    public static <M extends AbstractModel<? super M>> Function<Node, Iterable<M>> toModel(Class<M> modelType) {
        return new ToModelFunction<>(modelType);
    }
}
