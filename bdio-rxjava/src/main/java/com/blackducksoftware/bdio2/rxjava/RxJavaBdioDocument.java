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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioOptions;
import com.blackducksoftware.bdio2.BdioSubscriber;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.bdio2.Emitter;
import com.blackducksoftware.bdio2.EmitterFactory;

import io.reactivex.Flowable;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.processors.PublishProcessor;

/**
 * RxJava implementation of the BDIO document API.
 *
 * @author jgustie
 */
public final class RxJavaBdioDocument extends BdioDocument {

    public RxJavaBdioDocument(BdioOptions options) {
        super(options);
    }

    @Override
    public RxJavaJsonLdProcessing jsonLd(Publisher<Object> inputs) {
        return new RxJavaJsonLdProcessing(Flowable.fromPublisher(inputs), options());
    }

    @SuppressWarnings("CheckReturnValue")
    @Override
    public RxJavaJsonLdProcessing read(InputStream in, Consumer<BdioMetadata> metadataConsumer) {
        Flowable<Object> entries = Flowable.generate(
                () -> EmitterFactory.newEmitter(in),
                (parser, emitter) -> {
                    parser.emit(emitter::onNext, emitter::onError, emitter::onComplete);
                },
                Emitter::dispose)

                // Make IOExceptions unchecked
                .onErrorResumeNext(t -> {
                    if (t instanceof IOException) {
                        return Flowable.error(new UncheckedIOException((IOException) t));
                    } else {
                        return Flowable.error(t);
                    }
                })

                // TODO Should this happen on the I/O scheduler?
                .publish().autoConnect(2);

        entries.map(BdioDocument::toGraphMetadata)
                .reduce(new BdioMetadata(), BdioMetadata::merge)
                // TODO Have our own default error handler that defaults to the RxJavaPlugins.onError?
                // TODO What do we do with the disposable?
                .subscribe(metadataConsumer::accept);

        return jsonLd(entries);
    }

    @Override
    public Subscriber<Object> write(BdioMetadata metadata, StreamSupplier entryStreams) {
        PublishProcessor<Object> data = PublishProcessor.create();

        jsonLd(data)
                .expand()
                .flatMapIterable(BdioDocument::toGraphNodes)
                .subscribe(new BdioSubscriber(metadata, entryStreams, RxJavaPlugins::onError));

        // TODO Wrap/hide?
        return data;
    }

}
