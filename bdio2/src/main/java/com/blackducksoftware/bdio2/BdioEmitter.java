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
package com.blackducksoftware.bdio2;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An emitter that wraps a {@code BdioReader} for producing BDIO entries from a byte stream.
 *
 * @author jgustie
 * @see BdioReader
 */
public class BdioEmitter implements Emitter {

    /**
     * The BDIO reader.
     */
    private final BdioReader reader;

    public BdioEmitter(InputStream in) {
        reader = new BdioReader(in);
    }

    /**
     * Callback used to advance the reader. Only one of the supplied functional interfaces will be invoked. The objects
     * sent to {@code onNext} are the raw parsed JSON entries; in general this should be a {@code Map<String, Object>}
     * or a {@code List<Map<String, Object>>} but it could also be a scalar value in some cases.
     */
    @Override
    public void emit(Consumer<Object> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        Objects.requireNonNull(onNext);
        Objects.requireNonNull(onError);
        Objects.requireNonNull(onComplete);
        try {
            Object next = reader.nextEntry();
            if (next != null) {
                onNext.accept(next);
            } else {
                onComplete.run();
            }
        } catch (IOException e) {
            onError.accept(e);
        }
    }

    /**
     * Unchecked version of {@link BdioReader#close()}.
     */
    @Override
    public void dispose() {
        try {
            reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
