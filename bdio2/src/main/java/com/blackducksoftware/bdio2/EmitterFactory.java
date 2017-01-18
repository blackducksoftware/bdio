/*
 * Copyright 2017 Black Duck Software, Inc.
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

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.blackducksoftware.common.io.ExtraIO;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;

/**
 * Constructs {@link Emitter} instances by sniffing input streams.
 *
 * @author jgustie
 */
class EmitterFactory {

    /**
     * The byte count limit to use when sniffing content.
     */
    private static final int SNIFF_LIMIT = 512;

    /**
     * The names of fields on the scan container. Note that this isn't the full set as we are only concerned with field
     * names which will appear first; included are first three field names, both in declaration order and
     * lexicographical order.
     */
    private static final Set<String> SCAN_CONTAINER_FIELD_NAMES = ImmutableSet.<String> builder()
            // These are the first three fields declared in the `ScanContainerView`
            .add("scanProblem")
            .add("scanProblemList")
            .add("scanNodeList")

            // These are the first three lexicographically sorted fields defined in the `ScanContainerView`
            .add("baseDir")
            .add("createdOn")
            .add("hostName")
            .build();

    /**
     * The names of types in BDIO 1.x. Note that this isn't the full set as we are only concerned with the types that
     * will appear first. In general, BDIO 1.x was always represented in a compacted form with an implicit context so
     * names <em>should</em> be unqualified. That said, there is overlap between versions so we cannot consider
     * something that is in multiple versions of the specification.
     */
    private static final Set<String> BDIO_1X_TYPE_NAMES = ImmutableSet.<String> builder()
            .add("BillOfMaterials")
            .add("http://blackducksoftware.com/rdf/terms#BillOfMaterials")
            .build();

    /**
     * Constructs a {@link BdioDocument.Builder} by looking at the contents (presumably buffered) of what could be a
     * viable BDIO input source.
     */
    public static Emitter newParser(InputStream in) throws IOException {
        // Make sure the input stream is buffered
        InputStream bufferedIn = ExtraIO.buffer(in);

        // Buffer a chunk of the input stream without advancing it
        byte[] buffer = new byte[SNIFF_LIMIT];
        int len = readAndReset(bufferedIn, buffer);

        // Detect which parser to use
        return detectParser(buffer, len).orElse(BdioEmitter::new).apply(bufferedIn);
    }

    /**
     * Attempts to fill the supplied buffer from an input stream, reseting the input stream back to it's original
     * position. Returns the actual number of bytes read.
     */
    private static int readAndReset(InputStream in, byte[] buffer) throws IOException {
        checkState(in.markSupported(), "input stream must support marking");
        in.mark(buffer.length);
        try {
            return ByteStreams.read(in, buffer, 0, buffer.length);
        } finally {
            in.reset();
        }
    }

    /**
     * Given a buffer, returns the BDIO parser used to process the source of the buffered data. This method returns an
     * empty optional if no determination can be made, or if the default parser should be sufficient.
     */
    @VisibleForTesting
    protected static Optional<Function<InputStream, Emitter>> detectParser(byte[] buffer, int len) throws IOException {
        // If we see Zip magic, stick with the default parser
        if (len >= 4 && buffer[0] == 0x50 && buffer[1] == 0x4b && buffer[2] == 0x03 && buffer[3] == 0x04) {
            return Optional.empty();
        }

        // Use a simple streaming JSON parser to read through the buffer (keeping in mind the possibly truncated nature
        // of the data we are looking could cause a failure at any time)
        try {
            JsonParser jp = new JsonFactory().createParser(buffer, 0, len);
            jp.nextToken();
            if (jp.getCurrentToken() == JsonToken.START_ARRAY) {
                // Iterate through the array looking for BDIO 1.x "@type" values until we hit the end (or fail)
                while (jp.nextToken() == JsonToken.START_OBJECT) {
                    while (jp.nextValue() != JsonToken.END_OBJECT) {
                        if (JsonLdConsts.TYPE.equals(jp.getCurrentName()) && BDIO_1X_TYPE_NAMES.contains(jp.getText())) {
                            return Optional.of(LegacyBdio1xEmitter::new);
                        }
                    }
                }
            } else if (jp.getCurrentToken() == JsonToken.START_OBJECT) {
                // Detect scan containers using field names
                if (SCAN_CONTAINER_FIELD_NAMES.contains(jp.nextFieldName())) {
                    return Optional.of(LegacyScanContainerEmitter::new);
                }
            }

            // Try with the default parser
            return Optional.empty();
        } catch (JsonParseException e) {
            // Unrecognizable JSON? Truncated content? Who cares: we don't have a parser for that...
            return Optional.empty();
        }
    }

}
