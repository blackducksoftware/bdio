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
import java.util.Map;

import com.github.jsonldjava.core.JsonLdConsts;

/**
 * Metadata used to describe a linked data graph.
 *
 * @author jgustie
 */
public final class BdioMetadata extends LinkedHashMap<String, Object> {

    // TODO CI environment

    public BdioMetadata() {
    }

    public String getId() {
        return (String) get(JsonLdConsts.ID);
    }

    public void setId(String id) {
        put(JsonLdConsts.ID, id);
    }

    // TODO Can we just write values through, this a map of string->object...

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
        Map<String, Object> namedGraph = new LinkedHashMap<>(this);
        if (keys.length > 0) {
            namedGraph.keySet().retainAll(Arrays.asList(keys));
        }
        namedGraph.put(JsonLdConsts.GRAPH, nodes);
        return namedGraph;
    }
}
