/*
 * Copyright 2019 Synopsys, Inc.
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

import org.junit.Test;

/**
 * Tests for {@code Bdio}.
 *
 * @author jgustie
 */
public class BdioTest {

    @Test
    public void contentType_forMediaType() {
        assertThat(Bdio.ContentType.forMediaType("application/ld+json")).isEqualTo(Bdio.ContentType.JSONLD);
        assertThat(Bdio.ContentType.forMediaType("application/json")).isEqualTo(Bdio.ContentType.JSON);
        assertThat(Bdio.ContentType.forMediaType("application/vnd.blackducksoftware.bdio+json")).isEqualTo(Bdio.ContentType.BDIO_JSON);
        assertThat(Bdio.ContentType.forMediaType("application/vnd.blackducksoftware.bdio+zip")).isEqualTo(Bdio.ContentType.BDIO_ZIP);
    }

    @Test
    public void contentType_forMediaType_charset_utf8() {
        assertThat(Bdio.ContentType.forMediaType("application/json; charset=UTF-8")).isEqualTo(Bdio.ContentType.JSON);
    }

    @Test(expected = IllegalArgumentException.class)
    public void contentType_forMediaType_charset_ascii() {
        assertThat(Bdio.ContentType.forMediaType("application/json; charset=ASCII")).isEqualTo(Bdio.ContentType.JSON);
    }

}
