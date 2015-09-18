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

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.ImmutableSet;

/**
 * An anonymous node, may be needed to serialize additional data. The identifier and type information cannot change,
 * however the data map is mutable and can be modified directly.
 *
 * @author jgustie
 */
public final class AnonymousNode implements Node, Serializable {

    private final String id;

    private final Set<Type> types;

    private final Map<Term, Object> data;

    private AnonymousNode(Type... types) {
        id = "_:N" + UUID.randomUUID().toString().replace("-", "");
        this.types = ImmutableSet.copyOf(types);
        data = new LinkedHashMap<>();
    }

    /**
     * Returns a new mutable anonymous node.
     */
    public static Node create(Type type) {
        return new AnonymousNode(type);
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final Set<Type> types() {
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
            return id.equals(other.id()) && types.equals(other.types()) && data.equals(other.data());
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("id", id)
                .add("types", types)
                .add("data", data)
                .toString();
    }
}
