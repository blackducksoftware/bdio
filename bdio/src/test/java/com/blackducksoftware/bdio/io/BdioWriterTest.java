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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

import com.blackducksoftware.bdio.BlackDuckTerm;
import com.blackducksoftware.bdio.BlackDuckType;
import com.blackducksoftware.bdio.ImmutableNode;
import com.blackducksoftware.bdio.SpdxTerm;
import com.blackducksoftware.bdio.SpdxValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;

/**
 * Tests for the Bill of Materials Writer.
 *
 * @author jgustie
 */
public class BdioWriterTest {

    @Test
    public void simpleWriteFileModelTest() throws IOException {
        LinkedDataContext context = new LinkedDataContext();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BdioWriter writer = new BdioWriter(context, baos)) {
            writer.write(ImmutableNode.builder()
                    .id("foo")
                    .addType(BlackDuckType.FILE)
                    .put(SpdxTerm.FILE_NAME, "./foo/bar")
                    .put(SpdxTerm.FILE_TYPE, ImmutableSet.of(SpdxValue.FILE_TYPE_SOURCE.id()))
                    .put(BlackDuckTerm.SIZE, 10L)
                    .build());
        }

        final ObjectMapper objectMapper = new ObjectMapper();
        assertThat(objectMapper.readTree(baos.toByteArray())).isEqualTo(objectMapper.readTree("[ {"
                + "  \"@id\" : \"foo\","
                + "  \"@type\" : \"File\","
                + "  \"fileName\" : \"./foo/bar\","
                + "  \"fileType\" : \"SOURCE\","
                + "  \"size\" : 10"
                + "} ]"));
    }
}
