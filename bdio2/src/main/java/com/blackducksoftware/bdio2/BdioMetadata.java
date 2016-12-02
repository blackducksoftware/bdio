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
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableSet;

/**
 * Metadata used to describe a linked data graph.
 *
 * @author jgustie
 */
public final class BdioMetadata extends LinkedHashMap<String, Object> {

    // TODO CI environment

    public BdioMetadata(@Nullable String id) {
        if (id != null) {
            put(JsonLdConsts.ID, id);
        }
    }

    public String id() {
        return (String) get(JsonLdConsts.ID);
    }

    public void setCreation(Instant creation) {
        put(Bdio.DataProperty.creation.toString(), creation.toString());
    }

    public void setCreator(String creator) {
        put(Bdio.DataProperty.creator.toString(), creator);
    }

    public void setProducer(String producer) {
        put(Bdio.DataProperty.producer.toString(), producer);
    }

    /**
     * Returns a named graph using the specified keys from this metadata.
     */
    public Map<String, Object> asNamedGraph(Object nodes, String... keys) {
        Map<String, Object> namedGraph = new LinkedHashMap<>();
        namedGraph.putAll(this);
        if (keys.length != 0) {
            // Keep the identifier and anything else they asked for
            namedGraph.keySet().retainAll(ImmutableSet.<String> builder().add(JsonLdConsts.ID).add(keys).build());
        }
        namedGraph.put(JsonLdConsts.GRAPH, nodes);
        return namedGraph;
    }
}
