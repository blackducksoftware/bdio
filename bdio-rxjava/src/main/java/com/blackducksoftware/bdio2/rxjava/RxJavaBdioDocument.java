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
package com.blackducksoftware.bdio2.rxjava;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.reactivestreams.Subscriber;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioSubscriber;
import com.blackducksoftware.bdio2.Emitter;
import com.blackducksoftware.bdio2.EmitterFactory;
import com.blackducksoftware.common.io.ExtraIO;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;

import io.reactivex.Flowable;
import io.reactivex.FlowableTransformer;
import io.reactivex.Single;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;

/**
 * RxJava implementation of the BDIO document API.
 *
 * @author jgustie
 */
public final class RxJavaBdioDocument extends BdioDocument {

    /**
     * Returns more specific types then the {@link BdioDocument.JsonLdProcessing}.
     */
    public interface RxJavaJsonLdProcessing extends BdioDocument.JsonLdProcessing {
        @Override
        Flowable<Map<String, Object>> compact(Object context);

        @Override
        Flowable<List<Object>> expand();

        @Override
        Flowable<Object> flatten(@Nullable Object context);

        @Override
        Flowable<Map<String, Object>> frame(Object frame);
    }

    private final PublishProcessor<Object> data;

    private final Single<BdioMetadata> metadata;

    public RxJavaBdioDocument(Builder builder) {
        super(builder);

        // Create a new processor to facilitate the flow of BDIO data
        data = PublishProcessor.create();

        // Each "entry" may contain metadata which needs to be collected
        metadata = data
                .map(this::extractMetadata)
                .reduce(new BdioMetadata(), BdioMetadata::merge)
                .cache();

        // Make sure we don't miss any metadata
        metadata.subscribe();
    }

    @Override
    public FlowableProcessor<Object> processor() {
        return data;
    }

    @Override
    public Subscriber<Map<String, Object>> asNodeSubscriber() {
        PublishProcessor<Map<String, Object>> nodes = PublishProcessor.create();

        // TODO How do we eliminate the need for guess work?
        // NOTE: The buffer size choice was arbitrary, we just want to ensure we never
        // hit the BDIO imposed 16MB limit on the serialized size
        nodes.buffer(1000)
                .defaultIfEmpty(new ArrayList<>(0))
                .subscribe(processor());

        // TODO Wrap/hide?
        return nodes;
    }

    @Override
    public RxJavaBdioDocument metadata(Consumer<BdioMetadata> metadataConsumer) {
        metadata.subscribe(metadataConsumer::accept);
        return this;
    }

    @Override
    public RxJavaBdioDocument takeFirstMetadata(Consumer<BdioMetadata> metadataConsumer) {
        data.take(1).map(this::extractMetadata).map(BdioMetadata::new).subscribe(metadataConsumer::accept);
        return this;
    }

    @Override
    public RxJavaJsonLdProcessing jsonld() {
        return new RxJavaJsonLdProcessing() {
            @Override
            public Flowable<Map<String, Object>> frame(Object frame) {
                // TODO Restore the graph label?
                return processor().map(x -> dropGraphLabel(x)).compose(RxJavaJsonLdProcessor.frame(frame, options()));
            }

            @Override
            public Flowable<Object> flatten(Object context) {
                return processor().compose(RxJavaJsonLdProcessor.flatten(context, options()));
            }

            @Override
            public Flowable<List<Object>> expand() {
                return processor().compose(RxJavaJsonLdProcessor.expand(options()));
            }

            @Override
            public Flowable<Map<String, Object>> compact(Object context) {
                return processor().compose(RxJavaJsonLdProcessor.compact(context, options()));
            }

            @Override
            public JsonLdOptions options() {
                return RxJavaBdioDocument.this.options();
            }
        };
    }

    @Override
    public RxJavaBdioDocument writeToFile(BdioMetadata metadata, OutputStream out) {
        jsonld().expand().compose(withoutMetadata())
                .subscribe(new BdioSubscriber(metadata, ExtraIO.buffer(out)));
        return this;
    }

    /**
     * Transformer to obtain a list of JSON-LD nodes, discarding any metadata (including the named graph label).
     */
    public <T> FlowableTransformer<T, Map<String, Object>> withoutMetadata() {
        return entry -> entry.flatMap(input -> {
            try {
                return Flowable.fromIterable(extractNodes(input));
            } catch (JsonLdError e) {
                return Flowable.error(e);
            }
        });
    }

    @Override
    public RxJavaBdioDocument read(InputStream in) {
        // TODO Should this happen on the I/O scheduler?
        fromInputStream(in).subscribe(processor());
        return this;
    }

    /**
     * Returns a flowable representing the BDIO entries from the supplied input stream. Generally it is preferable to
     * use {@link #read(InputStream)}, however it may be necessary to manipulate the sequence prior to subscribing.
     */
    public Flowable<Object> fromInputStream(InputStream in) {
        return Flowable.generate(() -> EmitterFactory.newEmitter(in),
                (parser, emitter) -> {
                    // FIXME: Braces shouldn't be necessary, Eclipse thinks it is ambiguous, it isn't...
                    parser.emit(emitter::onNext, emitter::onError, emitter::onComplete);
                }, Emitter::dispose);
    }

}
