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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
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
import com.google.common.primitives.Bytes;

/**
 * Constructs {@link Emitter} instances by sniffing input streams.
 *
 * @author jgustie
 */
public class EmitterFactory {

    /**
     * The byte count limit to use when sniffing content.
     */
    private static final int SNIFF_LIMIT = 512;

    /**
     * The JSON factory used to create parsers.
     */
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /**
     * The names of fields on the scan container. Note that this isn't the full set as we are only concerned with field
     * names which will appear in the first {@value #SNIFF_LIMIT} bytes; included are first three field names, both in
     * original declaration order (which was used prior to assignment of an explicit field order) and lexicographical
     * order (which is used by some pretty printers).
     */
    @VisibleForTesting
    static final ImmutableSet<String> SCAN_CONTAINER_FIELD_NAMES = ImmutableSet.<String> builder()
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
     * The names of the fields the scan container indicating a new version of the data that can be streamed.
     */
    @VisibleForTesting
    static final ImmutableSet<String> STREAMABLE_SCAN_CONTAINER_FIELD_NAMES = ImmutableSet.<String> builder()
            // These are the first three fields since the `ScanContainerView` explicitly specified serialization order
            .add("scannerVersion")
            .add("signatureVersion")
            .add("ownerEntityKeyToken")

            .build();

    /**
     * The names of types in BDIO 1.x. Note that this isn't the full set as we are only concerned with the types that
     * will appear first. In general, BDIO 1.x was always represented in a compacted form with an implicit context so
     * names <em>should</em> be unqualified. That said, there is overlap between versions so we cannot consider
     * something that is in multiple versions of the specification.
     */
    @VisibleForTesting
    static final ImmutableSet<String> BDIO_1X_TYPE_NAMES = ImmutableSet.<String> builder()
            // These are the unqualified names that do not conflict
            .add("BillOfMaterials")
            .add("ExternalIdentifier")
            .add("MatchDetail")

            // These are the fully qualified names
            .add("http://blackducksoftware.com/rdf/terms#BillOfMaterials")
            .add("http://blackducksoftware.com/rdf/terms#Component")
            .add("http://blackducksoftware.com/rdf/terms#File")
            .add("http://blackducksoftware.com/rdf/terms#License")
            .add("http://blackducksoftware.com/rdf/terms#Project")
            .add("http://blackducksoftware.com/rdf/terms#Vulnerability")
            .add("http://blackducksoftware.com/rdf/terms#ExternalIdentifier")
            .add("http://blackducksoftware.com/rdf/terms#MatchDetail")

            .build();

    /**
     * The names of fields in BDIO 1.x. As with type names, this is not a full set, just the distinct parts.
     */
    static final ImmutableSet<String> BDIO_1X_FIELD_NAMES = ImmutableSet.<String> builder()
            // These are the unqualified names that do not conflict
            .add("specVersion")
            .add("externalIdentifier")
            .add("externalSystemTypeId")
            .add("externalId")
            .add("externalRepositoryLocation")
            .add("matchDetail")
            .add("matchType")

            // There are the fully qualified names
            .add("http://blackducksoftware.com/rdf/terms#specVersion")
            .add("http://blackducksoftware.com/rdf/terms#externalIdentifier")
            .add("http://blackducksoftware.com/rdf/terms#externalSystemTypeId")
            .add("http://blackducksoftware.com/rdf/terms#externalId")
            .add("http://blackducksoftware.com/rdf/terms#externalRepositoryLocation")
            .add("http://blackducksoftware.com/rdf/terms#matchDetail")
            .add("http://blackducksoftware.com/rdf/terms#matchType")

            .build();

