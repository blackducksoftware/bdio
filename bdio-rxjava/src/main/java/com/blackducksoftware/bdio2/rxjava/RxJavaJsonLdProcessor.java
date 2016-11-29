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
package com.blackducksoftware.bdio.rx;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;

import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Func1;

/**
 * Implementation of the {@code JsonLdProcessor} high level API for RxJava.
 *
 * @author jgustie
 */
public class RxJsonLdProcessor {

    // Note that the gratuitous use of Object stems from the JSON-LD API itself: often times it
    // passes Object when it will accept String, Map<String, Object> or List<Object>.

    /**
     * Base class used to invoke the JSON-LD processor as a transformation over emitted elements.
     */
    private static abstract class JsonLdProcessorTransformer<R> implements Transformer<Object, R> {

        private final JsonLdOptions options;

        protected JsonLdProcessorTransformer(JsonLdOptions options) {
            this.options = Objects.requireNonNull(options);
        }

        @Override
        public final Observable<R> call(Observable<Object> inputs) {
            // Use flat map so we can propagate the checked JSON-LD error cleanly
            return inputs.flatMap(new Func1<Object, Observable<R>>() {
                @Override
                public Observable<R> call(Object input) {
                    try {
                        return Observable.just(JsonLdProcessorTransformer.this.call(input, options));
                    } catch (JsonLdError e) {
                        return Observable.error(e);
                    }
                }
            });
        }

        /**
         * Implemented by subclasses to invoke the appropriate method on the {@code JsonLdProcessor}.
         */
        protected abstract R call(Object input, JsonLdOptions options) throws JsonLdError;
    }

    /**
     * @see RxJsonLdProcessor#compact(Object, JsonLdOptions)
     */
    private static class CompactTransformer extends JsonLdProcessorTransformer<Map<String, Object>> {

        private final Object context;

        private CompactTransformer(Object context, JsonLdOptions options) {
            super(options);
            this.context = Objects.requireNonNull(context);
        }

        @Override
        protected Map<String, Object> call(Object input, JsonLdOptions options) throws JsonLdError {
            return JsonLdProcessor.compact(input, context, options);
        }
    }

    /**
     * @see RxJsonLdProcessor#expand(JsonLdOptions)
     */
    private static class ExpandTransformer extends JsonLdProcessorTransformer<List<Object>> {
        private ExpandTransformer(JsonLdOptions options) {
            super(options);
        }

        @Override
        protected List<Object> call(Object input, JsonLdOptions options) throws JsonLdError {
            return JsonLdProcessor.expand(input, options);
        }
    }

    /**
     * @see RxJsonLdProcessor#flatten(Object, JsonLdOptions)
     */
    private static class FlattenTransformer extends JsonLdProcessorTransformer<Object> {
        @Nullable
        private final Object context;

        private FlattenTransformer(@Nullable Object context, JsonLdOptions options) {
            super(options);
            this.context = context;
        }

        @Override
        protected Object call(Object input, JsonLdOptions options) throws JsonLdError {
            return JsonLdProcessor.flatten(input, context, options);
        }
    }

    /**
     * @see RxJsonLdProcessor#frame(Object, JsonLdOptions)
     */
    private static class FrameTransformer extends JsonLdProcessorTransformer<Map<String, Object>> {
        private final Object frame;

        private FrameTransformer(Object frame, JsonLdOptions options) {
            super(options);
            this.frame = Objects.requireNonNull(frame);
        }

        @Override
        protected Map<String, Object> call(Object input, JsonLdOptions options) throws JsonLdError {
            return JsonLdProcessor.frame(input, frame, options);
        }
    }

    /**
     * Compacts each element in the sequence according to the steps in the JSON-LD Compaction algorithm.
     *
     * @see JsonLdProcessor#compact(Object, Object, JsonLdOptions)
     */
    public static Transformer<Object, Map<String, Object>> compact(Object context, JsonLdOptions options) {
        return new CompactTransformer(context, options);
    }

    /**
     * Expands each element in the sequence according to the steps in the Expansion algorithm.
     *
     * @see JsonLdProcessor#expand(Object, JsonLdOptions)
     */
    public static Transformer<Object, List<Object>> expand(JsonLdOptions options) {
        return new ExpandTransformer(options);
    }

    /**
     * Flattens each element in the sequence and compacts it using the passed context according to the steps in the
     * Flattening algorithm.
     *
     * @see JsonLdProcessor#flatten(Object, Object, JsonLdOptions)
     */
    public static Transformer<Object, Object> flatten(@Nullable Object context, JsonLdOptions options) {
        return new FlattenTransformer(context, options);
    }

    /**
     * Frames each element in the sequence using the frame according to the steps in the Framing Algorithm.
     * 
     * @see JsonLdProcessor#frame(Object, Object, JsonLdOptions)
     */
    public static Transformer<Object, Map<String, Object>> frame(Object frame, JsonLdOptions options) {
        return new FrameTransformer(frame, options);
    }

}
