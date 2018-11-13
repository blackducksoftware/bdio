/*
 * Copyright 2016 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.common.value.ProductList;
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
    private final BdioContext context;

    protected BdioDocument(BdioContext context) {
        this.context = Objects.requireNonNull(context);
    }

    protected final BdioContext context() {
        return context;
    }

    /**
     * Prepares the supplied input stream for being read as a sequence of BDIO entries.
     */
    public abstract Publisher<Object> read(InputStream in);

    /**
     * Creates a subscriber for writing a sequence of JSON-LD entries to the supplied output streams.
     */
    // TODO Should we accept a Consumer<Throwable> for error handling?
    public abstract Subscriber<Object> write(BdioMetadata metadata, StreamSupplier entryStreams);

    /**
     * Leverage the JSON-LD processing API on a given sequence of BDIO entries.
     */
    public abstract JsonLdProcessing jsonLd(Publisher<Object> inputs);

    /**
     * Returns a single element sequence of the combined metadata from all of the supplied BDIO entries. Typically BDIO
     * producers will generate BDIO entries in such a way that all of the metadata is found only on the first entry,
     * however this is not a strict requirement.
     */
    public abstract Publisher<BdioMetadata> metadata(Publisher<Object> inputs);

    // NOTE: This is one place where we are opinionated on our JSON-LD usage, that means this code can break
    // general JSON-LD interoperability if someone produces something we weren't expecting...

    /**
     * Extracts metadata from the supplied entry.
     */
    protected static Map<String, Object> toMetadata(Object input) {
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
     * Unfolds expanded JSON-LD by returning only the {@code Map} instances, cast to have string keys.
     */
    @SuppressWarnings("unchecked")
    protected static Iterable<Map<String, Object>> unfoldExpand(List<Object> expanded) {
        if (expanded.isEmpty() || (expanded.size() == 1 && expanded.get(0) instanceof Map<?, ?>)) {
            // This is probably the most common case, there is no need to create extra overhead when a cast will do
            return (Iterable<Map<String, Object>>) ((Object) expanded);
        } else {
            // Filter the whole list with our danger casts
            return () -> expanded.stream().flatMap(x -> x instanceof Map<?, ?> ? Stream.of((Map<String, Object>) x) : Stream.empty()).iterator();
        }
    }

    /**
     * We can stop processing metadata if we are looking at a legacy format because we only write metadata to the first
     * entry when we are converting.
     */
    public boolean needsMoreMetadata(Object entry) {
        if (entry instanceof Map<?, ?>) {
            String key = Bdio.DataProperty.publisher.toString();
            Object value = ((Map<?, ?>) entry).get(key);
            if (value != null) {
                ProductList products = ProductList.from(context.fromFieldValue(key, value));
                if (products.tryFind(p -> p.name().equals(LegacyScanContainerEmitter.class.getSimpleName())
                        || p.name().equals(LegacyBdio1xEmitter.class.getSimpleName())).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the list of JSON-LD nodes extracted from the supplied input.
     */
    public static List<Map<String, Object>> toGraphNodes(Object input) {
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
            return nodeList;
        } else {
            return new ArrayList<>(0);
        }
    }

    @Nullable
    private static Object getGraph(@Nullable Object input) {
        if (input instanceof Map<?, ?>) {
            return ((Map<?, ?>) input).get(JsonLdConsts.GRAPH);
        } else {
            return null;
        }
    }

}
