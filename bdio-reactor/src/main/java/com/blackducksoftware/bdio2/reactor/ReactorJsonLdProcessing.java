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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.blackducksoftware.bdio2.BdioDocument;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;

import reactor.core.publisher.Flux;

/**
 * Implementation of the {@link JsonLdProcessor} high level API for Project Reactor.
 *
 * @author jgustie
 */
public class ReactorJsonLdProcessing implements BdioDocument.JsonLdProcessing {

    // Note that the gratuitous use of Object stems from the JSON-LD API itself: often times it
    // passes Object when it will accept String, Map<String, Object> or List<Object>.

    private final Flux<Object> entries;

    private final JsonLdOptions options;

    ReactorJsonLdProcessing(Flux<Object> entries, JsonLdOptions options) {
        this.entries = Objects.requireNonNull(entries);
        this.options = Objects.requireNonNull(options);
    }

    @Override
    public Flux<Object> identity() {
        return entries;
    }

    @Override
    public Flux<Map<String, Object>> compact(Object context) {
        return identity().compose(inputs -> inputs.flatMap(input -> {
            try {
                return Flux.just(JsonLdProcessor.compact(input, context, options));
            } catch (JsonLdError e) {
                return Flux.error(e);
            }
        }));
    }

    @Override
    public Flux<List<Object>> expand() {
        return identity().compose(inputs -> inputs.flatMap(input -> {
            try {
                // TODO The RxJava implementation has workarounds, why are they not here?
                return Flux.just(JsonLdProcessor.expand(input, options));
            } catch (JsonLdError e) {
                return Flux.error(e);
            }
        }));
    }

    @Override
    public Flux<Object> flatten(Object context) {
        return identity().compose(inputs -> inputs.flatMap(input -> {
            try {
                return Flux.just(JsonLdProcessor.flatten(input, context, options));
            } catch (JsonLdError e) {
                return Flux.error(e);
            }
        }));
    }

    @Override
    public Flux<Map<String, Object>> frame(Object frame) {
        return identity().compose(inputs -> inputs.flatMap(input -> {
            try {
                // TODO The RxJava implementation has multiple workarounds, why are they not here?
                return Flux.just(JsonLdProcessor.frame(input, frame, options));
            } catch (JsonLdError e) {
                return Flux.error(e);
            }
        }));
    }

}
