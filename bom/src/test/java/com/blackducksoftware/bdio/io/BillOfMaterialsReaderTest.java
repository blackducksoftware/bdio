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
import static com.google.common.truth.TruthJUnit.assume;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.junit.Test;

import com.blackducksoftware.bdio.BlackDuckTerm;
import com.blackducksoftware.bdio.BlackDuckType;
import com.blackducksoftware.bdio.Node;
import com.blackducksoftware.bdio.SpdxTerm;
import com.blackducksoftware.bdio.SpdxType;
import com.blackducksoftware.bdio.SpdxValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;

/**
 * Tests for the Bill of Materials Reader.
 *
 * @author jgustie
 */
public class BillOfMaterialsReaderTest {

    @Test
    public void testSimpleRead() throws Exception {
        LinkedDataContext context = new LinkedDataContext();
        Reader in = new StringReader("[ {"
                + "  \"@id\": \"foo\","
                + "  \"@type\": \"File\","
                + "  \"fileName\": \"./foo\","
                + "  \"fileType\": [ \"SOURCE\" ]"
                + " }, {"
                + "  \"@id\": \"foo\","
                + "  \"@type\": \"File\","
                + "  \"size\": 333,"
                + "  \"checksum\": {"
                + "    \"algorithm\": \"sha1\","
                + "    \"checksumValue\": \"9069ca78e7450a285173431b3e52c5c25299e473\""
                + "  }"
                + "} ]");

        try (BillOfMaterialsReader reader = new BillOfMaterialsReader(context, in)) {
            Node node1 = reader.read();
            assertThat(node1.id()).isEqualTo("foo");
            assertThat(node1.types()).containsExactly(BlackDuckType.FILE);
            assertThat(node1.data()).isEqualTo(ImmutableMap.builder()
                    .put(SpdxTerm.FILE_NAME, "./foo")
                    .put(SpdxTerm.FILE_TYPE, ImmutableList.of(SpdxValue.FILE_TYPE_SOURCE.id()))
                    .build());

            Node node2 = reader.read();
            assertThat(node2.id()).isEqualTo("foo");
            assertThat(node2.types()).containsExactly(BlackDuckType.FILE);
            assertThat(node2.data()).isEqualTo(ImmutableMap.builder()
                    .put(BlackDuckTerm.SIZE, 333L)
                    .put(SpdxTerm.CHECKSUM, ImmutableMap.builder()
                            .put(JsonLdKeyword.TYPE.toString(), ImmutableList.of(SpdxType.CHECKSUM.toString()))
                            .put(SpdxTerm.ALGORITHM.toString(), SpdxValue.CHECKSUM_ALGORITHM_SHA1.id())
                            .put(SpdxTerm.CHECKSUM_VALUE.toString(), Hashing.sha1().hashInt(0).toString())
                            .build())
                    .build());

            assertThat(reader.read()).isNull();
        }
    }

    @Test
    public void testVersionChange() throws IOException {
        // Create a new context and make sure we are going to see interesting results
        LinkedDataContext context = new LinkedDataContext();
        assume().that(context.getSpecVersion()).isNotEqualTo("1.0.0");

        Reader in = new StringReader("[ {"
                + "  \"@id\": \"foo\","
                + "  \"@type\": \"BillOfMaterials\","
                + "  \"specVersion\": \"1.0.0\""
                + " } ]");

        try (BillOfMaterialsReader reader = new BillOfMaterialsReader(context, in)) {
            Node node = reader.read();
            assertThat(node.data().get(BlackDuckTerm.SPEC_VERSION)).isEqualTo("1.0.0");
            assertThat(reader.context().getSpecVersion()).isEqualTo("1.0.0");
            assertThat(reader.context()).isNotSameAs(context);
        }
    }

}
