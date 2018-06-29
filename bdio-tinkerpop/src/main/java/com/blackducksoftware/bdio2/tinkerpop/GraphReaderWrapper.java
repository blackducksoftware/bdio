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
package com.blackducksoftware.bdio2.tinkerpop;

import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static com.github.jsonldjava.core.JsonLdProcessor.compact;
import static java.util.Comparator.comparing;
import static org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality.single;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Graph.Features.VertexFeatures;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Context used when performing a
 * {@link org.apache.tinkerpop.gremlin.structure.io.GraphReader#readGraph(java.io.InputStream, Graph)
 * GraphReader.readGraph} operation on BDIO data.
 *
 * @author jgustie
 */
class GraphReaderWrapper extends GraphIoWrapper {

    /**
     * The number of mutations between commits.
     */
    private final int batchSize;

    /**
     * The number of observed mutations.
     */
    private final AtomicLong count;

    /**
     * Cached instance of vertex features.
     */
    private final VertexFeatures vertexFeatures;

    protected GraphReaderWrapper(Graph graph, GraphMapper mapper, List<TraversalStrategy<?>> strategies, Optional<Object> expandContext, int batchSize) {
        super(graph, mapper, strategies, expandContext);
        this.batchSize = batchSize;
        this.count = new AtomicLong();
        vertexFeatures = graph.features().vertex();
    }

    /**
     * Used to flush the current transaction.
     */
    public void flushTx() {
        // Reset the count so that direct invocations do not conflict with repeated calls to `batchFlushTx()`
        count.set(0);
    }

    /**
     * Used to initiate batch processing.
     */
    public void startBatchTx() {
        // By default, do nothing
    }

    /**
     * Used to perform batch flushing, each invocation increments the mutation count and {@link #flushTx()} is called
     * on batch boundaries.
     */
    public void batchFlushTx() {
        if (count.incrementAndGet() % batchSize == 0) {
            flushTx();
        }
    }

    /**
     * Accepts each partition key and write partition value.
     */
    public void forEachPartition(BiConsumer<? super String, ? super String> consumer) {
        strategies()
                .flatMap(ofType(PartitionStrategy.class))
                .filter(s -> s.getWritePartition() != null)
                .forEachOrdered(s -> consumer.accept(s.getPartitionKey(), s.getWritePartition()));
    }

    /**
     * If a metadata label is configured, store the supplied BDIO metadata on a vertex in the graph.
     */
    public void createMetadata(BdioMetadata metadata) {
        mapper().metadataLabel().ifPresent(metadataLabel -> {
            // Find or create the one vertex with the metadata label and attach the properties
            GraphTraversalSource g = traversal();
            Vertex vertex = g.V().hasLabel(metadataLabel).tryNext().orElseGet(() -> g.addV(metadataLabel).next());
            ElementHelper.attachProperties(vertex, getMetadataProperties(metadata));
        });
    }

    /**
     * Returns key/values pairs for the data properties of the graph metadata.
     */
    public Object[] getMetadataProperties(BdioMetadata metadata) {
        try {
            Map<String, Object> compactMetadata = compact(metadata, mapper().context(), bdioOptions().jsonLdOptions());
            mapper().metadataLabel().ifPresent(label -> compactMetadata.put(JsonLdConsts.TYPE, label));
            return getNodeProperties(compactMetadata, false);
        } catch (JsonLdError e) {
            // TODO We need to improve error handling for `createMetadata`, right now we just ignore everything
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns key/value pairs for the data properties of the specified BDIO node.
     */
    public Object[] getNodeProperties(Map<String, Object> node, boolean includeId) {
        List<Object> properties = new ArrayList<>(node.size());
        getNodeDataProperties(node, (k, v) -> {
            properties.add(k);
            properties.add(v);
        }, includeId);
        return properties.toArray();
    }

    /**
     * Extracts the data properties of the specified node.
     */
    public void getNodeDataProperties(Map<String, Object> node, BiConsumer<Object, Object> dataPropertyHandler, boolean includeId) {
        // Include the identifier if requested
        if (includeId) {
            Optional.ofNullable(node.get(JsonLdConsts.ID))
                    .map(this::generateId)
                    .ifPresent(id -> dataPropertyHandler.accept(T.id, id));
        }

        // Graph label
        Optional.ofNullable(node.get(JsonLdConsts.TYPE))
                // TODO Validate that the label is a String, we can't support multiple values!
                .ifPresent(label -> dataPropertyHandler.accept(T.label, label));

        // Write partitions (because we are not always performing modifications through a strategy)
        forEachPartition(dataPropertyHandler);

        // BDIO identifier preservation
        mapper().identifierKey().ifPresent(key -> {
            if (node instanceof BdioMetadata) {
                Optional.ofNullable(((BdioMetadata) node).id()).ifPresent(id -> dataPropertyHandler.accept(key, id));
            } else {
                Optional.ofNullable(node.get(JsonLdConsts.ID)).ifPresent(id -> dataPropertyHandler.accept(key, id));
            }
        });

        // Data properties
        Maps.transformEntries(node, mapper().valueObjectMapper()::fromFieldValue).entrySet().stream()
                .filter(e -> mapper().isDataPropertyKey(e.getKey()))
                .sorted(comparing(Map.Entry::getKey))
                .forEachOrdered(e -> dataPropertyHandler.accept(e.getKey(), e.getValue()));

        // Unknown properties
        mapper().preserveUnknownProperties(node, dataPropertyHandler);
    }

    /**
     * Extracts the object properties of the specified node.
     */
    public void getNodeObjectProperties(Map<String, Object> node, BiConsumer<String, Object> consumer) {
        for (Map.Entry<String, Object> property : node.entrySet()) {
            if (mapper().isObjectPropertyKey(property.getKey())) {
                mapper().valueObjectMapper().fromReferenceValueObject(property.getValue())
                        .map(this::generateId)
                        .forEach(id -> consumer.accept(property.getKey(), id));
            }
        }
    }

    public void mergeProperties(Vertex target, @Nullable Vertex source, Object... keyValues) {
        // TODO 'single' force overwrite, can we detect multiple (e.g. file fingerprints) from the mapper?
        if (source != null) {
            source.properties().forEachRemaining(vp -> target.property(single, vp.key(), vp.value()));
        }
        if (keyValues.length > 0) {
            ElementHelper.attachProperties(target, single, keyValues);
        }
    }

    private Object generateId(Object id) {
        // If user supplied identifiers are not supported this value will only be used by the star graph elements
        // (for example, this is the identifier used by star vertices prior to being attached, and is later used to look
        // up the persisted identifier of the star edge incoming vertex)
        if (vertexFeatures.supportsUserSuppliedIds() && strategies().anyMatch(s -> s instanceof PartitionStrategy)) {
            // The effective identifier must include the write partition value to avoid problems where the
            // same node imported into separate partitions gets recreated instead merged
            ImmutableMap.Builder<String, Object> mapIdBuilder = ImmutableMap.builder();
            mapIdBuilder.put(JsonLdConsts.ID, id);
            forEachPartition(mapIdBuilder::put);
            Map<String, Object> mapId = mapIdBuilder.build();
            if (vertexFeatures.willAllowId(mapId)) {
                return mapId;
            }

            String stringId = Joiner.on("\",\"").withKeyValueSeparator("\"=\"").appendTo(new StringBuilder().append("{\""), mapId).append("\"}").toString();
            if (vertexFeatures.willAllowId(stringId)) {
                return stringId;
            }

            // TODO For numeric IDs we could hash the string representation? Similar for UUID IDs?
        }
        return id;
    }

}
