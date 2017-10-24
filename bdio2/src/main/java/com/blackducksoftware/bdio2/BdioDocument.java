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

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * A BDIO Document. A BDIO document works on sequences of "BDIO entries", each entry is a JSON-LD
 *
 * @author jgustie
 */
public abstract class BdioDocument {

    /**
     * Exposes the BDIO document using the JSON-LD processing API.
     */
    public interface JsonLdProcessing {
        /**
         * Returns each element in the sequence unaltered.
         */
        Publisher<Object> identity();

        /**
         * Compacts each element in the sequence according to the steps in the JSON-LD Compaction algorithm.
         *
         * @see com.github.jsonldjava.core.JsonLdProcessor#compact(Object, Object, JsonLdOptions)
         */
        Publisher<Map<String, Object>> compact(Object context);

        /**
         * Expands each element in the sequence according to the steps in the Expansion algorithm.
         *
         * @see com.github.jsonldjava.core.JsonLdProcessor#expand(Object, JsonLdOptions)
         */
        Publisher<List<Object>> expand();

        /**
         * Flattens each element in the sequence and compacts it using the passed context according to the steps in the
         * Flattening algorithm.
         *
         * @see com.github.jsonldjava.core.JsonLdProcessor#flatten(Object, Object, JsonLdOptions)
         */
        Publisher<Object> flatten(@Nullable Object context);

        /**
         * Frames each element in the sequence using the frame according to the steps in the Framing Algorithm.
         *
         * @see com.github.jsonldjava.core.JsonLdProcessor#frame(Object, Object, JsonLdOptions)
         */
        Publisher<Map<String, Object>> frame(Object frame);
    }

    /**
     * The configuration options.
     */
    private final BdioOptions options;

    protected BdioDocument(BdioOptions options) {
        this.options = Objects.requireNonNull(options);
    }

    /**
     * Returns the configuration options on this document for use by subclasses.
     */
    protected final BdioOptions options() {
        return options;
    }

    /**
     * Prepares the supplied input stream for being read as a sequence of JSON-LD entries.
     */
    public abstract JsonLdProcessing read(InputStream in, Consumer<BdioMetadata> metadataConsumer);

    /**
     * Creates a subscriber for writing a sequence of JSON-LD entries to the supplied output streams.
     */
    public abstract Subscriber<Object> write(BdioMetadata metadata, StreamSupplier entryStreams);

    /**
     * Expose the JSON-LD processing API on a given sequence of inputs.
     */
    protected abstract JsonLdProcessing jsonLd(Publisher<Object> inputs);

    // NOTE: This is one place where we are opinionated on our JSON-LD usage, that means this code can break
    // general JSON-LD interoperability if someone produces something we weren't expecting...

    /**
     * Extracts metadata from the supplied entry.
     */
    protected static Map<String, Object> toGraphMetadata(Object input) {
        if (input instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) input;
            if (data.containsKey(JsonLdConsts.GRAPH)) {
                return Maps.filterKeys(data, key -> !key.equals(JsonLdConsts.GRAPH));
            }
        }
        return ImmutableMap.of();
    }

    /**
     * Returns the list of JSON-LD nodes extracted from the supplied input.
     */
    public static List<Map<String, Object>> toGraphNodes(Object input) {
        // TODO Should this consider the identifier from `metadata.id()`?
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
            // throw new JsonLdError(JsonLdError.Error.SYNTAX_ERROR);
            return null;
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
            return getGraph(input);
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

}
