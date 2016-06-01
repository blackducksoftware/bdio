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
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import com.blackducksoftware.bdio.BlackDuckTerm;
import com.blackducksoftware.bdio.BlackDuckType;
import com.blackducksoftware.bdio.Node;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdApi;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.google.common.io.CharSource;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observables.AbstractOnSubscribe;
import rx.observables.AbstractOnSubscribe.SubscriptionState;

/**
 * A simple reader for consuming a JSON formatted Bill of Materials from a source of characters.
 *
 * @author jgustie
 */
public class BdioReader implements Closeable {

    /**
     * We want to read nodes out as a map we can pass into the context.
     */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    /**
     * A reactive termination action used to close a reader.
     */
    private static final Action1<BdioReader> ON_TERMINATED = new Action1<BdioReader>() {
        @Override
        public void call(BdioReader reader) {
            try {
                reader.close();
            } catch (IOException e) {
                return;
            }
        }
    };

    /**
     * Produces a reactive subscription action that creates a new reader.
     */
    private static Func1<Subscriber<?>, BdioReader> onSubscribe(final LinkedDataContext context, final CharSource source) {
        return new Func1<Subscriber<?>, BdioReader>() {
            @Override
            public BdioReader call(Subscriber<?> t) {
                try {
                    return new BdioReader(context, source.openBufferedStream());
                } catch (IOException e) {
                    t.onError(e);
                    return null;
                }
            }
        };
    }

    /**
     * Attempts to locate the specification version from a list of raw deserialized JSON objects (i.e. a list of
     * {@code Map<String, Object>}).
     */
    @Nullable
    private static String scanForSpecVersion(List<?> list) {
        final String fragment = BlackDuckType.BILL_OF_MATERIALS.toUri().getFragment();
        for (Object o : list) {
            if (o instanceof Map<?, ?>) {
                // Check to see if the specification version is defined
                Object specVersion = ((Map<?, ?>) o).get(BlackDuckTerm.SPEC_VERSION.toString());
                if (specVersion instanceof String) {
                    return (String) specVersion;
                }

                // There should only be one BillOfMaterials node so if we see it, this is v0 file
                Object type = ((Map<?, ?>) o).get(JsonLdKeyword.TYPE.toString());
                if (type.equals(fragment) || (type instanceof Collection<?> && ((Collection<?>) type).contains(BlackDuckType.BILL_OF_MATERIALS.toString()))) {
                    break;
                }
            }
        }
        return null;
    }

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

    public BdioReader(LinkedDataContext context, Reader in) throws IOException {
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
        checkNotNull(context);
        checkNotNull(source);
        return AbstractOnSubscribe.create(new Action1<SubscriptionState<Node, BdioReader>>() {
            @Override
            public void call(SubscriptionState<Node, BdioReader> t) {
                // Iterate over the nodes in the file as we see them
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
        }, onSubscribe(context, source), ON_TERMINATED).toObservable();
    }

    /**
     * Reads the entire BOM into memory. This will normalize the data so that each node is "complete",
     * however this requires a significant amount of up front resources: both in terms of memory and CPU.
     */
    public static Observable<Node> readFully(final LinkedDataContext context, CharSource source) {
        // We end up with an Observable<Observable<Object>> so use merge to flatten it out
        return Observable.merge(AbstractOnSubscribe.create(new Action1<SubscriptionState<Observable<Object>, BdioReader>>() {
            @Override
            public void call(SubscriptionState<Observable<Object>, BdioReader> t) {
                try {
                    // Parse the whole input
                    List<?> input = t.state().jp.readValueAs(List.class);
                    String specVersion = scanForSpecVersion(input);

                    // THIS IS THE EXPENSIVE BIT...

                    // Expand the input and the frame; frame the results and compact it back down
                    JsonLdOptions opts = new JsonLdOptions();
                    opts.setExpandContext(context.newContextForReading(specVersion).serialize());
                    List<Object> expandedInput = JsonLdProcessor.expand(input, opts);
                    List<Object> expandedFrame = JsonLdProcessor.expand(context.newImportFrame(), opts);
                    List<Object> framed = new JsonLdApi(opts).frame(expandedInput, expandedFrame);
                    List<?> compacted = (List<?>) JsonLdProcessor.compact(framed, context.serialize(), opts).get("@graph");
                    // TODO How do we eliminate the blank node identifiers introduced during expansion?

                    // We only emit a single element: an observable over the raw objects in the graph
                    t.onNext(Observable.from(compacted));
                    t.onCompleted();
                } catch (IOException | JsonLdError e) {
                    t.onError(e);
                }
            }
        }, onSubscribe(context, source), ON_TERMINATED).toObservable()).flatMap(new Func1<Object, Observable<Node>>() {
            @Override
            public Observable<Node> call(Object nodeMap) {
                // Convert the raw JSON to Node instances, but only if each element is actually a Map
                // (e.g. scalars in the graph are safely ignored)
                if (nodeMap instanceof Map<?, ?>) {
                    return Observable.just(context.expandToNode((Map<?, ?>) nodeMap));
                } else {
                    return Observable.empty();
                }
            }
        });
    }

}
