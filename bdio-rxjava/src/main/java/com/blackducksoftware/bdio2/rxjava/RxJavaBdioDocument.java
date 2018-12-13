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
package com.blackducksoftware.bdio2.rxjava;

import java.io.InputStream;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioMetadata;
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
public class RxJavaBdioDocument extends BdioDocument {

    public RxJavaBdioDocument(BdioContext context) {
        super(context);
    }

    @Override
    public Flowable<Object> read(InputStream in) {
        return Flowable.generate(
                () -> EmitterFactory.newEmitter(context(), in),
                (parser, emitter) -> {
                    parser.emit(emitter::onNext, emitter::onError, emitter::onComplete);
                },
                Emitter::dispose);
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
        return new RxJavaJsonLdProcessing(Flowable.fromPublisher(inputs), context().jsonLdOptions());
    }

    @Override
    public Flowable<BdioMetadata> metadata(Publisher<Object> inputs) {
        return jsonLd(Flowable.fromPublisher(inputs).map(BdioDocument::toMetadata))
                .expand().flatMapIterable(BdioDocument::unfoldExpand)
                .onErrorReturn(BdioMetadata::new)
                .reduce(new BdioMetadata(), BdioMetadata::merge)
                .toFlowable();
    }

}
