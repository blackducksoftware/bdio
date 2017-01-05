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
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.http.impl.client.CloseableHttpClient;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * A BDIO Document. A BDIO document is a {@link rx.subjects.Subject} of JSON-LD graphs; each element published or
 * consumed is a graph (generally a sequence of nodes).
 * <p>
 * Because the data is represented as a subject, there is meant to be very little data actually at rest: the data flows
 * through the document but is not actually held in memory. If you are trying to read BDIO data in from a file, you must
 * first subscribe to this document (e.g. using {@link #asObservable()} or one of the JSON-LD API operations)
 * <em>before</em> invoking {@link #read(InputStream)}. Similarly, if you are trying to write BDIO data out to a file,
 * you must first invoke {@link #writeTo(OutputStream)} before publishing data (e.g. using {@link #asSubscriber()}).
 *
 * @author jgustie
 */
public abstract class BdioDocument {

    /**
     * Exposes the BDIO document using the JSON-LD processing API.
     */
    public interface JsonLdProcessing {
        /**
         * Compacts each element in the sequence according to the steps in the JSON-LD Compaction algorithm.
         *
         * @see JsonLdProcessor#compact(Object, Object, JsonLdOptions)
         */
        Publisher<Map<String, Object>> compact(Object context);

        /**
         * Expands each element in the sequence according to the steps in the Expansion algorithm.
         *
         * @see JsonLdProcessor#expand(Object, JsonLdOptions)
         */
        Publisher<List<Object>> expand();

        /**
         * Flattens each element in the sequence and compacts it using the passed context according to the steps in the
         * Flattening algorithm.
         *
         * @see JsonLdProcessor#flatten(Object, Object, JsonLdOptions)
         */
        Publisher<Object> flatten(@Nullable Object context);

        /**
         * Frames each element in the sequence using the frame according to the steps in the Framing Algorithm.
         *
         * @see JsonLdProcessor#frame(Object, Object, JsonLdOptions)
         */
        Publisher<Map<String, Object>> frame(Object frame);

        /**
         * Returns the options to use when invoking the JSON-LD API.
         */
        JsonLdOptions options();
    }

    /**
     * The JSON-LD options used to manipulate the data.
     */
    private final JsonLdOptions options;

    /**
     * A factory used to create new parser instances. This is configurable so we can support the loading of legacy
     * formats, like scan containers and BDIO 1.x.
     */
    private final Function<InputStream, Emitter> parserFactory;

    protected BdioDocument(Builder builder) {
        // Construct the JSON-LD options
        options = new JsonLdOptions(Objects.requireNonNull(builder.base));
        options.setExpandContext(Objects.requireNonNull(builder.expandContext));
        options.setDocumentLoader(builder.documentLoader.build());

        // Store a reference to the parser factory
        parserFactory = Objects.requireNonNull(builder.parserFactory);
    }

    /**
     * Returns the JSON-LD API configuration options.
     */
    protected final JsonLdOptions options() {
        return options;
    }

    /**
     * Returns a new parser for the supplied byte stream.
     */
    protected final Emitter newParser(InputStream in) {
        return parserFactory.apply(in);
    }

    /**
     * Allows you to add or consume JSON-LD graph entries.
     */
    public abstract Processor<Object, Object> processor();

    /**
     * Allows you to add JSON-LD nodes to this document.
     */
    public abstract Subscriber<Map<String, Object>> asNodeSubscriber(BdioMetadata metadata);

    /**
     * Allows you to consume the aggregate BDIO metadata across all entries.
     */
    public abstract BdioDocument metadata(Consumer<BdioMetadata> metadataSubscriber);

    /**
     * Allows you to consume just the metadata from the first entry. This is always enough to obtain the identifier,
     * however, depending on how the data was structured there is no guarantee that other metadata will be available (in
     * general, if a BDIO file has a header entry, this will give you complete metadata).
     */
    public abstract BdioDocument takeFirstMetadata(Consumer<BdioMetadata> metadataSubscriber);

    /**
     * Allows you to consume the processed JSON-LD graph entries from this document.
     */
    public abstract JsonLdProcessing jsonld();

    /**
     * Writes the BDIO data coming into this document out to the supplied byte stream.
     */
    // TODO Also take a Consumer<Throwable> for error handling?
    public abstract BdioDocument writeToFile(BdioMetadata metadata, OutputStream out);

    /**
     * Reads BDIO data into this document from the supplied byte stream.
     */
    public abstract BdioDocument read(InputStream in);

    /**
     * A builder for constructing BDIO documents.
     */
    public static class Builder {

        private String base = "";

        private Object expandContext = Bdio.Context.DEFAULT.toString();

        private final RemoteDocumentLoader.Builder documentLoader = new RemoteDocumentLoader.Builder();

        private Function<InputStream, Emitter> parserFactory = BdioEmitter::new;

        public Builder() {
            // Always load all versions of the BDIO context for offline access
            for (Bdio.Context context : Bdio.Context.values()) {
                documentLoader.withResource(context.toString(), BdioDocument.class, context.resourceName());
            }
        }

        /**
         * Creates a new BDIO document of the specified type from the current state of this builder.
         */
        public <D extends BdioDocument> D build(Class<D> type) {
            try {
                return type.getConstructor(getClass()).newInstance(this);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot construct BdioDocument: " + type.getName(), e);
            }
        }

        /**
         * Specifies the base URI. The base URI is used to relavitize identifiers.
         */
        public Builder base(String base) {
            this.base = Objects.requireNonNull(base);
            // TODO Verify empty or absolute URI?
            return this;
        }

        /**
         * Specifies the base URI.
         *
         * @see #base(String)
         */
        public Builder base(URI base) {
            return base(base.toString());
        }

        /**
         * Enable remote context loading using the supplied HTTP client.
         */
        public Builder allowRemoteLoading(CloseableHttpClient httpClient) {
            documentLoader.allowRemoteLoading(httpClient);
            return this;
        }

        /**
         * Sets the expansion context.
         */
        // TODO Have expandContext(Map<String, Object>)/expandContext(String)/expandContext(URI) instead?
        public Builder expandContext(@Nullable Object expandContext) {
            this.expandContext = expandContext;
            return this;
        }

        /**
         * Sets the expansion context to the default BDIO context.
         */
        public Builder expandAsBdio() {
            return expandContext(Bdio.Context.DEFAULT.toString());
        }

        /**
         * Sets the expansion context for processing BDIO 1.0 data.
         */
        public Builder expandAsBdio_1_0() {
            return expandContext(Bdio.Context.VERSION_1_0.toString());
        }

        /**
         * Sets the expansion context for processing BDIO 1.1 data.
         */
        public Builder expandAsBdio_1_1() {
            return expandContext(Bdio.Context.VERSION_1_1.toString());
        }

        /**
         * Sets the expansion context for processing BDIO 2.0 data.
         */
        public Builder expandAsBdio_2_0() {
            return expandContext(Bdio.Context.VERSION_2_0.toString());
        }

        /**
         * Sets the expansion context based on a specific version of the BDIO specification.
         */
        public Builder expandAsBdio(String specVersion) {
            switch (specVersion) {
            case "": // v0 == v1.0.0
            case "1.0.0":
                return expandAsBdio_1_0();
            case "1.1.0":
                return expandAsBdio_1_1();
            case "2.0.0":
                return expandAsBdio_2_0();
            default:
                throw new IllegalArgumentException("unknown BDIO specification version: " + specVersion);
            }
        }

        /**
         * Use an alternate BDIO parser for reading.
         */
        public Builder usingParser(Function<InputStream, Emitter> parserFactory) {
            this.parserFactory = Objects.requireNonNull(parserFactory);
            return this;
        }

    }

    // NOTE: This is one place where we are opinionated on our JSON-LD usage, that means this code can break
    // general JSON-LD interoperability if someone produces something we weren't expecting...

    /**
     * Returns the list of JSON-LD nodes extracted from the supplied input.
     */
    protected final List<Map<String, Object>> extractNodes(Object input) throws JsonLdError {
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
            throw new JsonLdError(JsonLdError.Error.SYNTAX_ERROR);
        }
    }

    /**
     * Framing does not work on named graphs so we need to pull just the graph nodes out.
     *
     * @see <a href="https://github.com/jsonld-java/jsonld-java/issues/109">#109</a>
     */
    @Nullable
    protected final Object dropGraphLabel(@Nullable Object input) {
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

    /**
     * Extracts metadata from the supplied entry.
     */
    protected final Map<String, Object> extractMetadata(Object input) {
        if (input instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) input;
            if (data.containsKey(JsonLdConsts.GRAPH)) {
                return Maps.filterKeys(data, key -> !key.equals(JsonLdConsts.GRAPH));
            }
        }
        return ImmutableMap.of();
    }

}
