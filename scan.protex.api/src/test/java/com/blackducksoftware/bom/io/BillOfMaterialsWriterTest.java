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
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.ImmutableNode;
import com.blackducksoftware.bom.SpdxTerm;
import com.google.common.base.Joiner;
import com.google.common.hash.Hashing;
import com.google.common.net.MediaType;

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
                    .put(BlackDuckTerm.CONTENT_TYPE, MediaType.OCTET_STREAM)
                    .put(BlackDuckTerm.MD5, Hashing.md5().hashBytes(new byte[] { 1, 2, 3 }))
                    .put(BlackDuckTerm.SHA1, Hashing.sha1().hashBytes(new byte[] { 1, 2, 3 }))
                    .put(BlackDuckTerm.SIZE, 10L)
                    .build());
        }

        // TODO There appears to better ways of doing this:
        // http://stackoverflow.com/questions/2253750/compare-two-json-objects-in-java
        assertThat(new String(baos.toByteArray(), UTF_8)).isEqualTo(Joiner.on('\n').join(new String[] { "[ {",
                "  \"@id\" : \"foo\",",
                "  \"@type\" : \"File\",",
                "  \"spdx:fileName\" : \"./foo/bar\",",
                "  \"contentType\" : \"application/octet-stream\",",
                "  \"size\" : 10,",
                "  \"sha1\" : \"7037807198c22a7d2b0807371d763779a84fdfcf\",",
                "  \"md5\" : \"5289df737df57326fcdd22597afb1fac\"",
                "} ]" }));
    }
}
