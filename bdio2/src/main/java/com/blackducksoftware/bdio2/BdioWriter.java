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
import com.google.common.collect.ImmutableList;

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
     * Closed state.
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * The number of entries written to the BDIO document. Starts at -1 to account for an initial "header" entry that
     * includes an empty named graph solely for expressing graph metadata.
     */
    private final AtomicInteger entryCount = new AtomicInteger(-1);

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
     * The output stream used for constructing the Zip file.
     */
    private final ZipOutputStream out;

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

    public BdioWriter(BdioMetadata metadata, OutputStream out) {
        this.metadata = Objects.requireNonNull(metadata);
        this.out = new ZipOutputStream(Objects.requireNonNull(out));

        // Generate these fixed byte arrays used to serialize each graph
        header = new StringBuilder()
                .append("{\n  ")
                .append('"').append(JsonLdConsts.ID).append('"')
                .append(" : ")
                .append('"').append(metadata.getId()).append('"')
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
        checkState(entryCount.get() < 0, "already started");

        // Generate the header entry and serialize the JSON to it
        out.putNextEntry(new ZipEntry(Bdio.dataEntryName(entryCount.getAndIncrement())));

        // Generate a representation of the first named graph in the sequence
        Writer writer = new OutputStreamWriter(out, UTF_8);
        JsonUtils.writePrettyPrint(writer, metadata.asNamedGraph(ImmutableList.of()));
        writer.flush();
    }

    /**
     * Writes an individual node to the BDIO document. This must be called <em>after</em> calling {@code start}.
     */
    public void next(Map<String, Object> node) throws IOException {
        checkState(entryCount.get() >= 0, "not started");

        // TODO Have a reusable pool of ByteArrayOutputStream wrapped writers?
        byte[] serializedNode = JsonUtils.toPrettyString(node).replace("\n", "\n  ").getBytes(UTF_8);
        if (remaining.addAndGet((delimiter.length + serializedNode.length) * -1) >= 0L) {
            // It fits, write it out
            out.write(delimiter);
            out.write(serializedNode);
        } else {
            // It didn't fit, create a new entry and try again
            nextEntry();
            if (remaining.addAndGet(serializedNode.length * -1) >= 0L) {
                out.write(serializedNode);
            } else {
                throw new EntrySizeViolationException(
                        Bdio.dataEntryName(entryCount.get() - 1),
                        Math.abs(remaining.get()) + Bdio.MAX_ENTRY_SIZE);
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
     * Closes this BDIO document, finishing all output and closing the underlying stream.
     */
    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            // Write the footer for the current entry
            if (entryCount.get() > 0) {
                out.write(footer);
            }

            // TODO Write out signature files here? Or are we signing each file?

            // Finish the Zip file
            out.close();
        }
    }

    /**
     * Increments the entry being written out.
     */
    private void nextEntry() throws IOException {
        // Check the current entry number to see if we need to close the previous entry
        final int entryNumber = entryCount.getAndIncrement();
        if (entryNumber > 0) {
            out.write(footer);
        }

        // Create a new entry and reset the remaining size
        out.putNextEntry(new ZipEntry(Bdio.dataEntryName(entryNumber)));
        out.write(header);
        remaining.set(Bdio.MAX_ENTRY_SIZE - header.length - footer.length);
    }

}
