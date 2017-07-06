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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A subscriber for serializing BDIO nodes to a byte stream.
 *
 * @author jgustie
 * @see BdioWriter
 */
public class BdioSubscriber implements Subscriber<Map<String, Object>> {

    private final BdioWriter writer;

    private Subscription subscription;

    public BdioSubscriber(BdioMetadata metadata, OutputStream out) {
        writer = new BdioWriter(metadata, out);
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
            // TODO What do we do? Anything?
        }
    }

    @Override
    public void onError(Throwable e) {
        // Make sure we try to close the writer so the Zip isn't corrupted
        try {
            writer.close();
        } catch (IOException suppressed) {
            e.addSuppressed(suppressed);
        }

        // TODO What else do we do? Anything?
    }
}
