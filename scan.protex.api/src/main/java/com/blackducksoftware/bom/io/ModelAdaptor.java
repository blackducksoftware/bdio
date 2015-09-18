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
package com.blackducksoftware.bom.io;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.blackducksoftware.bom.Node;
import com.blackducksoftware.bom.Term;
import com.blackducksoftware.bom.Type;
import com.blackducksoftware.bom.io.ModelAdaptors.Adaptor;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;

/**
 * A node implementation that allows for writing of model objects. Creation of an adaptor is expensive relative to use,
 * once created, an instance should be re-used.
 * <p>
 * This object is not thread-safe, external synchronization is required.
 *
 * @author jgustie
 */
public final class ModelAdaptor extends AdaptorNode<Object> {

    /**
     * The class to type mappings to use.
     */
    private final Map<Class<?>, Set<Type>> TYPES;

    /**
     * A series of maps that expose the terms of a model. Each map assumes the current value of {@code model} is of the
     * type that is keyed off of. For example, {@code DATA.get(File.class).get(someFileTerm)}.
     */
    private final Map<Class<?>, Map<Term, Object>> DATA;

    /**
     * The identifier for the current model.
     */
    private String id;

    /**
     * The model. May be changed, must be one of the supported types.
     */
    private Object model;

    private ModelAdaptor() {
        // A supplier that exposes the current value of the model field
        final Supplier<Object> modelSupplier = new Supplier<Object>() {
            @Override
            public Object get() {
                return model;
            }
        };

        ImmutableMap.Builder<Class<?>, Set<Type>> typesBuilder = ImmutableMap.builder();
        ImmutableMap.Builder<Class<?>, Map<Term, Object>> dataBuilder = ImmutableMap.builder();
        for (Entry<Class<?>, Adaptor> adaptor : ModelAdaptors.registeredAdaptors().entrySet()) {
            typesBuilder.put(adaptor.getKey(), adaptor.getValue().types());
            dataBuilder.put(adaptor.getKey(), adaptor.getValue().asMap(modelSupplier));
        }
        TYPES = typesBuilder.build();
        DATA = dataBuilder.build();
    }

    public static ModelAdaptor create() {
        return new ModelAdaptor();
    }

    /**
     * Updates this adaptor with a new identified model, returning itself for potential call chaining.
     */
    public ModelAdaptor update(String id, Object model) {
        // Make sure the model can be adapted sooner rather then later
        checkArgument(DATA.containsKey(model.getClass()), "cannot adapt type: %s", model.getClass().getName());
        this.model = model;

        return update(id);
    }

    /**
     * Updates the current identifier without changing the underlying model. This is useful when the model is mutable
     * and is being changed for each identifier.
     */
    public ModelAdaptor update(String id) {
        this.id = checkNotNull(id);
        return this;
    }

    /**
     * Updates this adaptor with an existing node, returning itself for potential call chaining.
     * <p>
     * Note that this is only useful if the caller maintains a reference to the current model (e.g. by using
     * {@code createModel}).
     */
    public ModelAdaptor update(Node other) {
        checkArgument(types().equals(other.types()), "incompatible types: expected %s but was %s", types(), other.types());
        data().putAll(other.data());
        return update(other.id());
    }

    /**
     * Creates and returns new model of the specified type.
     */
    public <M> M createModel(Class<M> type) throws IllegalAccessException, InstantiationException {
        checkArgument(DATA.containsKey(type), "cannot adapt type: %s", type.getName());
        M model = type.newInstance();
        this.model = model;
        return model;
    }

    @Override
    public String id() {
        return checkNotNull(id, "call update at least once");
    }

    @Override
    public Set<Type> types() {
        return TYPES.get(checkNotNull(model, "call update at least once").getClass());
    }

    @Override
    public Map<Term, Object> data() {
        return DATA.get(checkNotNull(model, "call update at least once").getClass());
    }

}
