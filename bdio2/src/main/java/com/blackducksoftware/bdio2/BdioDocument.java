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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.http.impl.client.CloseableHttpClient;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.google.common.base.MoreObjects;
import com.google.common.base.StandardSystemProperty;

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
    }

    /**
     * The linked data graph metadata.
     */
    private final BdioMetadata metadata;

    /**
     * The JSON-LD options used to manipulate the data.
     */
    private final JsonLdOptions options;

    protected BdioDocument(Builder builder) {
        // Construct a new metadata instance
        metadata = new BdioMetadata(MoreObjects.firstNonNull(builder.id, "@default"));
        metadata.setCreation(MoreObjects.firstNonNull(builder.creation, Instant.now()));
        metadata.setCreator(MoreObjects.firstNonNull(builder.creator, StandardSystemProperty.USER_NAME.value()));

        // Construct the JSON-LD options
        options = new JsonLdOptions(MoreObjects.firstNonNull(builder.base, ""));
        options.setExpandContext(MoreObjects.firstNonNull(builder.expandContext, Bdio.Context.DEFAULT.toString()));
        options.setDocumentLoader(builder.documentLoader.build());
    }

    /**
     * Returns the graph metadata for this document.
     */
    protected final BdioMetadata metadata() {
        return metadata;
    }

    /**
     * Returns the JSON-LD API configuration options.
     */
    protected final JsonLdOptions options() {
        return options;
    }

    /**
     * Allows you to add or consume JSON-LD graphs using this document.
     */
    public abstract Processor<Object, Object> processor();

    /**
     * Allows you to add JSON-LD nodes to this document.
     */
    public abstract Subscriber<Map<String, Object>> asNodeSubscriber();

    /**
     * Allows you to consume the processed JSON-LD graph elements from this document.
     */
    public abstract JsonLdProcessing jsonld();

    /**
     * Writes the BDIO data coming into this document out to the supplied byte stream.
     */
    // TODO Also take an Action1<Throwable> for error handling?
    public abstract BdioDocument writeToFile(OutputStream out);

    /**
     * Reads BDIO data into this document from the supplied byte stream.
     */
    public abstract BdioDocument read(InputStream in);

    /**
     * A builder for constructing BDIO documents.
     */
    public static class Builder {

        private String id;

        private Instant creation;

        private String creator;

        private String base;

        private Object expandContext;

        private final RemoteDocumentLoader.Builder documentLoader = new RemoteDocumentLoader.Builder();

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
         * Specifies the identifier used to name the graph. The value must be a valid URI.
         */
        public Builder id(String id) {
            this.id = Objects.requireNonNull(id);
            // TODO Verify URI syntax?
            return this;
        }

        /**
         * Specifies the creation time for the graph. If not specified, the time at which the {@link #build()}
         * method is invoked will be used.
         */
        public Builder creation(Instant creation) {
            this.creation = Objects.requireNonNull(creation);
            return this;
        }

        /**
         * Specifies the creator of the graph. If not specified, the owner of the current process will be used.
         */
        public Builder creator(String creator) {
            this.creator = Objects.requireNonNull(creator);
            return this;
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

    }

}
