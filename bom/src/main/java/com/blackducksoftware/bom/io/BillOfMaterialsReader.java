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

import java.io.IOException;
import java.io.Reader;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observables.AbstractOnSubscribe;
import rx.observables.AbstractOnSubscribe.SubscriptionState;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.Node;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharSource;

/**
 * A simple reader for consuming a JSON formatted Bill of Materials from a source of characters.
 *
 * @author jgustie
 */
public class BillOfMaterialsReader implements AutoCloseable {

    /**
     * We want to read nodes out as a map we can pass into the context.
     */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    /**
     * The current linked data context. If we are reading and we encounter a
     * {@linkplain BlackDuckType#BILL_OF_MATERIALS BOM} node, we will use the specification version to replace this
     * context. This means that the order or appearance of the BOM node in the graph is important: it must appear
     * sufficiently early in the graph such that only data recognizable from the initial specification of the format
     * appear before the specification version. Ideally the BOM node will always be the first node in the graph.
     * Additionally, this assumes only backwards compatible changes are made to the BOM node.
     */
    @GuardedBy("this")
    private LinkedDataContext context;

    /**
     * The reader backed parser used to read actual JSON.
     */
    private final JsonParser jp;

    public BillOfMaterialsReader(LinkedDataContext context, Reader in) throws IOException {
        // Store the context, we may need to overwrite it later
        this.context = checkNotNull(context);

        // Setup the JSON parser
        jp = new ObjectMapper().getFactory().createParser(in);

        // Start by finding the list of nodes
        if (jp.nextToken() != JsonToken.START_ARRAY) {
            throw new IOException("expected input to start with an array");
        }
    }

    /**
     * Returns the current context used for reading data. Note that the context can change from the initial value passed
     * used to construct this reader, for example if we are reading data from an older version of the specification.
     */
    public final synchronized LinkedDataContext context() {
        return context;
    }

    /**
     * Just keep calling this until it returns {@code null}.
     */
    @Nullable
    public synchronized Node read() throws IOException {
        Node result = null;
        if (jp.nextToken() != JsonToken.END_ARRAY) {
            Map<String, Object> nodeMap = jp.readValueAs(MAP_TYPE);
            result = context.expandToNode(nodeMap);
            if (result.types().contains(BlackDuckType.BILL_OF_MATERIALS)) {
                // Replace the current context based on the specification version
                // NOTE: if the specVersion is absent, it means it is the initial version
                Object value = result.data().get(BlackDuckTerm.SPEC_VERSION);
                String specVersion = value != null ? value.toString() : null;
                context = context.newContextForReading(specVersion);
            }
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        jp.close();
    }

    /**
     * Returns an observable from a character source.
     */
    public static Observable<Node> open(final LinkedDataContext context, final CharSource source) {
        return AbstractOnSubscribe.create(new Action1<SubscriptionState<Node, BillOfMaterialsReader>>() {
            @Override
            public void call(SubscriptionState<Node, BillOfMaterialsReader> t) {
                try {
                    Node node = t.state().read();
                    if (node != null) {
                        t.onNext(node);
                    } else {
                        t.onCompleted();
                    }
                } catch (IOException e) {
                    t.onError(e);
                }
            }
        }, new Func1<Subscriber<?>, BillOfMaterialsReader>() {
            @Override
            public BillOfMaterialsReader call(Subscriber<?> t) {
                try {
                    return new BillOfMaterialsReader(context, source.openBufferedStream());
                } catch (IOException e) {
                    t.onError(e);
                    return null;
                }
            }
        }, new Action1<BillOfMaterialsReader>() {
            @Override
            public void call(BillOfMaterialsReader reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // TODO ???
                }
            }
        }).toObservable();
    }

}
