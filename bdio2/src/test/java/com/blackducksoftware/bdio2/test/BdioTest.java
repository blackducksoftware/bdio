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
package com.blackducksoftware.bdio2.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.blackducksoftware.common.io.HeapInputStream;
import com.blackducksoftware.common.io.HeapOutputStream;
import com.google.common.io.ByteStreams;

/**
 * Helpers for testing BDIO.
 * <p>
 * Methods must be extremely simple and self contained, we don't want bugs being masked.
 *
 * @author jgustie
 */
public final class BdioTest {

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
     * Returns a collection of strings representing each entry in a Zip file.
     */
    public static List<String> zipEntries(InputStream in) {
        List<String> result = new LinkedList<>();
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

    private BdioTest() {
        assert false;
    }
}
