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
