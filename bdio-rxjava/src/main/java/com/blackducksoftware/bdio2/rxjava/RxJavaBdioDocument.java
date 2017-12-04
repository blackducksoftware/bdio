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
    public Flowable<Object> read(InputStream in) {
        // TODO Should this happen on the I/O scheduler?
        return Flowable.generate(
                () -> EmitterFactory.newEmitter(options(), in),
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
                });
    }

    @Override
    public Subscriber<Object> write(BdioMetadata metadata, StreamSupplier entryStreams) {
        PublishProcessor<Object> data = PublishProcessor.create();

        jsonLd(data)
                .expand()
                .flatMapIterable(BdioDocument::toGraphNodes)
                .subscribe(new BdioSubscriber(metadata, entryStreams, RxJavaPlugins::onError));

        // TODO How can we use FlowableHide.HideSubscriber?
        return data;
    }

    @Override
    public RxJavaJsonLdProcessing jsonLd(Publisher<Object> inputs) {
        return new RxJavaJsonLdProcessing(Flowable.fromPublisher(inputs), options());
    }

    @Override
    public Flowable<BdioMetadata> metadata(Publisher<Object> inputs) {
        return Flowable.fromPublisher(inputs)
                .map(BdioDocument::toGraphMetadata)
                .onErrorResumeNext(Flowable.empty())
                .reduce(new BdioMetadata(), BdioMetadata::merge)
                .toFlowable();
    }

}
