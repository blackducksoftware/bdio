/*
 * Copyright 2018 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2.reactor;

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

import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;

/**
 * Project Reactor implementation of the BDIO document API.
 *
 * @author jgustie
 */
public class ReactorBdioDocument extends BdioDocument {

    public ReactorBdioDocument(BdioContext context) {
        super(context);
    }

    @Override
    public Flux<Object> read(InputStream in) {
        return Flux.generate(
                () -> EmitterFactory.newEmitter(context(), in),
                (parser, emitter) -> {
                    parser.emit(emitter::next, emitter::error, emitter::complete);
                    return parser;
                },
                Emitter::dispose);
    }

    @Override
    public Subscriber<Object> write(BdioMetadata metadata, StreamSupplier entryStreams) {
        EmitterProcessor<Object> data = EmitterProcessor.create();

        jsonLd(data)
                .expand()
                .flatMapIterable(BdioDocument::toGraphNodes)
                .subscribe(new BdioSubscriber(metadata, entryStreams, t -> {})); // TODO Generic error handler?

        return data;
    }

    @Override
    public ReactorJsonLdProcessing jsonLd(Publisher<Object> inputs) {
        return new ReactorJsonLdProcessing(Flux.from(inputs), context().options());
    }

    @Override
    public Flux<BdioMetadata> metadata(Publisher<Object> inputs) {
        return jsonLd(Flux.from(inputs).map(BdioDocument::toMetadata))
                .expand().flatMapIterable(BdioDocument::unfoldExpand)
                .onErrorResume(t -> Flux.just(new BdioMetadata(t)))
                .reduce(new BdioMetadata(), BdioMetadata::merge)
                .flux();
    }

}
