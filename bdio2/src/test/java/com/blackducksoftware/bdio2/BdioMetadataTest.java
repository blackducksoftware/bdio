/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio2;

import static com.google.common.truth.Truth.assertThat;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.Test;

import com.blackducksoftware.bdio2.Bdio.Container;
import com.blackducksoftware.bdio2.Bdio.Datatype;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
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
     * You can't merge unless the identifiers match.
     */
    @Test(expected = IllegalArgumentException.class)
    public void mergeMetadataDifferentIdentifiers() {
        new BdioMetadata().id("123").merge(new BdioMetadata().id("456"));

    }

    // TODO Explicitly test merge conflict behavior (e.g. last value wins)

    @Test
    public void compact() throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put(JsonLdConsts.ID, dataProperty.toString());
            if (dataProperty.type() != Datatype.Default) {
                definition.put(JsonLdConsts.TYPE, dataProperty.type().toString());
            }
            if (dataProperty.container() == Container.ordered) {
                definition.put(JsonLdConsts.CONTAINER, JsonLdConsts.LIST);
            } else if (dataProperty.container() == Container.unordered) {
                definition.put(JsonLdConsts.CONTAINER, JsonLdConsts.SET);
            }
            context.put(dataProperty.name(), definition);
            // context.put(dataProperty.toString(), definition);
        }

        BdioMetadata metadata = new BdioMetadata();
        metadata.id("urn:uuid:" + UUID.randomUUID());
        metadata.creation(Instant.now());

        Map<String, Object> metadata2 = new LinkedHashMap<>();
        metadata2.put("@id", "urn:uuid:" + UUID.randomUUID());
        metadata2.put("http://blackducksoftware.com/rdf/terms#hasCreation", Instant.now().toString());
        // metadata2.put("creation", Instant.now().toString());

        JsonLdOptions opts = new JsonLdOptions();
        opts.setExpandContext(context);

        Object result = metadata;
        // result = JsonLdProcessor.compact(metadataresult, context, opts);
        // result = JsonLdProcessor.expand(result, opts);
        result = JsonLdProcessor.compact(result, context, opts);

        JsonUtils.writePrettyPrint(new PrintWriter(System.out), result);

        System.out.println("\n\n====\n");

        JsonUtils.writePrettyPrint(new PrintWriter(System.out), metadata);

    }

}
