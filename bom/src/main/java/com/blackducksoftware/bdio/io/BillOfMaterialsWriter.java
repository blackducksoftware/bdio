/*
 * Copyright 2015 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio.io;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;

import com.blackducksoftware.bdio.Node;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteSink;

import rx.Subscriber;
import rx.functions.Action1;

/**
 * A simple writer for producing a JSON formatted Bill of Materials from a source of node instances.
 *
 * @author jgustie
 */
public class BillOfMaterialsWriter implements Closeable, Flushable {

    /**
     * The linked data context.
     */
    private final LinkedDataContext context;

    /**
     * The output stream backed generator used to produce actual JSON.
     */
    private final JsonGenerator jgen;

    /**
     * Create a new writer that produces UTF-8 encoded JSON.
     */
    public BillOfMaterialsWriter(LinkedDataContext context, OutputStream out) throws IOException {
        this.context = checkNotNull(context);

        // Setup the JSON generator
        jgen = new ObjectMapper().getFactory().createGenerator(out);
        jgen.useDefaultPrettyPrinter();
        jgen.disable(Feature.AUTO_CLOSE_TARGET);

        // Start the list of nodes
        jgen.writeStartArray();
    }

    /**
     * Just keep calling this.
     */
    public BillOfMaterialsWriter write(Node node) throws IOException {
        jgen.writeObject(context.compact(checkNotNull(node)));
        return this;
    }

    @Override
    public void flush() throws IOException {
        jgen.flush();
    }

    @Override
    public void close() throws IOException {
        // Close the list (and object enclosing the context if necessary)
        jgen.writeEndArray();
        jgen.close();
    }

    /**
     * Returns a subscriber from a byte sink.
     */
    public static Subscriber<Node> store(final LinkedDataContext context, final ByteSink sink, final Action1<Throwable> onError) {
        checkNotNull(context);
        checkNotNull(sink);
        checkNotNull(onError);
        return new Subscriber<Node>() {
            private BillOfMaterialsWriter writer;

            @Override
            public void onStart() {
                try {
                    writer = new BillOfMaterialsWriter(context, sink.openBufferedStream());
                } catch (IOException e) {
                    onError(e);
                }
            }

            @Override
            public void onNext(Node node) {
                try {
                    writer.write(node);
                } catch (IOException e) {
                    onError(e);
                }
            }

            @Override
            public void onCompleted() {
                try {
                    writer.close();
                } catch (IOException e) {
                    onError(e);
                }
            }

            @Override
            public void onError(Throwable e) {
                onError.call(e);
            }
        };
    }
}
