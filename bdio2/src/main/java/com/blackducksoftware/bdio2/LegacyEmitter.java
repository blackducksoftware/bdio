/*
 * Copyright 2017 Black Duck Software, Inc.
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
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.blackducksoftware.common.io.ExtraIO;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;

abstract class LegacyEmitter implements Emitter {

    /**
     * The spliterator over the BDIO nodes.
     */
    private final Spliterator<? extends Object> bdioNodes;

    protected LegacyEmitter(Spliterator<? extends Object> bdioNodes) {
        this.bdioNodes = Objects.requireNonNull(bdioNodes);
    }

    /**
     * Returns a single element stream which will parse the supplied bytes once a terminal operation is invoked on the
     * stream, i.e. just constructing the stream will not advance the byte sequence.
     */
    protected static <T> Stream<T> streamLazyFromJson(InputStream in, Class<T> type, Module... modules) {
        return StreamSupport.stream(() -> {
            try {
                return Spliterators.spliterator(new Object[] {
                        new ObjectMapper()
                                .registerModules(modules)
                                .readValue(ExtraIO.buffer(in), type) },
                        Spliterator.DISTINCT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Spliterator.DISTINCT | Spliterator.SIZED | Spliterator.SUBSIZED, false);
    }

    @Override
    public void emit(Consumer<Object> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        Objects.requireNonNull(onNext);
        Objects.requireNonNull(onError);
        Objects.requireNonNull(onComplete);
        try {
            if (!bdioNodes.tryAdvance(onNext)) {
                onComplete.run();
            }
        } catch (UncheckedIOException e) {
            onError.accept(e.getCause());
        } catch (RuntimeException e) {
            onError.accept(e);
        }
    }

    @Override
    public void dispose() {
        // Right now, this does nothing because all we can do at this point is go out of scope
    }

}
