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
