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
import static com.google.common.truth.Truth8.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.blackducksoftware.common.value.ProductList;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.io.CharSource;

/**
 * Tests for {@code LegacyBdio1xEmitter}.
 *
 * @author jgustie
 */
public class LegacyBdio1xEmitterTest {

    private static final BdioContext context = BdioContext.getDefault();

    private static final String idKey = JsonLdConsts.ID;

    private static final String nameKey = Bdio.DataProperty.name.toString();

    private static final String publisherKey = Bdio.DataProperty.publisher.toString();

    private static final String creatorKey = Bdio.DataProperty.creator.toString();

    private static final String creationDateTimeKey = Bdio.DataProperty.creationDateTime.toString();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /**
     * A missing creator tool version just skips the version.
     */
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
        assertThat(metadata).doesNotContainEntry(idKey, "urn:uuid:c4c37b94-0c57-4d59-abc4-630e631af7a9");
        assertThat(context.getFieldValue(nameKey, metadata))
                .containsExactly("Test Metadata 1");
        assertThat(context.getFieldValue(publisherKey, metadata))
                .containsExactly(ProductList.from("Example LegacyBdio1xEmitter (bdio 1.1.0)"));
        assertThat(context.getFieldValue(creationDateTimeKey, metadata))
                .containsExactly(ZonedDateTime.parse("2016-11-22T16:33:20.000Z"));
        assertThat(metadata).doesNotContainKey(creatorKey);
    }

    /**
     * In SPDX, creator tool names may contain spaces.
     */
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
        assertThat(metadata).doesNotContainEntry(idKey, "urn:uuid:9240c6e7-abb1-435f-ace5-abc7277bbc4f");
        assertThat(context.getFieldValue(nameKey, metadata))
                .containsExactly("Test Metadata 2");
        assertThat(context.getFieldValue(publisherKey, metadata))
                .containsExactly(ProductList.from("BlackDuckIOProject/2.0.1 LegacyBdio1xEmitter (bdio 1.1.0)"));
        assertThat(context.getFieldValue(creationDateTimeKey, metadata))
                .containsExactly(ZonedDateTime.parse("2017-09-27T17:41:02.000Z"));
        assertThat(metadata).doesNotContainKey(creatorKey);
    }

    /**
     * We can extract the host name from identifier if it is a hierarchical, host bearing URI.
     */
    @Test
    public void creationInfo_creatorWithHost() throws IOException {
        InputStream inputStream = CharSource.wrap(""
                + "[ {"
                + "  \"@id\" : \"http://example.com/test\","
                + "  \"@type\" : \"BillOfMaterials\""
                + "} ]").asByteSource(UTF_8).openStream();
        Map<?, ?> metadata = (Map<?, ?>) new LegacyBdio1xEmitter(inputStream).stream().collect(onlyElement());
        assertThat(metadata).containsEntry(idKey, "http://example.com/test");
        assertThat(metadata).doesNotContainKey(nameKey);
        assertThat(context.getFieldValue(publisherKey, metadata))
                .containsExactly(ProductList.from("LegacyBdio1xEmitter (bdio 1.0.0)"));
        assertThat(metadata).doesNotContainKey(creationDateTimeKey);
        assertThat(context.getFieldValue(creatorKey, metadata))
                .containsExactly("@example.com");
    }

    /**
     * A person name (with spaces!) is successfully combined with the hostname.
     */
    @Test
    public void creationInfo_creatorWithPersonAndHost() throws IOException {
        InputStream inputStream = CharSource.wrap(""
                + "[ {"
                + "  \"@id\" : \"http://example.com/test\","
                + "  \"@type\" : \"BillOfMaterials\","
                + "  \"creationInfo\" : {"
                + "    \"spdx:creator\" : \"Person: Joe Shmoe\""
                + "  }"
                + "} ]").asByteSource(UTF_8).openStream();
        Map<?, ?> metadata = (Map<?, ?>) new LegacyBdio1xEmitter(inputStream).stream().collect(onlyElement());
        assertThat(metadata).containsEntry(idKey, "http://example.com/test");
        assertThat(metadata).doesNotContainKey(nameKey);
        assertThat(context.getFieldValue(publisherKey, metadata))
                .containsExactly(ProductList.from("LegacyBdio1xEmitter (bdio 1.0.0)"));
        assertThat(metadata).doesNotContainKey(creationDateTimeKey);
        assertThat(context.getFieldValue(creatorKey, metadata))
                .containsExactly("Joe Shmoe@example.com");
    }

    /**
     * Make sure we capture the host even when using a name on the BOM node.
     */
    @Test
    public void creationInfo_nameDoesNotImpactHost() throws IOException {
        InputStream inputStream = CharSource.wrap(""
                + "[ {"
                + "  \"@id\" : \"http://example.com/test\","
                + "  \"@type\" : \"BillOfMaterials\","
                + "  \"spdx:name\" : \"Test Metadata 3\","
                + "  \"creationInfo\" : {"
                + "    \"spdx:creator\" : \"Person: Joe Shmoe\""
                + "  }"
                + "} ]").asByteSource(UTF_8).openStream();
        Map<?, ?> metadata = (Map<?, ?>) new LegacyBdio1xEmitter(inputStream).stream().collect(onlyElement());
        assertThat(metadata).doesNotContainEntry(idKey, "http://example.com/test");
        assertThat(context.getFieldValue(nameKey, metadata))
                .containsExactly("Test Metadata 3");
        assertThat(context.getFieldValue(publisherKey, metadata))
                .containsExactly(ProductList.from("LegacyBdio1xEmitter (bdio 1.0.0)"));
        assertThat(metadata).doesNotContainKey(creationDateTimeKey);
        assertThat(context.getFieldValue(creatorKey, metadata))
                .containsExactly("Joe Shmoe@example.com");
    }

    /**
     * If the tool specification is empty we should safely ignore it.
     */
    @Test
    public void creationInfo_missingTool() throws IOException {
        InputStream inputStream = CharSource.wrap(""
                + "[ {"
                + "  \"@id\" : \"http://example.com/test\","
                + "  \"@type\" : \"BillOfMaterials\","
                + "  \"creationInfo\" : {"
                + "    \"spdx:creator\" : \"Tool: \""
                + "  }"
                + "} ]").asByteSource(UTF_8).openStream();
        Map<?, ?> metadata = (Map<?, ?>) new LegacyBdio1xEmitter(inputStream).stream().collect(onlyElement());
        assertThat(metadata).containsEntry(idKey, "http://example.com/test");
        assertThat(metadata).doesNotContainKey(nameKey);
        assertThat(context.getFieldValue(publisherKey, metadata))
                .containsExactly(ProductList.from("LegacyBdio1xEmitter (bdio 1.0.0)"));
        assertThat(metadata).doesNotContainKey(creationDateTimeKey);
        assertThat(context.getFieldValue(creatorKey, metadata))
                .containsExactly("@example.com");
    }

    /**
     * Treat the version as the product name token.
     */
    @Test
    public void creationInfo_versionOnlyTool() throws IOException {
        InputStream inputStream = CharSource.wrap(""
                + "[ {"
                + "  \"@id\" : \"http://example.com/test\","
                + "  \"@type\" : \"BillOfMaterials\","
                + "  \"creationInfo\" : {"
                + "    \"spdx:creator\" : \"Tool: 1.1.1\""
                + "  }"
                + "} ]").asByteSource(UTF_8).openStream();
        Map<?, ?> metadata = (Map<?, ?>) new LegacyBdio1xEmitter(inputStream).stream().collect(onlyElement());
        assertThat(metadata).containsEntry(idKey, "http://example.com/test");
        assertThat(metadata).doesNotContainKey(nameKey);
        assertThat(context.getFieldValue(publisherKey, metadata))
                .containsExactly(ProductList.from("1.1.1 LegacyBdio1xEmitter (bdio 1.0.0)"));
        assertThat(metadata).doesNotContainKey(creationDateTimeKey);
        assertThat(context.getFieldValue(creatorKey, metadata))
                .containsExactly("@example.com");
    }

    /**
     * An invalid specification version causes a failure.
     */
    @Test
    public void creationInfo_invalidSpecVersion() throws IOException {
        thrown.expect(IllegalArgumentException.class);
        InputStream inputStream = CharSource.wrap(""
                + "[ {"
                + "  \"@id\" : \"http://example.com/test\","
                + "  \"@type\" : \"BillOfMaterials\","
                + "  \"specVersion\" : \"1.2.0\""
                + "} ]").asByteSource(UTF_8).openStream();
        new LegacyBdio1xEmitter(inputStream).stream().iterator().next();
    }

}
