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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.google.common.collect.Lists;

/**
 * Utility methods for manipulating JSON-LD data representing BDIO.
 *
 * @author jgustie
 */
public final class BdioJsonLd {

    // TODO Does this logic belong on the BdioDocument base class?

    // NOTE: This is one place where we are opinionated on our JSON-LD usage, that means this code can break
    // general JSON-LD interoperability if someone produces something we weren't expecting...

    /**
     * Returns the list of JSON-LD nodes extracted from the supplied input.
     */
    // TODO Should this accept an optional identifier for getting a particular named graph?
    public static List<Map<String, Object>> extractNodes(Object input) throws JsonLdError {
        Object nodes = null;
        if (input instanceof List<?>) {
            for (Object item : (List<?>) input) {
                nodes = getGraph(item);
                if (nodes != null) {
                    break;
                }
            }
            if (nodes == null) {
                nodes = input;
            }
        } else if (input instanceof Map<?, ?>) {
            nodes = getGraph(input);
        }

        if (nodes instanceof List<?>) {
            // TODO How can we verify these casts? What does the JSON-LD library do?
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodeList = (List<Map<String, Object>>) nodes;
            // TODO Sort these while we have the in memory list?
            return nodeList;
        } else {
            // TODO Emit just the input as a single node list? Only if it has '@id'?
            throw new JsonLdError(JsonLdError.Error.SYNTAX_ERROR);
        }
    }

    /**
     * Framing does not work on named graphs so we need to pull just the graph nodes out.
     *
     * @see <a href="https://github.com/jsonld-java/jsonld-java/issues/109">#109</a>
     */
    @Nullable
    public static Object dropGraphLabel(@Nullable Object input) {
        if (input instanceof Map<?, ?>
                && ((Map<?, ?>) input).containsKey(JsonLdConsts.ID)
                && ((Map<?, ?>) input).containsKey(JsonLdConsts.GRAPH)) {
            return ((Map<?, ?>) input).get(JsonLdConsts.GRAPH);
        } else {
            return input;
        }
    }

    @Nullable
    private static Object getGraph(@Nullable Object input) {
        if (input instanceof Map<?, ?>) {
            // TODO Do we need an ID to check to determine which graph to get?
            return ((Map<?, ?>) input).get(JsonLdConsts.GRAPH);
        } else {
            return null;
        }
    }

    /**
     * Returns a function which performs input normalization. Input normalization simply ensures that all JSON-LD
     * entries take a named graph form where the {@value JsonLdConsts#GRAPH} value is a list of node objects. Additional
     * named graph metadata may be introduced with normalization.
     */
    public static Function<Object, Map<String, Object>> normalizer(BdioMetadata metadata, JsonLdOptions options) {
        return input -> {
            if (input instanceof Map<?, ?>) {
                if (((Map<?, ?>) input).containsKey(JsonLdConsts.GRAPH)) {
                    // Named graph
                    // TODO How can we verify these casts? What does the JSON-LD library do?
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nodeObject = (Map<String, Object>) input;
                    if (!Objects.equals(nodeObject.get(JsonLdConsts.ID), metadata.id())) {
                        nodeObject.put(JsonLdConsts.ID, metadata.id());
                    }
                    return nodeObject;
                } else {
                    // Assume a single node object
                    Map<String, Object> nodeObject = new LinkedHashMap<>();
                    nodeObject.put(JsonLdConsts.ID, metadata.id());
                    nodeObject.put(JsonLdConsts.GRAPH, Lists.newArrayList(input));
                    return nodeObject;
                }
            } else if (input instanceof List<?>) {
                // Array of node objects
                Map<String, Object> nodeObject = new LinkedHashMap<>();
                nodeObject.put(JsonLdConsts.ID, metadata.id());
                nodeObject.put(JsonLdConsts.GRAPH, input);
                return nodeObject;
            }

            // What should we do here?
            throw new IllegalArgumentException("unable to normalize input");
        };
    }

    private BdioJsonLd() {
        assert false;
    }
}
