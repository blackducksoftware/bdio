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
package com.blackducksoftware.bom.io;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.ImmutableNode;
import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

/**
 * Tests for the Bill of Materials Writer.
 *
 * @author jgustie
 */
public class BillOfMaterialsWriterTest {

    @Test
    public void simpleWriteFileModelTest() throws IOException {
        LinkedDataContext context = new LinkedDataContext();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BillOfMaterialsWriter writer = new BillOfMaterialsWriter(context, baos)) {
            writer.write(ImmutableNode.builder()
                    .id("foo")
                    .addType(BlackDuckType.FILE)
                    .put(SpdxTerm.FILE_NAME, "./foo/bar")
                    .put(SpdxTerm.FILE_TYPE, ImmutableSet.of(SpdxValue.FILE_TYPE_SOURCE.id()))
                    .put(BlackDuckTerm.SIZE, 10L)
                    .build());
        }

        // TODO There appears to better ways of doing this:
        // http://stackoverflow.com/questions/2253750/compare-two-json-objects-in-java
        // TODO I have seen: testCompile 'org.skyscreamer:jsonassert:1.2.1'
        assertThat(new String(baos.toByteArray(), UTF_8)).isEqualTo(Joiner.on('\n').join(new String[] { "[ {",
                "  \"@id\" : \"foo\",",
                "  \"@type\" : \"File\",",
                "  \"fileName\" : \"./foo/bar\",",
                "  \"fileType\" : \"SOURCE\",",
                "  \"size\" : 10",
                "} ]" }));
    }
}
