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

import static com.blackducksoftware.bdio2.test.BdioTest.utfBytes;
import static com.blackducksoftware.bdio2.test.BdioTest.zipBytes;
import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

import com.blackducksoftware.common.io.HeapInputStream;
import com.blackducksoftware.common.io.HeapOutputStream;
import com.fasterxml.jackson.core.JsonParseException;
import com.google.common.io.ByteStreams;

/**
 * Tests for {@link BdioReader}.
 *
 * @author jgustie
 */
public class BdioReaderTest {

    /**
     * A {@code null} stream is not valid.
     */
    @SuppressWarnings("resource")
    @Test(expected = NullPointerException.class)
    public void nullStream() {
        new BdioReader(null);
    }

    /**
     * An empty stream is just a JSON parsing error.
     */
    @SuppressWarnings("resource")
    @Test(expected = JsonParseException.class)
    public void emptyStream() throws IOException {
        new BdioReader(new HeapInputStream(new byte[0])).nextEntry();
    }

    /**
     * Verify an object with a single name/value pair can be read through as plain JSON.
     */
    @Test
    public void objectSingleNameValueJson() throws IOException {
        try (BdioReader reader = new BdioReader(utfBytes("{ \"test\" : \"foo\" }"))) {
            Object entry = reader.nextEntry();
            assertThat(entry).isInstanceOf(Map.class);
            assertThat((Map<?, ?>) entry).containsEntry("test", "foo");

            assertThat(reader.nextEntry()).isNull();
        }
    }

    /**
     * Verify a list with a single element can be read through as plain JSON.
     */
    public void singleElementListJson() throws IOException {
        try (BdioReader reader = new BdioReader(utfBytes("[ \"test\" ]"))) {
            Object entry = reader.nextEntry();
            assertThat(entry).isInstanceOf(List.class);
            assertThat((List<?>) entry).containsExactly("test");

            assertThat(reader.nextEntry()).isNull();
        }
    }

    /**
     * Verify an object with a single name/value pair can be through as a Zip file.
     */
    @Test
    public void singleKeyObjectZip() throws IOException {
        try (BdioReader reader = new BdioReader(zipBytes("{ \"test\" : \"foo\" }"))) {
            Object entry = reader.nextEntry();
            assertThat(entry).isInstanceOf(Map.class);
            assertThat((Map<?, ?>) entry).containsEntry("test", "foo");

            assertThat(reader.nextEntry()).isNull();
        }
    }

    /**
     * Verify a list with a single element can be read through as a Zip file.
     */
    public void singleElementListZip() throws IOException {
        try (BdioReader reader = new BdioReader(zipBytes("[ \"test\" ]"))) {
            Object entry = reader.nextEntry();
            assertThat(entry).isInstanceOf(List.class);
            assertThat((List<?>) entry).containsExactly("test");

            assertThat(reader.nextEntry()).isNull();
        }
    }

    /**
     * Verify multiple entries can be read through from a Zip file.
     */
    @Test
    public void multipleEntryZip() throws IOException {
        try (BdioReader reader = new BdioReader(zipBytes("{ \"test\" : \"foo\" }", "{ \"test\" : \"bar\" }"))) {
            Object entry1 = reader.nextEntry();
            assertThat(entry1).isInstanceOf(Map.class);
            assertThat((Map<?, ?>) entry1).containsEntry("test", "foo");

            Object entry2 = reader.nextEntry();
            assertThat(entry2).isInstanceOf(Map.class);
            assertThat((Map<?, ?>) entry2).containsEntry("test", "bar");

            assertThat(reader.nextEntry()).isNull();
        }
    }

    /**
     * Verify that if the only entry in a Zip file does not have a ".jsonld" suffix, no entries are returned.
     */
    @Test
    public void ignoreNonEntries_onlyOne() throws IOException {
        HeapOutputStream buffer = new HeapOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry(UUID.randomUUID() + ".json"));
            ByteStreams.copy(utfBytes("{ \"test\" : \"foo\" }"), zip);
        }
        try (BdioReader reader = new BdioReader(buffer.getInputStream())) {
            assertThat(reader.nextEntry()).isNull();
        }
    }

    /**
     * Verify that leading non-JSON-LD entries are ignored.
     */
    @Test
    public void ignoreNonEntries_first() throws IOException {
        HeapOutputStream buffer = new HeapOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry(UUID.randomUUID() + ".json"));
            ByteStreams.copy(utfBytes("{ \"test\" : \"foo\" }"), zip);

            zip.putNextEntry(new ZipEntry(UUID.randomUUID() + ".jsonld"));
            ByteStreams.copy(utfBytes("{ \"test\" : \"bar\" }"), zip);
        }
        try (BdioReader reader = new BdioReader(buffer.getInputStream())) {
            assertThat((Map<?, ?>) reader.nextEntry()).containsEntry("test", "bar");
            assertThat(reader.nextEntry()).isNull();
        }
    }

    /**
     * Verify that trailing non-JSON-LD entries are ignored.
     */
    @Test
    public void ignoreNonEntries_last() throws IOException {
        HeapOutputStream buffer = new HeapOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry(UUID.randomUUID() + ".jsonld"));
            ByteStreams.copy(utfBytes("{ \"test\" : \"bar\" }"), zip);

            zip.putNextEntry(new ZipEntry(UUID.randomUUID() + ".json"));
            ByteStreams.copy(utfBytes("{ \"test\" : \"foo\" }"), zip);
        }
        try (BdioReader reader = new BdioReader(buffer.getInputStream())) {
            assertThat((Map<?, ?>) reader.nextEntry()).containsEntry("test", "bar");
            assertThat(reader.nextEntry()).isNull();
        }
    }

}
