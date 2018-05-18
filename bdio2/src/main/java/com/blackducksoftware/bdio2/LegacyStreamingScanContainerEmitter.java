/*
 * Copyright 2017 Black Duck Software, Inc.
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

import static com.blackducksoftware.bdio2.LegacyUtilities.averageEntryNodeCount;
import static com.blackducksoftware.bdio2.LegacyUtilities.estimateEntryOverhead;
import static com.blackducksoftware.bdio2.LegacyUtilities.estimateSize;
import static com.blackducksoftware.bdio2.LegacyUtilities.scanContainerObjectMapper;
import static com.blackducksoftware.bdio2.LegacyUtilities.toFileUri;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.LegacyScanContainerEmitter.LegacyScanNode;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.model.FileCollection;
import com.blackducksoftware.bdio2.model.Project;
import com.blackducksoftware.common.value.Product;
import com.blackducksoftware.common.value.ProductList;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

/**
 * An emitter that leverages more recent changes to the serialization order of scan container fields to stream the data
 * instead of loading it all into memory.
 *
 * @author jgustie
 */
class LegacyStreamingScanContainerEmitter extends LegacyJsonParserEmitter {

    private BdioMetadata metadata;

    private Function<File, Map<String, Object>> base;

    private Predicate<LegacyScanNode> isBase;

    private String hostName;

    private String baseDir;

    private List<Map<String, Object>> graph;

    private int estimatedSize;

    public LegacyStreamingScanContainerEmitter(InputStream inputStream) {
        super(scanContainerObjectMapper().getFactory(), inputStream);
    }

    @Override
    protected Object next(JsonParser jp) throws IOException {
        if (metadata == null) {
            if (jp.nextToken() != null) {
                if (!jp.isExpectedStartObjectToken()) {
                    throw new IOException("expected start object:  " + jp.getCurrentToken());
                }

                parseMetadata(jp);
                return metadata.asNamedGraph();
            }
        } else if (graph != null) {
            List<Map<String, Object>> graph = parseGraph(jp);
            if (!graph.isEmpty()) {
                return metadata.asNamedGraph(graph, JsonLdConsts.ID);
            }
        }
        return null;
    }

    private void parseMetadata(JsonParser jp) throws IOException {
        metadata = new BdioMetadata();
        String project = null;
        String release = null;
        String fieldName = jp.nextFieldName();
        while (fieldName != null) {
            switch (fieldName) {
            case "scanNodeList":
                finishMetadata(jp, metadata, project, release);
                return;
            case "hostName":
                hostName = jp.nextTextValue();
                metadata.creator(null, hostName);
                break;
            case "baseDir":
                baseDir = jp.nextTextValue();
                isBase = LegacyScanNode.isBase(baseDir);
                break;
            case "scannerVersion":
                metadata.merge(publisher(scanClient().version(jp.nextTextValue()).build()));
                break;
            case "signatureVersion":
                metadata.merge(publisher(scanClient().addCommentText("signature %s", jp.nextTextValue()).build()));
                break;
            case "name":
                metadata.name(Strings.emptyToNull(jp.nextTextValue()));
                break;
            case "createdOn":
                jp.nextToken();
                metadata.creationDateTime(jp.readValueAs(Date.class).toInstant().atZone(ZoneOffset.UTC));
                break;
            case "project":
                project = jp.nextTextValue();
                break;
            case "release":
                release = jp.nextTextValue();
                break;
            default:
                if (jp.nextToken().isStructStart()) {
                    jp.skipChildren();
                }
                break;
            }
            fieldName = jp.nextFieldName();
        }
    }

    private void finishMetadata(JsonParser jp, BdioMetadata metadata, @Nullable String project, @Nullable String release) throws IOException {
        // Add the metadata identifier and adjust the name to preserve the base directory
        Optional<String> name = Optional.ofNullable((String) metadata.get(Bdio.DataProperty.name.toString()));
        metadata.id(name.map(LegacyUtilities::toNameUri).orElseGet(() -> toFileUri(hostName, baseDir, null)));
        metadata.name(name.map(n -> String.format("%s <%s>", n, baseDir)).orElseGet(() -> String.format("<%s>", baseDir)));

        // Merge in additional product information for this code
        metadata.merge(publisher(new Product.Builder()
                .simpleName(LegacyScanContainerEmitter.class)
                .implementationVersion(LegacyScanContainerEmitter.class)
                .addComment("(streaming)")
                .build()));

        // Make sure we are actually looking at a "list"
        if (jp.nextToken() == null || !jp.isExpectedStartArrayToken()) {
            throw new IOException("expected start array: " + jp.getCurrentToken());
        }

        // Add the root object to the graph
        String rootId = toFileUri(hostName, baseDir, "root");
        if (project != null) {
            offer(new Project(rootId).name(project).version(release));
            base = f -> new Project(rootId).base(f);
        } else {
            offer(new FileCollection(rootId));
            base = f -> new FileCollection(rootId).base(f);
        }
    }

    private List<Map<String, Object>> parseGraph(JsonParser jp) throws IOException {
        while (jp.nextToken() != null) {
            if (jp.isExpectedStartObjectToken()) {
                // Read a scan node and convert it into a BDIO file
                LegacyScanNode scanNode = jp.readValueAs(LegacyScanNode.class);
                File file = new File(toFileUri(hostName, baseDir, scanNode.toString()))
                        .fileSystemType(scanNode.fileSystemType())
                        .path(scanNode.path(baseDir, null))
                        .byteCount(scanNode.byteCount())
                        .fingerprint(scanNode.fingerprint());

                // Add a base relationship
                if (isBase.test(scanNode)) {
                    offer(base.apply(file));
                }

                if (!offer(file)) {
                    // The entry is full, finish the current entry and start a new one by re-offering the file
                    List<Map<String, Object>> result = finishEntry();
                    offer(file);
                    return result;
                }
            } else if (jp.hasToken(JsonToken.END_ARRAY)) {
                // This is end of the scan node list, we can ignore the result of the file
                return finishEntry();
            }
        }

        // We should hit the end of the array before we run out of tokens
        throw new IOException("Unexpected end of stream");
    }

    /**
     * Attempts to add the specified node to the current graph buffer. Returns {@code true} if the node was added,
     * {@code false} if the buffer is full.
     */
    private boolean offer(Map<String, Object> node) {
        // Create a new buffer to hold BDIO nodes
        if (graph == null) {
            graph = new ArrayList<>(averageEntryNodeCount());
            estimatedSize = estimateEntryOverhead(metadata);
        }

        // Add the node if it fits
        int nodeSize = estimateSize(node);
        if (estimatedSize + nodeSize < Bdio.MAX_ENTRY_SIZE) {
            estimatedSize += nodeSize;
            return graph.add(node);
        } else {
            return false;
        }
    }

    /**
     * Resets and returns the current graph buffer.
     */
    private List<Map<String, Object>> finishEntry() {
        if (graph != null && !graph.isEmpty()) {
            List<Map<String, Object>> result = graph;
            graph = null;
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    private static Map<String, Object> publisher(Product product) {
        // Technically this should be a JSON-LD value object, however we might be able to sneak it through...
        return ImmutableMap.of(Bdio.DataProperty.publisher.toString(), ProductList.of(product));
    }

    private static Product.Builder scanClient() {
        return new Product.Builder().name("ScanClient");
    }

}
