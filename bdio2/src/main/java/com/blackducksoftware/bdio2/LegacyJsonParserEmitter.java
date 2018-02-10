/*
 * Copyright 2018 Synopsys, Inc.
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
package com.blackducksoftware.bdio2;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

/**
 * Base class for legacy emitters based on a (streaming) JSON parser.
 *
 * @author jgustie
 */
abstract class LegacyJsonParserEmitter implements Emitter {

    /**
     * Factory for creating the JSON parser. Using callable allows us to defer execution of code that may throw an
     * exception until we have a consumer to accept the failure.
     */
    private final Callable<JsonParser> parserSupplier;

    /**
     * The JSON parser (which wraps an input stream). Initially {@code null} until the first {@link #emit} call.
     */
    private JsonParser jp;

    /**
     * Creates a new emitter using the supplied factory to create a parser.
     */
    protected LegacyJsonParserEmitter(JsonFactory jsonFactory, InputStream inputStream) {
        Objects.requireNonNull(jsonFactory);
        Objects.requireNonNull(inputStream);
        parserSupplier = () -> {
            return jsonFactory.createParser(inputStream);
        };
    }

    @Override
    public final void emit(Consumer<Object> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        Objects.requireNonNull(onNext);
        Objects.requireNonNull(onError);
        Objects.requireNonNull(onComplete);
        try {
            if (jp == null) {
                jp = parserSupplier.call();
            }
            Object next = next(jp);
            if (next != null) {
                onNext.accept(next);
            } else {
                onComplete.run();
            }
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    @Override
    public final void dispose() {
        if (jp != null) {
            try {
                jp.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Extracts the next BDIO 2.x entry from the supplied JSON parser, returning {@code null} if no more entries are
     * available and throwing if a failure occurs during parsing.
     */
    @Nullable
    protected abstract Object next(JsonParser jp) throws IOException;

}
