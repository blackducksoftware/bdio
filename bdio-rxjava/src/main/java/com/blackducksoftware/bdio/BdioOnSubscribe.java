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
package com.blackducksoftware.bdio;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;

import com.github.jsonldjava.utils.JsonUtils;

import rx.Observer;
import rx.observables.SyncOnSubscribe;

/**
 * An on-subscribe for deserializing a BDIO document from a byte stream. This produces elements which are themselves
 * JSON-LD graphs (generally containing a sequence of nodes). If the supplied input stream is JSON data, only a single
 * element will be emitted; if the supplied input stream is a Zip file, an element will be emitted for each JSON entry
 * in the Zip file. The type of object produced depends on what the root JSON element is.
 * <p>
 * Note that the resulting observable will fail if subscribed to multiple times because only one pass can be made
 * through the input stream. If multiple subscribers are needed, consider caching or publishing.
 *
 * @author jgustie
 * @since 2.0.0
 */
class BdioOnSubscribe extends SyncOnSubscribe<BdioOnSubscribe.State, Object> {

    /**
     * The state object used to implement this {@code OnSubscribe}.
     * <p>
     * Internally, this is a "throwing supplier" which produces input streams. The input source can be an input stream
     * representing either JSON data or a Zip file which contains JSON entries.
     */
    public static final class State {

        /**
         * A Zip local entry header signature appears before each entry. BDIO Documents must not have headers so this
         * signature will appear at the beginning of a BDIO Document.
         */
        private static byte[] ZIP_MAGIC = new byte[] { 0x50, 0x4b, 0x03, 0x04 };

        /**
         * The possible phases for this state object.
         */
        private enum Phase {
            OPEN, ZIP, JSON, CLOSED
        }

        /**
         * The current phase of this state object.
         */
        private Phase phase = Phase.OPEN;

        /**
         * The input source. Can be either Zip or JSON data.
         */
        private final InputStream in;

        /**
         * The Zip input stream, {@code null} unless the phase is {@linkplain Phase#ZIP Zip}.
         */
        @Nullable
        private ZipInputStream zip;

        private State(AtomicReference<InputStream> inputSource) {
            final InputStream in = inputSource.getAndSet(null);
            if (in == null) {
                throw new IllegalStateException("multiple attempts to consume a single input stream");
            } else if (!in.markSupported()) {
                throw new IllegalArgumentException("input must support marking");
            }

            this.in = in;
        }

        /**
         * Returns the next input stream of JSON data to be processed.
         */
        @Nullable
        synchronized InputStream input() throws IOException {
            switch (phase) {
            case OPEN:
                // First time we can throw an exception, update the state and recurse one time
                if (isZipFile(in)) {
                    zip = new ZipInputStream(in, StandardCharsets.UTF_8);
                    phase = Phase.ZIP;
                } else {
                    phase = Phase.JSON;
                }
                return input();

            case ZIP:
                // Scan the Zip file for JSON entries
                ZipEntry entry = zip.getNextEntry();
                while (entry != null) {
                    if (isJsonEntry(entry)) {
                        return new JsonInputStream(zip, entry.getName(), entry.getSize());
                    }
                    entry = zip.getNextEntry();
                }

                // No more entries, close the Zip input stream ourselves
                phase = Phase.CLOSED;
                zip.close();
                return null;

            case JSON:
                // Just return the input source, it will get closed by the caller
                phase = Phase.CLOSED;
                return new JsonInputStream(in);

            default:
                // Indicate that we are done by returning null
                return null;
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

        /**
         * Check if a Zip entry represents JSON data.
         */
        private static boolean isJsonEntry(ZipEntry entry) {
            return entry.getName().endsWith(".jsonld");
        }
    }

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
         * be larger then {@value Bdio#MAX_ENTRY_SIZE}, however it is possible that the Zip file misrepresents the
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
            long result = in.skip(Math.min(n, remaining));
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
     * The input source: a reference to a stream of either JSON or Zip formated BDIO data.
     */
    private final AtomicReference<InputStream> inputSource;

    public BdioOnSubscribe(InputStream in) {
        inputSource = new AtomicReference<>(Objects.requireNonNull(in));
    }

    @Override
    protected State generateState() {
        return new State(inputSource);
    }

    @Override
    protected State next(State state, Observer<? super Object> observer) {
        try (InputStream input = state.input()) {
            if (input != null) {
                // Use the JSON-LD API's method for parsing JSON, assumes (properly) UTF-8
                observer.onNext(JsonUtils.fromInputStream(input));
            } else {
                observer.onCompleted();
            }
        } catch (IOException e) {
            observer.onError(e);
        }
        return state;
    }

}
