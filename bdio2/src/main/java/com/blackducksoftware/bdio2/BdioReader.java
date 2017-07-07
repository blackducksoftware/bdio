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

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;

import com.github.jsonldjava.utils.JsonUtils;

/**
 * A reader for BDIO data. Used to obtain full JSON-LD graphs.
 *
 * @author jgustie
 */
public class BdioReader implements Closeable {

    /**
     * An input stream wrapper that hides the details of the underlying stream.
     */
    private static final class JsonInputStream extends FilterInputStream {

        /**
         * Enforce the 16MB limit on JSON entries.
         */
        private int remaining = Bdio.MAX_ENTRY_SIZE;

        /**
         * The entry name that is being read or {@code null} if a raw JSON file is being read.
         */
        @Nullable
        private final String name;

        /**
         * The estimated total size (in bytes) of the entry or -1 if it is unknown. In theory, this value should always
         * be less then {@value Bdio#MAX_ENTRY_SIZE}, however it is possible that the Zip file misrepresents the
         * actual size of the entry.
         */
        private final long estimatedSize;

        private JsonInputStream(InputStream in) {
            super(in);
            name = null;
            estimatedSize = -1;
        }

        private JsonInputStream(InputStream in, String name, long estimatedSize) {
            super(in);
            this.name = Objects.requireNonNull(name);
            this.estimatedSize = estimatedSize < 0 ? -1 : estimatedSize;
        }

        private void checkRemaining() throws IOException {
            // This will always end up at exactly zero when we have read everything
            if (remaining == 0) {
                throw new EntrySizeViolationException(name, estimatedSize);
            }
        }

        @Override
        public void close() throws IOException {
            // When the caller closes this stream on a Zip, we really just want to close the entry
            if (in instanceof ZipInputStream) {
                ((ZipInputStream) in).closeEntry();
            } else {
                super.close();
            }
        }

        @Override
        public int available() throws IOException {
            return Math.min(in.available(), remaining);
        }

        @Override
        public int read() throws IOException {
            checkRemaining();
            int result = super.read();
            if (result != -1) {
                --remaining;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            checkRemaining();
            int result = in.read(b, off, Math.min(len, remaining));
            if (result != -1) {
                remaining -= result;
            }
            return result;
        }

        @Override
        public long skip(long n) throws IOException {
            // Since `remaining` is an int, we can cast back down safely
            int result = (int) in.skip(Math.min(n, remaining));
            remaining -= result;
            return result;
        }

        // TODO Mark support?

        @Override
        public synchronized void reset() throws IOException {
            throw new IOException("mark/reset not supported");
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }

    /**
     * A Zip local entry header signature appears before each entry. BDIO Documents must not have headers so this
     * signature will appear at the beginning of a BDIO Document.
     */
    private static byte[] ZIP_MAGIC = new byte[] { 0x50, 0x4b, 0x03, 0x04 };

    /**
     * The possible states for the reader.
     */
    private enum State {
        OPEN, ZIP, JSON, CLOSED
    }

    /**
     * The input source. Can be either Zip or JSON data.
     */
    private final InputStream in;

    /**
     * The current state of this reader.
     */
    private State state = State.OPEN;

    /**
     * The Zip input stream, {@code null} unless the state is {@linkplain Phase#ZIP Zip}.
     */
    @Nullable
    private ZipInputStream zip;

    public BdioReader(InputStream in) {
        this.in = Objects.requireNonNull(in);
    }

    @Nullable
    public Object nextEntry() throws IOException {
        try (InputStream input = nextStream()) {
            // Use the JSON-LD API's method for parsing JSON, assumes (properly) UTF-8
            return input != null ? JsonUtils.fromInputStream(input) : null;
        }
    }

    @Nullable
    private synchronized InputStream nextStream() throws IOException {
        switch (state) {
        case OPEN:
            // First time we can throw an exception, update the state and recurse one time
            if (isZipFile(in)) {
                zip = new ZipInputStream(in, StandardCharsets.UTF_8);
                state = State.ZIP;
            } else {
                state = State.JSON;
            }
            return nextStream();

        case ZIP:
            // Scan the Zip file for JSON entries
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                if (Bdio.isDataEntryName(entry.getName())) {
                    return new JsonInputStream(zip, entry.getName(), entry.getSize());
                }
                entry = zip.getNextEntry();
            }

            // No more entries, eagerly close the stream
            close();
            return null;

        case JSON:
            // Just return the input source, it will get closed by the caller
            state = State.CLOSED;
            return new JsonInputStream(in);

        default:
            // Indicate that we are done by returning null
            return null;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            if (zip != null) {
                zip.close();
            } else {
                in.close();
            }
        } finally {
            state = State.CLOSED;
        }
    }

    /**
     * Check if an input stream represents a Zip file.
     */
    private static boolean isZipFile(InputStream in) throws IOException {
        final byte[] signature = new byte[ZIP_MAGIC.length];

        in.mark(signature.length);
        int len = in.read(signature);
        in.reset();

        return len == signature.length && Arrays.equals(signature, ZIP_MAGIC);
    }

}
