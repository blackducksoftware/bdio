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
        }, false);
    }

}
