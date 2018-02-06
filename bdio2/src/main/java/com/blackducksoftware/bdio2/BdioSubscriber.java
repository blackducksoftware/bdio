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
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;

/**
 * A subscriber for serializing BDIO nodes to a byte stream.
 *
 * @author jgustie
 * @see BdioWriter
 */
public class BdioSubscriber implements Subscriber<Map<String, Object>> {

    private final BdioWriter writer;

    private final Consumer<Throwable> onError;

    private Subscription subscription;

    public BdioSubscriber(BdioMetadata metadata, StreamSupplier entryStreams, Consumer<Throwable> onError) {
        writer = new BdioWriter(metadata, entryStreams);
        this.onError = Objects.requireNonNull(onError);
    }

    private void validateSubscription(Subscription s) {
        Objects.requireNonNull(s, "subscription is null");
        if (subscription != null) {
            subscription.cancel();
            throw new IllegalStateException("subscription was already set");
        } else {
            subscription = s;
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        validateSubscription(s);

        try {
            writer.start();
            subscription.request(Long.MAX_VALUE);
        } catch (IOException e) {
            onError(e);
        }
    }

    @Override
    public void onNext(Map<String, Object> node) {
        try {
            writer.next(node);
        } catch (IOException e) {
            onError(e);
        }
    }

    @Override
    public void onComplete() {
        try {
            writer.close();
        } catch (IOException e) {
            // Don't call `onError(e)` here (even though `writer.close()` is idempotent?!)
            onError.accept(e);
        }
    }

    @Override
    public void onError(Throwable e) {
        // Make sure we try to close the writer so the Zip isn't corrupted
        try {
            writer.close();
        } catch (IOException | RuntimeException suppressed) {
            e.addSuppressed(suppressed);
        }
        onError.accept(e);
    }
}
