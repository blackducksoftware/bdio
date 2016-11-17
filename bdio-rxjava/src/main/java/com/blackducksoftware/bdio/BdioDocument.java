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
package com.blackducksoftware.bdio;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.http.impl.client.CloseableHttpClient;
import org.joda.time.Instant;

import com.blackducksoftware.bdio.BdioOnSubscribe;
import com.blackducksoftware.bdio.BdioSubscriber;
import com.blackducksoftware.bdio.jsonld.JsonLdKeyword;
import com.blackducksoftware.bdio.jsonld.RemoteDocumentLoader;
import com.blackducksoftware.bdio.rx.OperatorGraphNodes;
import com.blackducksoftware.bdio.rx.RxJsonLdProcessor;
import com.github.jsonldjava.core.JsonLdOptions;
import com.google.common.base.MoreObjects;
import com.google.common.base.StandardSystemProperty;

import rx.Observable;
import rx.Observable.Transformer;
import rx.Subscriber;
import rx.functions.Func1;
import rx.observers.Subscribers;
import rx.subjects.PublishSubject;

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
public final class BdioDocument {

    /**
     * The linked data graph metadata.
     */
    private final BdioMetadata metadata;

    /**
     * The JSON-LD options used to manipulate the data.
     */
    private final JsonLdOptions options;

    /**
     * The data for this document, represented as an observable subject.
     */
    // TODO Should read/write propagate errors out to this subject?
    private PublishSubject<Object> data = PublishSubject.create();

    private BdioDocument(Builder builder) {
        metadata = new BdioMetadata(MoreObjects.firstNonNull(builder.id, "@default"));
        metadata.setCreation(MoreObjects.firstNonNull(builder.creation, Instant.now()));
        metadata.setCreator(MoreObjects.firstNonNull(builder.creator, StandardSystemProperty.USER_NAME.value()));

        options = new JsonLdOptions(MoreObjects.firstNonNull(builder.base, ""));
        options.setExpandContext(MoreObjects.firstNonNull(builder.expandContext, Bdio.Context.DEFAULT.toString()));
        options.setDocumentLoader(builder.documentLoader.build());
    }

    // TODO Does having this make sense?
    public void reset() {
        // Complete the subject before tossing it
        data.onCompleted();

        // Create a new subject
        data = PublishSubject.create();
    }

    /**
     * Allows you to consume the raw JSON-LD graph elements from this document.
     */
    public Observable<Object> asObservable() {
        return data.asObservable();
    }

    /**
     * Frames each of the JSON-LD graph elements using the supplied frame.
     *
     * @see com.github.jsonldjava.core.JsonLdProcessor#frame(Object, Object, JsonLdOptions)
     */
    public Observable<Map<String, Object>> frame(Object frame) {
        return asObservable()
                .map(new Func1<Object, Object>() {
                    @Override
                    public Object call(Object input) {
                        // Framing does not work on named graphs so we need to pull just the graph nodes out
                        // https://github.com/jsonld-java/jsonld-java/issues/109
                        Object graph = OperatorGraphNodes.getGraph(input);
                        return graph != null ? graph : input;
                        // if (input instanceof Map<?, ?> && ((Map<?, ?>)
                        // input).containsKey(JsonLdKeyword.graph.toString())) {
                        // Map<String, Object> result = new LinkedHashMap<>();
                        // result.put("@id", "@default");
                        // result.put(JsonLdKeyword.graph.toString(), ((Map<?, ?>)
                        // input).get(JsonLdKeyword.graph.toString()));
                        // return result;
                        // }
                        // return input;
                    }
                })
                .compose(RxJsonLdProcessor.frame(frame, options));
        // TODO Restore the graph identifier from the metadata?
    }

    /**
     * Expands each of the JSON-LD graph elements.
     *
     * @see com.github.jsonldjava.core.JsonLdProcessor#expand(Object, JsonLdOptions)
     */
    public Observable<List<Object>> expand() {
        return asObservable().compose(RxJsonLdProcessor.expand(options));
    }

    /**
     * Writes the BDIO data coming into this document out to the supplied byte stream.
     */
    public BdioDocument writeTo(OutputStream out) {
        Observable.merge(expand()
                .lift(OperatorGraphNodes.instance()))
                .subscribe(new BdioSubscriber(metadata, out));
        return this;
    }

    /**
     * Allows you to add JSON-LD graph elements to this document.
     */
    public Subscriber<Object> asSubscriber() {
        return Subscribers.from(data);
    }

    /**
     * Allows you to add JSON-LD nodes to this document.
     */
    public Subscriber<Map<String, Object>> asNodeSubscriber() {
        // TODO Is this right?
        PublishSubject<Map<String, Object>> nodes = PublishSubject.create();
        nodes.compose(fromNodes()).subscribe(asSubscriber());
        return Subscribers.from(nodes);
    }

    /**
     * A transformer for converting a sequence of nodes into a graph.
     */
    private Transformer<Map<String, Object>, Object> fromNodes() {
        final Map<String, Object> header = new LinkedHashMap<>();
        header.putAll(metadata);
        header.put(JsonLdKeyword.graph.toString(), new ArrayList<>(0));
        return new Transformer<Map<String, Object>, Object>() {
            @Override
            public Observable<Object> call(Observable<Map<String, Object>> nodes) {
                // NOTE: The buffer size choice was arbitrary, we just want to ensure we never
                // hit the BDIO imposed 16MB limit on the serialized size
                return nodes.buffer(1000).map(new Func1<List<Map<String, Object>>, Object>() {
                    @Override
                    public Object call(List<Map<String, Object>> graph) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put(JsonLdKeyword.id.toString(), metadata.id());
                        // TODO Sort the graph while we have it in memory here?
                        result.put(JsonLdKeyword.graph.toString(), graph);
                        return result;
                    }
                }).startWith(header);
            }
        };
    }

    /**
     * Reads BDIO data into this document from the supplied byte stream.
     */
    public BdioDocument read(InputStream in) {
        // TODO Ignore onComplete from the OnSubscribe? Need to explicitly complete the subject?
        Observable.create(new BdioOnSubscribe(in)).subscribe(asSubscriber());
        return this;
    }

    /**
     * A builder for constructing BDIO documents.
     */
    public static final class Builder {

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
         * Creates a new BDIO document from the current state of this builder.
         */
        public BdioDocument build() {
            return new BdioDocument(this);
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
