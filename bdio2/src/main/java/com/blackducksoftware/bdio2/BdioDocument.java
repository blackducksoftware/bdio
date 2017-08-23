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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.google.common.base.Strings;
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

    protected BdioDocument(Builder builder) {
        // Construct the JSON-LD options
        options = new JsonLdOptions(Objects.requireNonNull(builder.base));
        options.setDocumentLoader(new BdioDocumentLoader(options.getDocumentLoader()));
        options.setExpandContext(builder.expandContext);
    }

    /**
     * Returns the JSON-LD API configuration options.
     */
    protected final JsonLdOptions options() {
        return options;
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
     *
     * @return this BDIO document for call chaining
     */
    public abstract BdioDocument metadata(Consumer<BdioMetadata> metadataSubscriber);

    /**
     * Allows you to consume just the metadata from the first entry. This is always enough to obtain the identifier,
     * however, depending on how the data was structured there is no guarantee that other metadata will be available (in
     * general, if a BDIO file has a header entry, this will give you complete metadata).
     *
     * @return this BDIO document for call chaining
     */
    public abstract BdioDocument takeFirstMetadata(Consumer<BdioMetadata> metadataSubscriber);

    /**
     * Allows you to consume the processed JSON-LD graph entries from this document.
     */
    public abstract JsonLdProcessing jsonld();

    /**
     * Writes the BDIO data coming into this document out to the supplied byte stream.
     *
     * @return this BDIO document for call chaining
     */
    // TODO Also take a Consumer<Throwable> for error handling?
    public abstract BdioDocument writeToFile(BdioMetadata metadata, OutputStream out);

    // TODO Should we have more generic writing facilities for streaming?
    // public abstract Subscriber<Map<String, Object>> write(Supplier<OutputStream> streamFactory);

    /**
     * Reads BDIO data into this document from the supplied byte stream.
     *
     * @return this BDIO document for call chaining
     */
    public abstract BdioDocument read(InputStream in);

    /**
     * A builder for constructing BDIO documents.
     */
    public static class Builder {

        private String base;

        private Object expandContext;

        public Builder() {
            base = "";
            expandContext = Bdio.Context.DEFAULT.toString();
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
         * Specifies the base URI as a string. The base URI is used to relavitize identifiers.
         */
        public Builder base(@Nullable String base) {
            if (Strings.isNullOrEmpty(base)) {
                this.base = Strings.nullToEmpty(base);
                return this;
            } else {
                try {
                    return base(new URI(base));
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("base URI must be well formed", e);
                }
            }
        }

        /**
         * Specifies the base URI. The base URI is used to relavitize identifiers.
         */
        public Builder base(@Nullable URI base) {
            checkArgument(base == null || base.isAbsolute(), "base URI must be absolute");
            checkArgument(base == null || !base.isOpaque(), "base URI must be hierarchical");
            this.base = Objects.toString(base, "");
            return this;
        }

        /**
         * Prepares this document for processing documents based on their detected or declared content type.
         * <p>
         * Note that the supplied expansion context is only used with the {@linkplain Bdio.ContentType#JSON JSON} type.
         */
        public Builder forContentType(@Nullable Bdio.ContentType contentType, @Nullable Object expandContext) {
            checkArgument(expandContext == null
                    || expandContext instanceof String
                    || expandContext instanceof Map<?, ?>
                    || expandContext instanceof List<?>,
                    "expandContext must be a String, Map<String, Object> or a List<Object>");

            if (contentType == null) {
                this.expandContext = Bdio.Context.DEFAULT.toString();
            } else if (contentType.equals(Bdio.ContentType.JSON)) {
                // TODO Warn if expandContext is null? Require non-null?
                this.expandContext = expandContext;
            } else if (contentType.equals(Bdio.ContentType.JSONLD)) {
                this.expandContext = null;
            } else if (contentType.equals(Bdio.ContentType.BDIO_V2_JSON) || contentType.equals(Bdio.ContentType.BDIO_V2_ZIP)) {
                this.expandContext = Bdio.Context.VERSION_2_0.toString();
            } else {
                throw new IllegalArgumentException("unknown content type: " + contentType);
            }
            return this;
        }

        /**
         * Prepares the document for processing BDIO loaded from plain JSON. The {@code expandContext} is typically a
         * {@code String} representation of the {@code http://www.w3.org/ns/json-ld#context} link relationship (a URI
         * identifying the context), however it can also be a {@code Map<String, Object>} representing an already parsed
         * JSON-LD context. Note that, while accepted, a {@code null} context will only produce meaningful results if
         * the JSON contains fully qualified IRIs.
         */
        public Builder forJson(@Nullable Object expandContext) {
            return forContentType(Bdio.ContentType.JSON, expandContext);
        }

        /**
         * Prepares this document for processing BDIO loaded from JSON-LD. The JSON-LD contexts must be explicitly
         * defined within the document itself.
         */
        public Builder forJsonLd() {
            return forContentType(Bdio.ContentType.JSONLD, null);
        }

        /**
         * Prepares this document for processing BDIO documents. This assumes the default BDIO context will be used for
         * processing plain JSON or JSON-LD input (Zip forms should already be fully expanded internally).
         */
        public Builder forBdio() {
            return forContentType(null, null);
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
