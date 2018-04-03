/*
 * Copyright 2018 Synopsys, Inc.
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
package com.blackducksoftware.bdio2;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.Test;

import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.common.value.ProductList;
import com.google.common.io.CharSource;

/**
 * Tests for {@code LegacyBdio1xEmitter}.
 *
 * @author jgustie
 */
public class LegacyBdio1xEmitterTest {

    private static final ValueObjectMapper mapper = ValueObjectMapper.getContextValueObjectMapper();

    private static final String nameKey = Bdio.DataProperty.name.toString();

    private static final String publisherKey = Bdio.DataProperty.publisher.toString();

    private static final String creationDateTimeKey = Bdio.DataProperty.creationDateTime.toString();

    @Test
    public void creationInfo_noCreatorVersion() throws IOException {
        InputStream inputStream = CharSource.wrap(""
                + "[ {"
                + "  \"@id\" : \"urn:uuid:c4c37b94-0c57-4d59-abc4-630e631af7a9\","
                + "  \"@type\" : \"BillOfMaterials\","
                + "  \"specVersion\" : \"1.1.0\","
                + "  \"spdx:name\" : \"Test Metadata 1\","
                + "  \"creationInfo\" : {"
                + "    \"spdx:creator\" : \"Tool: Example\","
                + "    \"spdx:created\" : \"2016-11-22T16:33:20.000Z\""
                + "  }"
                + "} ]").asByteSource(UTF_8).openStream();
        Map<?, ?> metadata = (Map<?, ?>) new LegacyBdio1xEmitter(inputStream).stream().collect(onlyElement());
        assertThat(mapper.fromFieldValue(nameKey, metadata.get(nameKey)))
                .isEqualTo("Test Metadata 1");
        assertThat(mapper.fromFieldValue(publisherKey, metadata.get(publisherKey)))
                .isEqualTo(ProductList.from("Example LegacyBdio1xEmitter (bdio 1.1.0)"));
        assertThat(mapper.fromFieldValue(creationDateTimeKey, metadata.get(creationDateTimeKey)))
                .isEqualTo(ZonedDateTime.parse("2016-11-22T16:33:20.000Z"));
    }

    @Test
    public void creationInfo_creatorWithSpaces() throws IOException {
        InputStream inputStream = CharSource.wrap(""
                + "[ {"
                + "  \"@id\" : \"urn:uuid:9240c6e7-abb1-435f-ace5-abc7277bbc4f\","
                + "  \"@type\" : \"BillOfMaterials\","
                + "  \"specVersion\" : \"1.1.0\","
                + "  \"spdx:name\" : \"Test Metadata 2\","
                + "  \"creationInfo\" : {"
                + "    \"spdx:creator\" : \"Tool: Black Duck I/O Project-2.0.1\","
                + "    \"spdx:created\" : \"2017-09-27T17:41:02.000Z\""
                + "  }"
                + "} ]").asByteSource(UTF_8).openStream();
        Map<?, ?> metadata = (Map<?, ?>) new LegacyBdio1xEmitter(inputStream).stream().collect(onlyElement());
        assertThat(mapper.fromFieldValue(nameKey, metadata.get(nameKey)))
                .isEqualTo("Test Metadata 2");
        assertThat(mapper.fromFieldValue(publisherKey, metadata.get(publisherKey)))
                .isEqualTo(ProductList.from("BlackDuckIOProject/2.0.1 LegacyBdio1xEmitter (bdio 1.1.0)"));
        assertThat(mapper.fromFieldValue(creationDateTimeKey, metadata.get(creationDateTimeKey)))
                .isEqualTo(ZonedDateTime.parse("2017-09-27T17:41:02.000Z"));
    }

}
