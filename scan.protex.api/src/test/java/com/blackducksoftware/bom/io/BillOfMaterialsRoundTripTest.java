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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.testng.annotations.Test;

import com.blackducksoftware.bom.BlackDuckValue;
import com.blackducksoftware.bom.Node;
import com.blackducksoftware.bom.SpdxValue;
import com.blackducksoftware.bom.model.Checksum;
import com.blackducksoftware.bom.model.File;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;

public class BillOfMaterialsRoundTripTest {

    @Test
    public void testFileRoundTrip() throws IOException {
        File expectedFile = new File();
        File actualFile = new File();

        expectedFile.setId("http://example.com/test");
        expectedFile.setFileType(ImmutableSet.of(BlackDuckValue.FILE_TYPE_DIRECTORY.id(), SpdxValue.FILE_TYPE_SOURCE.id()));
        expectedFile.setChecksum(ImmutableList.of(Checksum.sha1(HashCode.fromInt(0))));

        LinkedDataContext context = new LinkedDataContext();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BillOfMaterialsWriter writer = new BillOfMaterialsWriter(context, out)) {
            writer.write(expectedFile);
        }
        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        StringReader in = new StringReader(output);
        try (BillOfMaterialsReader reader = new BillOfMaterialsReader(context, in)) {
            Node node = reader.read();
            actualFile.setId(node.id());
            actualFile.data().putAll(node.data());
        }

        assertThat(actualFile.getFileType()).containsExactlyElementsIn(expectedFile.getFileType());

    }

}
