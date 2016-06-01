/*
 * Copyright 2015 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio.io;

import static com.google.common.truth.Truth.assertThat;

import java.net.URI;

import org.junit.Test;

public class LinkedDataContextTest {

    @Test
    public void testBaseUri() {
        String base = "http://example.com/test";
        LinkedDataContext context = new LinkedDataContext(base);
        assertThat(context.getBase()).isEqualTo(URI.create(base));
        assertThat(context.getBase().isAbsolute()).isTrue();
    }

    @Test
    public void testNullBaseUri() {
        assertThat(new LinkedDataContext().getBase()).isNull();
        assertThat(new LinkedDataContext(null).getBase()).isNull();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBaseUri() {
        new LinkedDataContext(":");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRelativeBaseUri() {
        new LinkedDataContext("/foo");
    }

}
