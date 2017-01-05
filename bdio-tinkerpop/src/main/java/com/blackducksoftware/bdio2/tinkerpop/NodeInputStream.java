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
package com.blackducksoftware.bdio2.tinkerpop;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import com.github.jsonldjava.utils.JsonUtils;

/**
 * Wrapper around a node in a JSON-LD graph used to avoid excessive serialization.
 *
 * @author jgustie
 */
class NodeInputStream extends InputStream {

    /**
     * Reads the entire supplied input stream as an individual JSON-LD node.
     */
    public static Map<String, Object> readNode(InputStream inputStream) throws IOException {
        if (inputStream instanceof NodeInputStream) {
            // Perfect, we avoided any kind of serialization
            return ((NodeInputStream) inputStream).node;
        } else {
            // We actually need to read the input stream
            Object node = JsonUtils.fromInputStream(inputStream);
            if (node instanceof Map<?, ?>) {
                return (Map<String, Object>) node;
            } else {
                // TODO What should this throw?
                throw new IOException("Expected map");
            }
        }
    }

    /**
     * Wraps a node in an input stream; the exact node can be retrieved without JSON serialization using
     * {@link #readNode(InputStream)} on the resulting input stream.
     */
    public static InputStream wrapNode(Map<String, Object> node) {
        return new NodeInputStream(node);
    }

    /**
     * The wrapped JSON-LD node.
     */
    private final Map<String, Object> node;

    /**
     * An input stream representing {@code node} as bytes. This field will remain {@code null} unless it actually is
     * needed, allowing us to avoid serialization unless necessary.
     */
    private volatile InputStream delegate;

    private NodeInputStream(Map<String, Object> node) {
        this.node = Objects.requireNonNull(node);
    }

    private InputStream delegate() throws IOException {
        InputStream result = delegate;
        if (result == null) {
            synchronized (this) {
                result = delegate;
                if (result == null) {
                    // If we need to actually fulfill the input stream contract, lazily serialize the node
                    result = delegate = new ByteArrayInputStream(JsonUtils.toString(node).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        return result;
    }

    @Override
    public int read() throws IOException {
        return delegate().read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return delegate().read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate().skip(n);
    }
}
