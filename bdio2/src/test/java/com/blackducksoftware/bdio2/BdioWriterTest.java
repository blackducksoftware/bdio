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
package com.blackducksoftware.bdio2;

import static com.blackducksoftware.common.test.JsonSubject.assertThatJson;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.blackducksoftware.bdio2.BdioWriter.BdioFile;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.bdio2.test.BdioTest;
import com.blackducksoftware.common.io.HeapOutputStream;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

/**
 * Tests for {@link BdioWriter}.
 *
 * @author jgustie
 */
public class BdioWriterTest {

    private static final StreamSupplier NULL_STREAM_SUPPLIER = new StreamSupplier() {
        @Override
        public OutputStream newStream() throws IOException {
            return ByteStreams.nullOutputStream();
        }
    };

    private BdioMetadata metadata;

    @Before
    public void createMetadata() {
        metadata = BdioMetadata.createRandomUUID();
    }

    @SuppressWarnings("resource")
    @Test(expected = NullPointerException.class)
    public void nullMetadata() {
        new BdioWriter(null, NULL_STREAM_SUPPLIER);
    }

    @SuppressWarnings("resource")
    @Test(expected = NullPointerException.class)
    public void nullStreamSupplier() {
        new BdioWriter(metadata, null);
    }

    @Test(expected = IllegalStateException.class)
    public void doubleStart() throws IOException {
        try (BdioWriter writer = new BdioWriter(metadata, NULL_STREAM_SUPPLIER)) {
            writer.start();
            writer.start();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void noStart() throws IOException {
        try (BdioWriter writer = new BdioWriter(metadata, NULL_STREAM_SUPPLIER)) {
            writer.next(new HashMap<>());
        }
    }

    @Test
    public void idempotentClose() throws IOException {
        OutputStream out = mock(OutputStream.class);
        BdioWriter writer = new BdioWriter(metadata, new BdioFile(out));
        writer.close();
        writer.close();
        verify(out, times(1)).close();
    }

    @Test
    public void noNextCalls() throws IOException {
        HeapOutputStream buffer = new HeapOutputStream();
        try (BdioWriter writer = new BdioWriter(metadata, new BdioFile(buffer))) {
            writer.start();
        }
        List<String> entries = BdioTest.zipEntries(buffer.getInputStream());
        assertThat(entries).hasSize(1);
        assertThatJson(entries.get(0)).at("/@id").isEqualTo(metadata.id());
        assertThatJson(entries.get(0)).arrayAt("/@graph").hasSize(0);
    }

    @Test
    public void twoNextCalls() throws IOException {
        HeapOutputStream buffer = new HeapOutputStream();
        try (BdioWriter writer = new BdioWriter(metadata, new BdioFile(buffer))) {
            writer.start();
            writer.next(ImmutableMap.of("test", "foo"));
            writer.next(ImmutableMap.of("test", "bar"));
        }
        List<String> entries = BdioTest.zipEntries(buffer.getInputStream());
        assertThat(entries).hasSize(2);

        assertThatJson(entries.get(0)).at("/@id").isEqualTo(metadata.id());
        assertThatJson(entries.get(0)).arrayAt("/@graph").hasSize(0);
        assertThatJson(entries.get(1)).at("/@id").isEqualTo(metadata.id());
        assertThatJson(entries.get(1)).at("/@graph/0/test").isEqualTo("foo");
        assertThatJson(entries.get(1)).at("/@graph/1/test").isEqualTo("bar");
    }

}
