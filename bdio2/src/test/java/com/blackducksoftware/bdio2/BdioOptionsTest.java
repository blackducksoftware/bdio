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

import java.net.URI;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests for {@link BdioDocument.Builder}.
 *
 * @author jgustie
 */
public class BdioOptionsTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void base_nullString() throws Exception {
        BdioOptions options = new BdioOptions.Builder().base((String) null).build();
        assertThat(options.jsonLdOptions().getBase()).isEmpty();
    }

    @Test
    public void base_nullUri() throws Exception {
        BdioOptions options = new BdioOptions.Builder().base((URI) null).build();
        assertThat(options.jsonLdOptions().getBase()).isEmpty();
    }

    @Test
    public void base_emptyString() throws Exception {
        BdioOptions options = new BdioOptions.Builder().base("").build();
        assertThat(options.jsonLdOptions().getBase()).isEmpty();
    }

    @Test
    public void base_invalidString() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        new BdioOptions.Builder().base(":");
    }

    @Test
    public void base_relativeString() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        new BdioOptions.Builder().base("/");
    }

    @Test
    public void base_opaqueString() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        new BdioOptions.Builder().base("test:");
    }

    @Test
    public void expandContext_forJson_null() throws Exception {
        BdioOptions options = new BdioOptions.Builder().forJson(null).build();
        assertThat(options.jsonLdOptions().getExpandContext()).isNull();
    }

    @Test
    public void expandContext_forJson_invalidType() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        new BdioOptions.Builder().forJson(0);
    }

    @Test
    public void expandContext_forJson_string() throws Exception {
        // No verification is made on the string yet, we just need to ensure it passes through
        BdioOptions options = new BdioOptions.Builder().forJson("").build();
        assertThat(options.jsonLdOptions().getExpandContext()).isEqualTo("");
    }

    @Test
    public void expandContext_forJsonLd() throws Exception {
        BdioOptions options = new BdioOptions.Builder().forJsonLd().build();
        assertThat(options.jsonLdOptions().getExpandContext()).isNull();
    }

    @Test
    public void expandContext_forContentType_jsonLd() throws Exception {
        BdioOptions options = new BdioOptions.Builder().forContentType(Bdio.ContentType.JSONLD, "foobar").build();
        assertThat(options.jsonLdOptions().getExpandContext()).isNull();
    }

}
