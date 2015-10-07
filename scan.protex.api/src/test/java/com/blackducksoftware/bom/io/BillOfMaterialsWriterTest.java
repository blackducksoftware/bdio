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

import org.testng.annotations.Test;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.ImmutableNode;
import com.blackducksoftware.bom.SpdxTerm;
import com.google.common.base.Joiner;

/**
 * Tests for the Bill of Materials Writer.
 *
 * @author jgustie
 */
public class BillOfMaterialsWriterTest {

    @Test
    public void simpleWriteFileModelTest() throws IOException {
        LinkedDataContext context = new LinkedDataContext("");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BillOfMaterialsWriter writer = new BillOfMaterialsWriter(context, baos)) {
            writer.write(ImmutableNode.builder()
                    .id("foo")
                    .addType(BlackDuckType.FILE)
                    .put(SpdxTerm.FILE_NAME, "./foo/bar")
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
                "  \"size\" : 10",
                "} ]" }));
    }
}
