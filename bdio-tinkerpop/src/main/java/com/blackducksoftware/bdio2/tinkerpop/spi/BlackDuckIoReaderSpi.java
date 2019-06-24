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
package com.blackducksoftware.bdio2.tinkerpop.spi;

import static com.blackducksoftware.common.base.ExtraThrowables.illegalState;
import static java.util.Comparator.comparing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.reactivestreams.Publisher;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.bdio2.tinkerpop.util.UnknownProperties;
import com.blackducksoftware.common.value.HID;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;

import io.reactivex.Flowable;

public abstract class BlackDuckIoReaderSpi extends AbstractBlackDuckIoSpi {

    // TODO Any where we use property names, use the frame

    protected BlackDuckIoReaderSpi(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        super(traversal, options, frame);
    }

    public abstract Publisher<?> persistFramedEntries(Flowable<Map<String, Object>> framedEntries);

    public void persistMetadata(BdioMetadata metadata) {
        // Make sure we have a vertex label to use for persisting metadata
        String metadataLabel = options().metadataLabel().orElseThrow(illegalState("metadata label should be configured"));

        // Convert the map into a key/value list
        List<Object> keyValues = new ArrayList<>();
        getMetadataProperties(metadata, (k, v) -> {
            if (k != T.id) {
                keyValues.add(k);
                keyValues.add(v);
            }
        });

        // There can only be one vertex with the metadata label, upsert accordingly
        GraphTraversalSource g = traversal();
        Vertex vertex = g.V().hasLabel(metadataLabel).tryNext().orElseGet(() -> g.addV(metadataLabel).next());
        ElementHelper.attachProperties(vertex, keyValues.toArray());
    }

    protected void getMetadataProperties(Map<String, Object> metadata, BiConsumer<Object, Object> properties) {
        // A BdioMetadata instance should effectively be expanded data so we need to compact it using the graph context
        JsonLdOptions opts = frame().context().jsonLdOptions();
        Object expandContext = opts.getExpandContext();
        opts.setExpandContext(null);

        Map<String, Object> metadataNode = JsonLdProcessor.compact(metadata, expandContext, opts);
        options().metadataLabel().ifPresent(type -> metadataNode.put(JsonLdConsts.TYPE, type));
        if (metadata.size() == 1 && metadata.containsKey(JsonLdConsts.ID)) {
            // JSON-LD compaction will remove the "@id" if it is the only field, put it back
            metadataNode.put(JsonLdConsts.ID, metadata.get(JsonLdConsts.ID));
        }

        // Use the regular node property extraction logic
        getNodeProperties(metadataNode, properties);
    }

    protected void getNodeProperties(Map<String, Object> node, BiConsumer<Object, Object> properties) {
        // Include the identifier
        Optional.ofNullable(node.get(JsonLdConsts.ID))
                .ifPresent(id -> properties.accept(T.id, id));

        // Graph label
        Optional.ofNullable(node.get(JsonLdConsts.TYPE))
                // TODO Validate that the label is a String, we can't support multiple values!
                .ifPresent(label -> properties.accept(T.label, label));

        // BDIO identifier preservation
        options().identifierKey().ifPresent(key -> {
            Object id = node instanceof BdioMetadata ? ((BdioMetadata) node).id() : node.get(JsonLdConsts.ID);
            if (id != null) {
                properties.accept(key, id);
            }
        });

        // Data properties
        node.entrySet().stream()
                .filter(e -> frame().isDataProperty(e.getKey()))
                .sorted(comparing(Map.Entry::getKey))
                .forEachOrdered(e -> {
                    properties.accept(e.getKey(), frame().context().fromFieldValue(e.getKey(), e.getValue()));
                });

        // Unknown properties
        // TODO Is this doing anything or did we loose it in the framing?
        UnknownProperties.preserve(options().unknownKey(), node, properties);

        // File parent, even if the path or parent is null, it must be included
        if (options().fileParentKey().isPresent() && node.containsKey(Bdio.DataProperty.path.name())) {
            String parent;
            try {
                parent = Optional.ofNullable(node.get(Bdio.DataProperty.path.name()))
                        .map(HID::from).flatMap(HID::tryParent).map(HID::toUriString)
                        .orElse(null);
            } catch (IllegalArgumentException e) {
                parent = null;
            }
            properties.accept(options().fileParentKey().get(), parent);
        }
    }

    protected void getNodeEdges(Map<String, Object> node, BiConsumer<String, Object> edges) {
        node.forEach((term, value) -> {
            if (frame().isObjectProperty(term)) {

                if (value instanceof List<?>) {
                    for (Object file : ((List<?>) value)) {
                        edges.accept(term, frame().context().fromFieldValue(term, file));
                    }

                } else {
                    edges.accept(term, frame().context().fromFieldValue(term, value));
                }

            }
        });
    }

}
