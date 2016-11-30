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

import static com.blackducksoftware.common.test.JsonSubject.assertThatJson;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

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

    private BdioMetadata metadata;

    @Before
    public void createMetadata() {
        metadata = new BdioMetadata("urn:uuid:" + UUID.randomUUID());
    }

    @SuppressWarnings("resource")
    @Test(expected = NullPointerException.class)
    public void nullMetadata() {
        new BdioWriter(null, ByteStreams.nullOutputStream());
    }

    @SuppressWarnings("resource")
    @Test(expected = NullPointerException.class)
    public void nullOutputStream() {
        new BdioWriter(metadata, null);
    }

    @Test(expected = IllegalStateException.class)
    public void doubleStart() throws IOException {
        try (BdioWriter writer = new BdioWriter(metadata, ByteStreams.nullOutputStream())) {
            writer.start();
            writer.start();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void noStart() throws IOException {
        try (BdioWriter writer = new BdioWriter(metadata, ByteStreams.nullOutputStream())) {
            writer.next(new HashMap<>());
        }
    }

    @Test
    public void idempotentClose() throws IOException {
        OutputStream out = mock(OutputStream.class);
        BdioWriter writer = new BdioWriter(metadata, out);
        writer.close();
        writer.close();
        verify(out, times(1)).close();
    }

    @Test
    public void noNextCalls() throws IOException {
        HeapOutputStream buffer = new HeapOutputStream();
        try (BdioWriter writer = new BdioWriter(metadata, buffer)) {
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
        try (BdioWriter writer = new BdioWriter(metadata, buffer)) {
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
