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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.datatype.Products;
import com.blackducksoftware.common.base.ExtraStrings;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableMap;

/**
 * Metadata used to describe a linked data graph.
 *
 * @author jgustie
 */
public final class BdioMetadata extends BdioObject {

    /**
     * Creates a new, empty metadata instance.
     */
    public BdioMetadata() {
        this(ImmutableMap.of());
    }

    /**
     * Creates a new metadata instance populated from the supplied values.
     */
    public BdioMetadata(Map<String, Object> other) {
        super(other);
    }

    /**
     * Creates a new metadata instance with a random identifier.
     */
    public static BdioMetadata createRandomUUID() {
        return new BdioMetadata().id(randomId());
    }

    /**
     * Merges additional metadata into this metadata instance.
     */
    public BdioMetadata merge(Map<String, Object> other) {
        other.forEach((key, value) -> {
            if (key.equals(JsonLdConsts.ID)) {
                checkArgument(value instanceof String, "identifier must be mapped to a string");
                if (id() == null) {
                    // Establishes a new identifier
                    id((String) value);
                } else if (ExtraStrings.beforeLast((String) value, '#').equals(id())) {
                    // Discard non-matching fragments
                    if (!value.equals(get(JsonLdConsts.ID))) {
                        id(ExtraStrings.beforeLast((String) value, '#'));
                    }
                } else {
                    // Incompatible identifiers
                    throw new IllegalArgumentException("identifier mismatch: " + value + " (was expecting " + id() + ")");
                }
            } else if (key.equals(Bdio.DataProperty.producer.toString())) {
                Object producer = get(Bdio.DataProperty.producer.toString());
                if (producer != null) {
                    // Merges to create new producer
                    Products.Builder builder = new Products.Builder();
                    ((Products) mapper().fromFieldValue(producer)).forEach(builder::addProduct);
                    ((Products) mapper().fromFieldValue(value)).forEach(builder::addProduct);
                    putData(Bdio.DataProperty.producer, builder.build());
                } else {
                    // Establishes a new producer
                    put(key, value);
                }
            } else {
                put(key, value);
            }
        });
        return this;
    }

    /**
     * Sets the named graph label.
     */
    public BdioMetadata id(@Nullable String id) {
        put(JsonLdConsts.ID, id);
        return this;
    }

    /**
     * Sets the display name for the named graph.
     */
    public BdioMetadata name(@Nullable String name) {
        putData(Bdio.DataProperty.name, name);
        return this;
    }

    /**
     * Sets the time at which the named graph was created.
     */
    public BdioMetadata creation(@Nullable Instant creation) {
        putData(Bdio.DataProperty.creation, creation);
        return this;
    }

    /**
     * Sets the identifier of the user who created the named graph.
     */
    public BdioMetadata creator(@Nullable String creator) {
        putData(Bdio.DataProperty.creator, creator);
        return this;
    }

    /**
     * Sets the producer string of the tool (or tools) that created the named graph.
     */
    public BdioMetadata producer(@Nullable Products producer) {
        putData(Bdio.DataProperty.producer, producer);
        return this;
    }

    /**
     * Returns a named graph using the specified keys from this metadata.
     *
     * @param graph
     *            the JSON-LD data to associate with the {@value JsonLdConsts#GRAPH} value.
     * @param keys
     *            the metadata keys to include in the named graph (all metadata is included by default).
     */
    public Map<String, Object> asNamedGraph(Object graph, String... keys) {
        Objects.requireNonNull(graph);
        Map<String, Object> namedGraph = new LinkedHashMap<>(this);
        if (keys.length > 0) {
            namedGraph.keySet().retainAll(Arrays.asList(keys));
        }
        namedGraph.put(JsonLdConsts.GRAPH, graph);
        return namedGraph;
    }

    /**
     * Returns a named graph that only contains metadata.
     *
     * @see #asNamedGraph(Object, String...)
     */
    public Map<String, Object> asNamedGraph() {
        return asNamedGraph(new ArrayList<>(0));
    }

}
