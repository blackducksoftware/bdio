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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A generator for producing BDIO entries from a byte stream.
 *
 * @author jgustie
 * @see BdioReader
 */
public class BdioGenerator {

    private final BdioReader reader;

    public BdioGenerator(InputStream in) {
        reader = new BdioReader(in);
    }

    /**
     * Callback used to advance the reader. Only one of the supplied functional interfaces will be invoked. The objects
     * sent to {@code onNext} are the raw parsed JSON entries; in general this should be a {@code Map<String, Object>}
     * or a {@code List<Map<String, Object>>} but it could also be a scalar value in some cases.
     */
    public void generate(Consumer<Object> onNext, Consumer<Throwable> onError, Runnable onComplete) {
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
    public void dispose() throws UncheckedIOException {
        try {
            reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
