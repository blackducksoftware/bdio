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

import static com.google.common.truth.Truth.assertThat;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;

/**
 * Tests for {@link BdioDocument.Builder}.
 *
 * @author jgustie
 */
public class BdioDocumentBuilderTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void base_nullString() {
        BdioDocument doc = new BdioDocument.Builder().base((String) null).build(BuilderTestBdioDocument.class);
        assertThat(doc.options().getBase()).isEmpty();
    }

    @Test
    public void base_nullUri() {
        BdioDocument doc = new BdioDocument.Builder().base((URI) null).build(BuilderTestBdioDocument.class);
        assertThat(doc.options().getBase()).isEmpty();
    }

    @Test
    public void base_emptyString() {
        BdioDocument doc = new BdioDocument.Builder().base("").build(BuilderTestBdioDocument.class);
        assertThat(doc.options().getBase()).isEmpty();
    }

    @Test
    public void base_invalidString() {
        thrown.expect(IllegalArgumentException.class);
        new BdioDocument.Builder().base(":");
    }

    @Test
    public void base_relativeString() {
        thrown.expect(IllegalArgumentException.class);
        new BdioDocument.Builder().base("/");
    }

    @Test
    public void base_opaqueString() {
        thrown.expect(IllegalArgumentException.class);
        new BdioDocument.Builder().base("test:");
    }

    @Test
    public void expandContext_forJson_null() {
        BdioDocument doc = new BdioDocument.Builder().forJson(null).build(BuilderTestBdioDocument.class);
        assertThat(doc.options().getExpandContext()).isNull();
    }

    @Test
    public void expandContext_forJson_invalidType() {
        thrown.expect(IllegalArgumentException.class);
        new BdioDocument.Builder().forJson(0);
    }

    @Test
    public void expandContext_forJson_string() {
        // No verification is made on the string yet, we just need to ensure it passes through
        BdioDocument doc = new BdioDocument.Builder().forJson("").build(BuilderTestBdioDocument.class);
        assertThat(doc.options().getExpandContext()).isEqualTo("");
    }

    @Test
    public void expandContext_forJsonLd() {
        BdioDocument doc = new BdioDocument.Builder().forJsonLd().build(BuilderTestBdioDocument.class);
        assertThat(doc.options().getExpandContext()).isNull();
    }

    @Test
    public void expandContext_forContentType_jsonLd() {
        BdioDocument doc = new BdioDocument.Builder().forContentType(Bdio.ContentType.JSONLD, "foobar").build(BuilderTestBdioDocument.class);
        assertThat(doc.options().getExpandContext()).isNull();
    }

    /**
     * Unfortunate side-effect of the {@code BdioDocument} design is that it is not very testable; we can't mock out the
     * concrete implementation because it is always constructed reflectively.
     */
    public static final class BuilderTestBdioDocument extends BdioDocument {
        public BuilderTestBdioDocument(Builder builder) {
            super(builder);
        }

        @Override
        public Processor<Object, Object> processor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Subscriber<Map<String, Object>> asNodeSubscriber() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BdioDocument metadata(Consumer<BdioMetadata> metadataSubscriber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BdioDocument takeFirstMetadata(Consumer<BdioMetadata> metadataSubscriber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonLdProcessing jsonld() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BdioDocument writeToFile(BdioMetadata metadata, OutputStream out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BdioDocument read(InputStream in) {
            throw new UnsupportedOperationException();
        }
    }

}
