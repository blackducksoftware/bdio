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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.junit.Test;

import com.blackducksoftware.bom.BlackDuckValue;
import com.blackducksoftware.bom.model.AbstractModel;
import com.blackducksoftware.bom.model.Component;
import com.blackducksoftware.bom.model.ExternalIdentifier;
import com.google.common.collect.Iterables;

public class VersionChangeTest {

    @Test
    public void testImportOld() throws IOException {
        LinkedDataContext context = new LinkedDataContext();
        Reader in = new StringReader("[ {"
                + "  \"@id\": \"foo\","
                + "  \"@type\": \"BillOfMaterials\","
                + "  \"specVersion\": \"1.0.0\""
                + "}, {"
                + "  \"@id\": \"log4j\","
                + "  \"@type\": \"Component\","
                + "  \"externalIdentifier\": {"
                + "    \"externalSystemTypeId\": \"BD-Suite\""
                + "  }"
                + "} ]");

        try (BillOfMaterialsReader reader = new BillOfMaterialsReader(context, in)) {
            // Eat the BOM node (should push the version back to 1.0.0)
            reader.read();
            assertThat(reader.context().getSpecVersion()).isEqualTo("1.0.0");

            // The next node is the component
            Component component = Iterables.getOnlyElement(AbstractModel.toModel(Component.class).apply(reader.read()));

            // Get the only external identifier we put in
            assertThat(component.getExternalIdentifiers()).hasSize(1);
            ExternalIdentifier externalIdentifier = component.externalIdentifiers().first().get();

            // Make sure the old "BD-Suite" value gets renamed correctly
            assertThat(externalIdentifier.getExternalSystemTypeId()).isEqualTo(BlackDuckValue.EXTERNAL_IDENTIFIER_BDSUITE.id());
        }
    }

}
