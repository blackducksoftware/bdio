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

import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.base.Throwables;

/**
 * Abstraction of what will eventually become a reactive producer.
 *
 * @author jgustie
 */
public interface Emitter {

    /**
     * Emits a single element. Implementations must interact with only one parameter one time per call.
     */
    void emit(Consumer<Object> onNext, Consumer<Throwable> onError, Runnable onComplete);

    /**
     * Releases any resources held by this emitter.
     */
    default void dispose() {
    }

    /**
     * Stream the contents of this emitter.
     */
    default Stream<Object> stream() {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<Object>(Long.MAX_VALUE, 0) {
            private final AtomicBoolean hasNext = new AtomicBoolean(true);

            @Override
            public boolean tryAdvance(Consumer<? super Object> action) {
                emit(action, t -> {
                    Throwables.throwIfUnchecked(t);
                    throw new RuntimeException(t);
                }, () -> {
                    if (hasNext.compareAndSet(true, false)) {
                        dispose();
                    }
                });
                return hasNext.get();
            }
        }, false).onClose(this::dispose);
    }

    /**
     * Returns an emitter that does not produce any content.
     */
    public static Emitter empty() {
        return new Emitter() {
            @Override
            public void emit(Consumer<Object> onNext, Consumer<Throwable> onError, Runnable onComplete) {
                onComplete.run();
            }

            @Override
            public Stream<Object> stream() {
                return Stream.empty();
            }
        };
    }

}
