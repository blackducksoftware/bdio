/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 * 
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bom.io;

import static com.google.common.truth.Truth.assertThat;

import java.io.Reader;
import java.io.StringReader;

import org.testng.annotations.Test;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.Node;
import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxType;
import com.blackducksoftware.bom.SpdxValue;
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
        LinkedDataContext context = new LinkedDataContext("");
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
                            .put(JsonLdTerm.TYPE.toString(), ImmutableList.of(SpdxType.CHECKSUM.toString()))
                            .put(SpdxTerm.ALGORITHM.toString(), SpdxValue.CHECKSUM_ALGORITHM_SHA1.id())
                            .put(SpdxTerm.CHECKSUM_VALUE.toString(), Hashing.sha1().hashInt(0).toString())
                            .build())
                    .build());

            assertThat(reader.read()).isNull();
        }
    }

}
