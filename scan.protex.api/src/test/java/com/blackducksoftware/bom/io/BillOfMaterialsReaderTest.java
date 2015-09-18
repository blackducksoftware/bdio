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

import org.junit.Test;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.Node;
import com.blackducksoftware.bom.SpdxTerm;
import com.google.common.collect.ImmutableMap;

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
                + "  \"spdx:fileName\": \"./foo\","
                + "  \"md5\" : \"5289df737df57326fcdd22597afb1fac\""
                + " }, {"
                + "  \"@id\": \"foo\","
                + "  \"@type\": \"File\","
                + "  \"contentType\": \"application/octet-stream\","
                + "  \"sha1\" : \"7037807198c22a7d2b0807371d763779a84fdfcf\""
                + "} ]");

        try (BillOfMaterialsReader reader = new BillOfMaterialsReader(context, in)) {
            Node node1 = reader.read();
            assertThat(node1.id()).isEqualTo("foo");
            assertThat(node1.types()).containsExactly(BlackDuckType.FILE);
            assertThat(node1.data()).isEqualTo(ImmutableMap.builder()
                    .put(SpdxTerm.FILE_NAME, "./foo")
                    .put(BlackDuckTerm.MD5, "5289df737df57326fcdd22597afb1fac")
                    .build());

            Node node2 = reader.read();
            assertThat(node2.id()).isEqualTo("foo");
            assertThat(node2.types()).containsExactly(BlackDuckType.FILE);
            assertThat(node2.data()).isEqualTo(ImmutableMap.builder()
                    .put(BlackDuckTerm.CONTENT_TYPE, "application/octet-stream")
                    .put(BlackDuckTerm.SHA1, "7037807198c22a7d2b0807371d763779a84fdfcf")
                    .build());

            assertThat(reader.read()).isNull();
        }
    }

}
