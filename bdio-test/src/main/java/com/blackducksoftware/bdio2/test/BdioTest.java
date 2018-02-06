/*
 * Copyright 2016 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.blackducksoftware.common.io.HeapInputStream;
import com.blackducksoftware.common.io.HeapOutputStream;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;

/**
 * Helpers for testing BDIO.
 * <p>
 * Methods must be extremely simple and self contained, we don't want bugs being masked.
 *
 * @author jgustie
 */
public final class BdioTest {

    // Mimic JSON-LD
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static {
        OBJECT_MAPPER.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        OBJECT_MAPPER.getFactory().disable(JsonFactory.Feature.INTERN_FIELD_NAMES);
        OBJECT_MAPPER.getFactory().disable(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES);
    }

    /**
     * Returns an input stream representing the UTF-8 encoded bytes of the supplied character sequence.
     */
    public static InputStream utfBytes(CharSequence value) {
        return new HeapInputStream(StandardCharsets.UTF_8.encode(CharBuffer.wrap(value)));
    }

    /**
     * Returns an input stream representing a Zip file where each of the supplied character sequences will be assigned a
     * random entry name that ends with ".jsonld".
     */
    public static InputStream zipBytes(CharSequence... entries) {
        HeapOutputStream buffer = new HeapOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (CharSequence entry : entries) {
                zip.putNextEntry(new ZipEntry(UUID.randomUUID() + ".jsonld"));
                ByteStreams.copy(utfBytes(entry), zip);
            }
        } catch (IOException e) {
            throw new AssertionError("I/O to heap should not fail", e);
        }
        return buffer.getInputStream();
    }

    /**
     * Returns an input stream representing a Zip file where each of the supplied objects has been serialized into JSON
     * in a random entry name that ends with ".jsonld".
     */
    public static InputStream zipJsonBytes(Object... entries) {
        HeapOutputStream buffer = new HeapOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (Object entry : entries) {
                zip.putNextEntry(new ZipEntry(UUID.randomUUID() + ".jsonld"));
                OBJECT_MAPPER.writeValue(zip, entry);
            }
        } catch (IOException e) {
            throw new AssertionError("I/O to heap should not fail", e);
        }
        return buffer.getInputStream();
    }

    /**
     * Returns a collection of strings representing each entry in a Zip file.
     */
    public static List<String> zipEntries(InputStream in) {
        List<String> result = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                if (entry.getName().endsWith(".jsonld")) {
                    result.add(new String(ByteStreams.toByteArray(zip), StandardCharsets.UTF_8));
                }
                entry = zip.getNextEntry();
            }
        } catch (IOException e) {
            throw new AssertionError("I/O to heap should not fail", e);
        }
        return result;
    }

    /**
     * Given an entry, returns an ordered list of the node identifiers. This is useful if you know the node's identifier
     * and need to find it's index in the JSON structure.
     */
    public static List<String> nodeIdentifiers(String entry) {
        try {
            JsonNode json = new ObjectMapper().readTree(entry);
            JsonNode graph = json.get("@graph");
            if (graph != null && graph.isArray()) {
                List<String> result = new ArrayList<>(graph.size());
                for (int i = 0; i < graph.size(); ++i) {
                    JsonNode node = graph.get(i);
                    if (node != null && node.has("@id")) {
                        result.add(node.get("@id").asText());
                    }
                }
                return Collections.unmodifiableList(result);
            } else {
                return Collections.emptyList();
            }
        } catch (IOException e) {
            throw new AssertionError("JSON entry was not valid", e);
        }
    }

    private BdioTest() {
        assert false;
    }
}
