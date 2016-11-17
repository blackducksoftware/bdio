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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.blackducksoftware.bdio.jsonld.JsonLdKeyword;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.base.Functions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import rx.Subscriber;
import rx.exceptions.OnErrorNotImplementedException;

/**
 * A subscriber for serializing a BDIO document to a byte stream. This subscriber always produces the Zip form of BDIO
 * data, generally this is used to write data to disk. For writing BDIO data to the network, it is likely better to
 * break up the data into multiple requests prior to serialization.
 *
 * @author jgustie
 */
class BdioSubscriber extends Subscriber<Map<String, Object>> {

    // TODO What about signing?

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
     * The output stream used for constructing the Zip file.
     */
    private final ZipOutputStream out;

    /**
     * The first entry written to the Zip file, generally this only for named graph metadata.
     */
    private final Map<String, Object> headerEntry;

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

    BdioSubscriber(BdioMetadata metadata, OutputStream out) {
        // TODO Always buffer the stream?
        this.out = new ZipOutputStream(new BufferedOutputStream(Objects.requireNonNull(out)));

        // Generate a representation of the first named graph in the sequence
        headerEntry = ImmutableMap.<String, Object> builder()
                .putAll(metadata)
                .put(JsonLdKeyword.graph.toString(), ImmutableList.of())
                .build();

        // Generate these fixed byte arrays used to serialize each graph
        header = formatUtf8Bytes("{%n  \"%s\" : \"%s\",%n  \"%s\" : [ ", JsonLdKeyword.id, metadata.id(), JsonLdKeyword.graph);
        delimiter = formatUtf8Bytes(", ");
        footer = formatUtf8Bytes(" ]%n}%n");
    }

    @Override
    public void onStart() {
        try {
            // Generate the header entry and serialize the JSON to it
            out.putNextEntry(new ZipEntry(Bdio.dataEntryName(entryCount.getAndIncrement())));
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            JsonUtils.writePrettyPrint(writer, headerEntry);
            writer.flush();
        } catch (IOException e) {
            onError(e);
        }
    }

    @Override
    public void onNext(Map<String, Object> node) {
        try {
            // TODO Have a reusable pool of ByteArrayOutputStream wrapped writers
            byte[] serializedNode = JsonUtils.toPrettyString(node).replace("\n", String.format("%n  ")).getBytes(StandardCharsets.UTF_8);
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
                    // Add back the space since we didn't write it out
                    remaining.getAndAdd(serializedNode.length * -1);

                    // Split the node into smaller pieces, this should be rare
                    splitNode(node);
                }
            }
        } catch (IOException e) {
            onError(e);
        }
    }

    @Override
    public void onCompleted() {
        try {
            // Write the footer for the current entry
            if (entryCount.get() > 0) {
                out.write(footer);
            }

            // TODO Write out signature files here? Or are we signing each file?

            // Finish the Zip file and flush the (buffered) contents
            out.finish();
            out.flush();
        } catch (IOException e) {
            onError(e);
        }
    }

    @Override
    public void onError(Throwable e) {
        // TODO Is this ok?
        throw new OnErrorNotImplementedException(e);
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
        ZipEntry entry = new ZipEntry(Bdio.dataEntryName(entryNumber));
        out.putNextEntry(entry);
        out.write(header);
        remaining.set(Bdio.MAX_ENTRY_SIZE - header.length - footer.length);
    }

    /**
     * The node keys that should be preserved across split nodes.
     */
    private static final Set<String> REQUIRED_NODE_KEYS = FluentIterable.from(Arrays.asList(JsonLdKeyword.id, JsonLdKeyword.type))
            .transform(Functions.toStringFunction()).toSet();

    /**
     * Splits a single node that is too large to write completely. This is an edge case to say the least.
     */
    private void splitNode(Map<String, Object> node) {
        if (node instanceof Map<?, ?>) {
            if (node.size() == REQUIRED_NODE_KEYS.size() + 1) {
                // Recursive rock bottom: we only have one non-required value left
                throw new IllegalStateException("Node cannot be split");
            }

            // Create a map sized for all the required keys plus half of the non-required keys
            final int limit = ((node.size() - REQUIRED_NODE_KEYS.size()) / 2) + REQUIRED_NODE_KEYS.size();
            Map<String, Object> partitionMap = new LinkedHashMap<>(limit);
            for (String requiredKey : REQUIRED_NODE_KEYS) {
                partitionMap.put(requiredKey, node.get(requiredKey));
            }

            // Try recursively writing out each partition
            for (Map.Entry<String, Object> entry : node.entrySet()) {
                if (!REQUIRED_NODE_KEYS.contains(entry.getKey())) {
                    partitionMap.put(entry.getKey(), entry.getValue());
                    if (partitionMap.size() == limit) {
                        onNext(partitionMap);
                        partitionMap.keySet().retainAll(REQUIRED_NODE_KEYS);
                    }
                }
            }
        } else {
            throw new IllegalStateException("Unsplittable object type");
        }
    }

    /**
     * Returns the specified pattern formatted and converted to UTF-8 bytes.
     */
    private static byte[] formatUtf8Bytes(String format, Object... args) {
        return String.format(format, args).getBytes(StandardCharsets.UTF_8);
    }

}
