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

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.Closeable;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.utils.JsonUtils;

/**
 * A writer for serializing BDIO nodes to a byte stream. This writer always produces the Zip form of BDIO data,
 * generally this is used to write data to disk. For writing BDIO data to the network, it is likely better to
 * break up the data into multiple requests prior to serialization.
 *
 * @author jgustie
 */
public class BdioWriter implements Closeable {

    // TODO What about signing?

    /**
     * Supplier of output streams for writing BDIO data.
     */
    @FunctionalInterface
    public interface StreamSupplier extends Closeable {

        /**
         * Returns a new output stream for writing BDIO with the supplied entry name. The caller is responsible for
         * closing the supplied stream.
         */
        OutputStream newStream() throws IOException;

        /**
         * Called one to release all resources associated with this stream supplier. It is implementation specific as to
         * how existing unclosed streams will be handled.
         */
        @Override
        default void close() throws IOException {
            // By default, do nothing on close
        }
    }

    /**
     * A stream supplier for writing BDIO out to a file.
     */
    public static class BdioFile implements StreamSupplier {

        /**
         * The output stream used for constructing the Zip file.
         */
        private final ZipOutputStream out;

        /**
         * The number of entries written to the BDIO document. Starts at -1 to account for an initial "header" entry
         * that includes an empty named graph solely for expressing graph metadata.
         */
        private final AtomicInteger entryCount = new AtomicInteger(-1);

        // TODO Take a java.nio.file.Path instead?
        public BdioFile(OutputStream outputStream) {
            out = new ZipOutputStream(Objects.requireNonNull(outputStream));
        }

        @Override
        public OutputStream newStream() throws IOException {
            out.putNextEntry(new ZipEntry(Bdio.dataEntryName(entryCount.getAndIncrement())));
            return new FilterOutputStream(out) {
                @Override
                public void close() throws IOException {
                    // Do not close the Zip file for each entry
                }
            };
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    /**
     * Closed state.
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Started state.
     */
    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * Matching header/footer state.
     */
    private final AtomicBoolean needsFooter = new AtomicBoolean();

    /**
     * The number of bytes remaining before the current entry is full. Entries have a fixed size limit that must not be
     * exceeded (or parsing will fail). This value is established once the entry is opened and updated each time data is
     * written out.
     * <p>
     * NOTE: This is the <em>uncompressed</em> entry size.
     */
    private final AtomicInteger remaining = new AtomicInteger();

    /**
     * The BDIO metadata.
     */
    private final BdioMetadata metadata;

    /**
     * The source of output streams for each entry.
     */
    private final StreamSupplier entryStreams;

    /**
     * The leading bytes used to start each entry in the Zip file.
     */
    private final byte[] header;

    /**
     * The bytes used to delimit each JSON-LD node within each entry.
     */
    private final byte[] delimiter;

    /**
     * The trailing bytes used to finish each entry in the Zip file.
     */
    private final byte[] footer;

    /**
     * The output stream for the current entry.
     */
    private OutputStream out;

    /**
     * Creates a new writer using the supplied metadata and source of output streams.
     */
    public BdioWriter(BdioMetadata metadata, StreamSupplier entryStreams) {
        this.metadata = Objects.requireNonNull(metadata);
        this.entryStreams = Objects.requireNonNull(entryStreams);

        // Generate these fixed byte arrays used to serialize each graph
        header = new StringBuilder()
                .append("{\n  ")
                .append('"').append(JsonLdConsts.ID).append('"')
                .append(" : ")
                .append('"').append(metadata.id()).append('"')
                .append(",\n  ")
                .append('"').append(JsonLdConsts.GRAPH).append('"')
                .append(" : [ ")
                .toString().getBytes(UTF_8);
        delimiter = ", ".getBytes(UTF_8);
        footer = " ]\n}\n".getBytes(UTF_8);
    }

    /**
     * Starts writing the BDIO document. This must be called exactly once, <em>before</em> starting to call {@code next}
     * to write individual nodes.
     */
    public void start() throws IOException {
        checkState(started.compareAndSet(false, true), "already started");

        // Write just the full metadata as the first entry
        out = entryStreams.newStream();
        Writer writer = new OutputStreamWriter(out, UTF_8);
        JsonUtils.writePrettyPrint(writer, metadata.asNamedGraph());
        writer.flush();
    }

    /**
     * Writes an individual node to the BDIO document.
     */
    public void next(Map<String, Object> node) throws IOException {
        checkState(started.get(), "not started");

        // TODO Have a reusable pool of ByteArrayOutputStream wrapped writers?
        byte[] serializedNode = JsonUtils.toPrettyString(node).replace("\n", "\n  ").getBytes(UTF_8);
        if (remaining.addAndGet((delimiter.length + serializedNode.length) * -1) > 0L) {
            // It fits, write it out
            out.write(delimiter);
            out.write(serializedNode);
        } else {
            // It didn't fit, create a new entry and try again
            nextEntry();
            if (remaining.addAndGet(serializedNode.length * -1) > 0L) {
                out.write(serializedNode);
            } else {
                throw new EntrySizeViolationException(null, Math.abs(remaining.get()) + Bdio.MAX_ENTRY_SIZE);
            }
        }
    }

    /**
     * Closes this BDIO document, finishing all output and releasing any resources.
     */
    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            try {
                // Write the footer for the current entry
                closeStream();

                // TODO Write out signature files here? Or are we signing each file?
            } finally {
                // Release the source of entry streams
                entryStreams.close();
            }
        }
    }

    /**
     * Force close the current entry before reaching the maximum file size.
     */
    public void closeEntry() {
        // Really just drain the remaining size so the next write will open a new entry
        remaining.set(0);
    }

    /**
     * Increments the entry being written out.
     */
    private void nextEntry() throws IOException {
        // Create a new entry and reset the remaining size
        closeStream();
        out = entryStreams.newStream();
        out.write(header);
        needsFooter.set(true);
        remaining.set(Bdio.MAX_ENTRY_SIZE - header.length - footer.length);
    }

    /**
     * Closes the current entry stream.
     */
    private void closeStream() throws IOException {
        // Check if we need to finish the previous entry
        if (out != null) {
            if (needsFooter.get()) {
                out.write(footer);
            }
            out.close();
        }
    }

}
