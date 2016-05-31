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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.blackducksoftware.bdio.BlackDuckValue;
import com.blackducksoftware.bdio.Node;
import com.blackducksoftware.bdio.SpdxValue;
import com.blackducksoftware.bdio.model.Checksum;
import com.blackducksoftware.bdio.model.File;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;

public class BdioRoundTripTest {

    @Test
    public void testFileRoundTrip() throws IOException {
        File expectedFile = new File();
        File actualFile = new File();

        expectedFile.setId("http://example.com/test");
        expectedFile.setFileTypes(ImmutableSet.of(BlackDuckValue.FILE_TYPE_DIRECTORY.id(), SpdxValue.FILE_TYPE_SOURCE.id()));
        expectedFile.setChecksums(ImmutableList.of(Checksum.sha1(HashCode.fromInt(0))));

        LinkedDataContext context = new LinkedDataContext();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BdioWriter writer = new BdioWriter(context, out)) {
            writer.write(expectedFile);
        }
        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        StringReader in = new StringReader(output);
        try (BdioReader reader = new BdioReader(context, in)) {
            Node node = reader.read();
            actualFile.setId(node.id());
            actualFile.data().putAll(node.data());
        }

        assertThat(actualFile.getFileTypes()).containsExactlyElementsIn(expectedFile.getFileTypes());

    }

}
