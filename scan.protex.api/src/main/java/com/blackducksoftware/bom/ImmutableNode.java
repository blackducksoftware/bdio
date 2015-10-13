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
package com.blackducksoftware.bom;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * An immutable implementation of node.
 *
 * @author jgustie
 */
public final class ImmutableNode implements Node, Serializable {

    /**
     * A builder for immutable node instances.
     */
    public static final class Builder {
        private String id;

        private final Set<Type> types = new LinkedHashSet<>();

        private final Map<Term, Object> data = new LinkedHashMap<>();

        private Builder() {
        }

        public Node build() {
            return new ImmutableNode(id, types, data);
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(Type type) {
            types.clear();
            types.add(type);
            return this;
        }

        public Builder addType(Type type) {
            types.add(type);
            return this;
        }

        public Builder put(Term term, Object value) {
            data.put(term, checkNotNull(value, "value for '%s' is null", term));
            return this;
        }

        public Builder putAll(Map<Term, Object> data) {
            this.data.putAll(data);
            return this;
        }
    }

    private final String id;

    private final Set<Type> types;

    private final Map<Term, Object> data;

    private ImmutableNode(String id, Set<Type> types, Map<Term, Object> data) {
        this.id = id;
        this.types = ImmutableSet.copyOf(types);
        this.data = ImmutableMap.copyOf(data);
    }

    private ImmutableNode(String id) {
        this(id, ImmutableSet.<Type> of(), ImmutableMap.<Term, Object> of());
    }

    /**
     * Returns a new immutable node.
     */
    public static Node of(String id, Set<Type> types, Map<Term, Object> data) {
        return new ImmutableNode(id, types, data);
    }

    /**
     * Returns a new immutable copy of a node.
     */
    public static Node copyOf(Node node) {
        return new ImmutableNode(node.id(), node.types(), node.data());
    }

    /**
     * Returns a new identifier node. These may be used as values.
     */
    public static Node newIdentifierNode(Node node) {
        return new ImmutableNode(node.id());
    }

    /**
     * Returns a new identifier node. These may be used as values.
     */
    public static Node newIdentifierNode(String id) {
        return new ImmutableNode(id);
    }

    /**
     * Returns a builder for immutable node instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<Type> types() {
        return types;
    }

    @Override
    public Map<Term, Object> data() {
        return data;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, types, data);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Node) {
            Node other = (Node) obj;
            return Objects.equals(id, other.id()) && types.equals(other.types()) && data.equals(other.data());
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .omitNullValues()
                .add("id", id)
                .add("types", types)
                .add("data", data)
                .toString();
    }
}
