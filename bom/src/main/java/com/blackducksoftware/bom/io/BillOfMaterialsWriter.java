/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bom.io;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;

import rx.Subscriber;
import rx.functions.Action1;

import com.blackducksoftware.bom.Node;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteSink;

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
