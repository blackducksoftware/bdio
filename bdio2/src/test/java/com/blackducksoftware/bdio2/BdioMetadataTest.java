/*
 * Copyright 2016 Black Duck Software, Inc.
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

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import java.util.stream.Stream;

import org.junit.Test;
import com.blackducksoftware.common.value.ProductList;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * Tests for {@link BdioMetadata}.
 *
 * @author jgustie
 */
public class BdioMetadataTest {

    /**
     * Creating a named graph with no arguments should include everything from the original metadata.
     */
    @Test
    public void generateNamedGraphAllKeysEmptyList() {
        BdioMetadata metadata = new BdioMetadata();
        metadata.put("test1", "foo");
        metadata.put("test2", "bar");
        assertThat(metadata.asNamedGraph()).containsExactly(
                "test1", "foo",
                "test2", "bar",
                "@graph", Lists.newArrayList());
    }

    /**
     * Creating a named graph with just a list should include everything from the original metadata.
     */
    @Test
    public void generateNamedGraphAllKeys() {
        BdioMetadata metadata = new BdioMetadata();
        metadata.put("test1", "foo");
        metadata.put("test2", "bar");
        assertThat(metadata.asNamedGraph(Lists.newArrayList("foobar"))).containsExactly(
                "test1", "foo",
                "test2", "bar",
                "@graph", Lists.newArrayList("foobar"));
    }

    /**
     * Creating a named graph filtering some keys.
     */
    @Test
    public void generateNamedGraphSomeKeys() {
        BdioMetadata metadata = new BdioMetadata();
        metadata.put("test1", "foo");
        metadata.put("test2", "bar");
        assertThat(metadata.asNamedGraph(Lists.newArrayList("foobar"), "test1")).containsExactly(
                "test1", "foo",
                "@graph", Lists.newArrayList("foobar"));
    }

    /**
     * Cannot generate a {@code null} graph.
     */
    @Test(expected = NullPointerException.class)
    public void generateNamedGraphNull() {
        new BdioMetadata().asNamedGraph(null);
    }

    /**
     * Merge disjoint metadata.
     */
    @Test
    public void mergeMetadata() {
        BdioMetadata metadata1 = new BdioMetadata();
        metadata1.put("test1", "foo");

        BdioMetadata metadata2 = new BdioMetadata();
        metadata2.put("test2", "bar");

        BdioMetadata merged = Stream.of(metadata1, metadata2).reduce(new BdioMetadata(), BdioMetadata::merge);
        assertThat(merged).containsExactly("test1", "foo", "test2", "bar");
    }

    /**
     * For most keys, merging will simply overwrite the previous value.
     */
    @Test
    public void mergeMetadataOverwrite() {
        BdioMetadata metadata = new BdioMetadata();
        metadata.put("test1", "foo");
        metadata.merge(ImmutableMap.of("test1", "bar"));
        assertThat(metadata).containsExactly("test1", "bar");
    }

    /**
     * You can't merge unless the identifiers match.
     */
    @Test(expected = IllegalArgumentException.class)
    public void mergeMetadataDifferentIdentifiers() {
        new BdioMetadata().id("123").merge(new BdioMetadata().id("456"));
    }

    /**
     * URI fragments do not qualify as "different identifiers" when merging.
     */
    @Test
    public void mergeMetadataIgnoreIdentifierFragment() {
        BdioMetadata metadata1 = new BdioMetadata().id("http://example.com/test#1");

        BdioMetadata metadata;

        metadata = new BdioMetadata().id("http://example.com/test");
        assertThat(metadata.merge(metadata1).get("@id")).isEqualTo("http://example.com/test");

        metadata = new BdioMetadata().id("http://example.com/test#1");
        assertThat(metadata.merge(metadata1).get("@id")).isEqualTo("http://example.com/test#1");

        metadata = new BdioMetadata().id("http://example.com/test#2");
        assertThat(metadata.merge(metadata1).get("@id")).isEqualTo("http://example.com/test");
    }

    /**
     * The metadata being merged into does not have an identifier yet, it gets established.
     */
    @Test
    public void mergeMetadataIntoMissingIdentifier() {
        assertThat(new BdioMetadata().merge(new BdioMetadata().id("123")).id()).isEqualTo("123");
    }

    /**
     * Merging publishers combines the values into a single ordered product list.
     */
    @Test
    public void mergeMetadataPublishers() {
        BdioContext context = BdioContext.getDefault();
        BdioMetadata metadataFoo = new BdioMetadata();
        context.putFieldValue(metadataFoo, Bdio.DataProperty.publisher, ProductList.parse("foo"));

        BdioMetadata metadataBar = new BdioMetadata();
        context.putFieldValue(metadataBar, Bdio.DataProperty.publisher, ProductList.parse("bar"));

        assertThat(metadataFoo.merge(metadataBar))
                .containsEntry(Bdio.DataProperty.publisher.toString(),
                        context.toFieldValue(Bdio.DataProperty.publisher.name(), ProductList.parse("foo bar")));
    }

    /**
     * An existing scanType cannot be mapped to a different scanType.
     */
    @Test(expected = IllegalArgumentException.class)
    public void mergeDifferentScanType() {
        BdioContext context = BdioContext.getDefault();
        BdioMetadata metadataFoo = new BdioMetadata();
        context.putFieldValue(metadataFoo, JsonLdConsts.TYPE, Bdio.ScanType.PACKAGE_MANAGER.getValue());

        BdioMetadata metadataBar = new BdioMetadata();
        context.putFieldValue(metadataBar, JsonLdConsts.TYPE, Bdio.ScanType.SIGNATURE.getValue());

        metadataFoo.merge(metadataBar);
    }

    @Test
    public void mergeValidScanType() {
        BdioContext context = BdioContext.getDefault();
        BdioMetadata metadataFoo = new BdioMetadata();

        BdioMetadata metadataBar = new BdioMetadata();
        context.putFieldValue(metadataBar, JsonLdConsts.TYPE, Bdio.ScanType.PACKAGE_MANAGER.getValue());

        assertThat(metadataFoo.merge(metadataBar)).containsEntry(JsonLdConsts.TYPE, Bdio.ScanType.PACKAGE_MANAGER.getValue());

        // Validate That the scanType is merged in correct format
        BdioMetadata metadataFooBarFoo = new BdioMetadata();

        BdioMetadata metadataFooBar = new BdioMetadata();
        context.putFieldValue(metadataFooBar, JsonLdConsts.TYPE, Collections.singletonList(Bdio.ScanType.SIGNATURE.getValue()));

        assertThat(metadataFooBarFoo.merge(metadataFooBar)).containsEntry(JsonLdConsts.TYPE, Bdio.ScanType.SIGNATURE.getValue());
    }

}