    /**
     * Constructs a {@link BdioDocument.Builder} by looking at the contents (presumably buffered) of what could be a
     * viable BDIO input source.
     */
    public static Emitter newEmitter(BdioContext context, InputStream in) throws IOException {
        // Make sure the input stream is buffered
        InputStream bufferedIn = ExtraIO.buffer(in);
        if (context.isLegacyBdio()) {
            // Allow the context to bypass the sniff test
            return new LegacyBdio1xEmitter(bufferedIn);
        } else {
            byte[] buffer = new byte[SNIFF_LIMIT];
            int len = readAndReset(bufferedIn, buffer);
            return detectEmitter(buffer, len)
                    .orElse(BdioEmitter::new).apply(bufferedIn);
        }
    }

    /**
     * Attempts to fill the supplied buffer from an input stream, reseting the input stream back to it's original
     * position. Returns the actual number of bytes read.
     */
    private static int readAndReset(InputStream in, byte[] buffer) throws IOException {
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
    protected static Optional<Function<InputStream, Emitter>> detectEmitter(byte[] buffer, int len) throws IOException {
        // Optimization for empty input
        if (isEmpty(buffer, len)) {
            return Optional.of(x -> Emitter.empty());
        }

        // If we see Zip magic, stick with the default parser
        if (isZipMagic(buffer, len)) {
            return Optional.empty();
        }

        // BDIO 1.x used a vocabulary that is distinct enough to qualify by presence
        if (containsBdio1xVocab(buffer, len)) {
            return Optional.of(LegacyBdio1xEmitter::new);
        }

        // Use a simple streaming JSON parser to read through the buffer (keeping in mind the possibly truncated nature
        // of the data we are looking could cause a failure at any time)
        try {
            JsonParser jp = JSON_FACTORY.createParser(buffer, 0, len);
            jp.nextToken();
            if (jp.getCurrentToken() == JsonToken.START_ARRAY) {
                // Iterate through the array looking for BDIO 1.x "@type" values until we hit the end (or fail)
                while (jp.nextToken() == JsonToken.START_OBJECT) {
                    while (jp.nextValue() != JsonToken.END_OBJECT) {
                        if (BDIO_1X_FIELD_NAMES.contains(jp.getCurrentName())
                                || (JsonLdConsts.TYPE.equals(jp.getCurrentName()) && BDIO_1X_TYPE_NAMES.contains(jp.getText()))) {
                            return Optional.of(LegacyBdio1xEmitter::new);
                        }
                    }
                }
            } else if (jp.getCurrentToken() == JsonToken.START_OBJECT) {
                // Detect scan containers using field names
                String fieldName = jp.nextFieldName();
                if (SCAN_CONTAINER_FIELD_NAMES.contains(fieldName)) {
                    return Optional.of(LegacyScanContainerEmitter::new);
                } else if (STREAMABLE_SCAN_CONTAINER_FIELD_NAMES.contains(fieldName)) {
                    return Optional.of(LegacyStreamingScanContainerEmitter::new);
                }
            }

            // Try with the default parser
            return Optional.empty();
        } catch (JsonParseException e) {
            // Unrecognizable JSON? Truncated content? Who cares: we don't have an emitter for that...
            return Optional.empty();
        }
    }

    private static boolean isZipMagic(byte[] buffer, int len) {
        return len >= 4 && buffer[0] == 0x50 && buffer[1] == 0x4b && buffer[2] == 0x03 && buffer[3] == 0x04;
    }

    private static boolean isEmpty(byte[] buffer, int len) {
        if (len == 0) {
            return true;
        }

        // Test for "logically empty" (e.g. "{}" or "[]")
        try {
            JsonParser jp = JSON_FACTORY.createParser(buffer, 0, len);
            return jp.nextToken().isStructStart() && jp.nextToken().isStructEnd();
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean containsBdio1xVocab(byte[] buffer, int len) {
        byte[] array = len == buffer.length ? buffer : Arrays.copyOf(buffer, len);
        byte[] target = "http://blackducksoftware.com/rdf/terms#".getBytes(UTF_8);
        return Bytes.indexOf(array, target) > 0;
    }

}
