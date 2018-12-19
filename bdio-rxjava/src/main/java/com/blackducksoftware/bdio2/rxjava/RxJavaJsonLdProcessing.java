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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.reactivestreams.Publisher;

import com.blackducksoftware.bdio2.BdioDocument;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;

import io.reactivex.Flowable;
import io.reactivex.FlowableTransformer;

/**
 * Implementation of the {@link JsonLdProcessor} high level API for RxJava.
 *
 * @author jgustie
 */
public class RxJavaJsonLdProcessing implements BdioDocument.JsonLdProcessing {

    // Note that the gratuitous use of Object stems from the JSON-LD API itself: often times it
    // passes Object when it will accept String, Map<String, Object> or List<Object>.

    /**
     * Base class used to invoke the JSON-LD processor as a transformation over emitted elements.
     */
    private static abstract class JsonLdProcessorTransformer<R> implements FlowableTransformer<Object, R> {

        private final JsonLdOptions options;

        protected JsonLdProcessorTransformer(JsonLdOptions options) {
            this.options = Objects.requireNonNull(options);
        }

        @Override
        public final Publisher<R> apply(Flowable<Object> inputs) {
            // Use flat map so we can propagate the checked JSON-LD error cleanly
            return inputs.flatMap(input -> {
                try {
                    return Flowable.just(applyOnce(input, options));
                } catch (JsonLdError e) {
                    return Flowable.error(e);
                }
            });
            // TODO Do we need to handle handle the default if empty case?
        }

        /**
         * Implemented by subclasses to invoke the appropriate method on the {@code JsonLdProcessor}.
         *
         * @param input
         *            the input to the JSON-LD algorithm, typically a list of nodes or a named graph
         * @param options
         *            the JSON-LD options, such as the base IRI
         */
        protected abstract R applyOnce(Object input, JsonLdOptions options) throws JsonLdError;

    }

    /**
     * @see RxJavaJsonLdProcessing#compact(Object)
     */
    private static class CompactTransformer extends JsonLdProcessorTransformer<Map<String, Object>> {
        private final Object context;

        private CompactTransformer(Object context, JsonLdOptions options) {
            super(options);
            this.context = Objects.requireNonNull(context);
        }

        @Override
        protected Map<String, Object> applyOnce(Object input, JsonLdOptions options) throws JsonLdError {
            return JsonLdProcessor.compact(input, context, options);
        }
    }

    /**
     * @see RxJavaJsonLdProcessing#expand()
     */
    private static class ExpandTransformer extends JsonLdProcessorTransformer<List<Object>> {
        private ExpandTransformer(JsonLdOptions options) {
            super(options);
        }

        @Override
        protected List<Object> applyOnce(Object input, JsonLdOptions options) throws JsonLdError {
            if (input instanceof Map<?, ?>) {
                Map<?, ?> inputMap = (Map<?, ?>) input;
                if (inputMap.size() == 1 && inputMap.containsKey(JsonLdConsts.ID)) {
                    // Preserve inputs that are a single identifier
                    List<Object> result = new ArrayList<>();
                    result.add(new LinkedHashMap<>(inputMap));
                    return result;
                }
            }
            return JsonLdProcessor.expand(input, options);
        }
    }

    /**
     * @see RxJavaJsonLdProcessing#flatten(Object, JsonLdOptions)
     */
    private static class FlattenTransformer extends JsonLdProcessorTransformer<Object> {
        @Nullable
        private final Object context;

        private FlattenTransformer(@Nullable Object context, JsonLdOptions options) {
            super(options);
            this.context = context;
        }

        @Override
        protected Object applyOnce(Object input, JsonLdOptions options) throws JsonLdError {
            return JsonLdProcessor.flatten(input, context, options);
        }
    }

    /**
     * @see RxJavaJsonLdProcessing#frame(Object)
     */
    private static class FrameTransformer extends JsonLdProcessorTransformer<Map<String, Object>> {
        private final Object frame;

        private FrameTransformer(Object frame, JsonLdOptions options) {
            super(options);
            this.frame = Objects.requireNonNull(frame);
        }

        /**
         * Framing does not work on named graphs so we need to pull just the graph nodes out.
         *
         * @see <a href="https://github.com/jsonld-java/jsonld-java/issues/109">#109</a>
         */
        @Override
        protected Map<String, Object> applyOnce(Object input, JsonLdOptions options) throws JsonLdError {
            if (input instanceof Map<?, ?>) {
                Map<?, ?> inputMap = (Map<?, ?>) input;
                if (inputMap.containsKey(JsonLdConsts.GRAPH)) {
                    // Only pass the context and the graph itself to the framing implementation
                    Map<String, Object> alternateInput = new LinkedHashMap<>();
                    alternateInput.put(JsonLdConsts.CONTEXT, inputMap.get(JsonLdConsts.CONTEXT));
                    alternateInput.put(JsonLdConsts.GRAPH, inputMap.get(JsonLdConsts.GRAPH));
                    alternateInput.values().removeIf(Objects::isNull);
                    input = alternateInput;
                }
            }

            // There is a bug in the JSON-LD API where an empty list causes an NPE
            if (BdioDocument.toGraphNodes(input).isEmpty()) {
                Map<String, Object> emptyResult = new HashMap<>(1);
                emptyResult.put(JsonLdConsts.GRAPH, new ArrayList<>(0));
                return emptyResult;
            } else {
                return JsonLdProcessor.frame(input, frame, options);
            }
        }
    }

    private final Flowable<Object> entries;

    private final JsonLdOptions options;

    RxJavaJsonLdProcessing(Flowable<Object> entries, JsonLdOptions options) {
        this.entries = Objects.requireNonNull(entries);
        this.options = Objects.requireNonNull(options);
    }

    @Override
    public Flowable<Object> identity() {
        return entries;
    }

    @Override
    public Flowable<Map<String, Object>> frame(Object frame) {
        return identity().compose(new FrameTransformer(frame, options));
    }

    @Override
    public Flowable<Object> flatten(Object context) {
        return identity().compose(new FlattenTransformer(context, options));
    }

    @Override
    public Flowable<List<Object>> expand() {
        return identity().compose(new ExpandTransformer(options));
    }

    @Override
    public Flowable<Map<String, Object>> compact(Object context) {
        return identity().compose(new CompactTransformer(context, options));
    }

}
