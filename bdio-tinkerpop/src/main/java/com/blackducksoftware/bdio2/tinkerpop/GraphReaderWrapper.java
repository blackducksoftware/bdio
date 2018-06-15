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
import static org.apache.tinkerpop.gremlin.structure.Direction.OUT;
import static org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality.single;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Graph.Features.VertexFeatures;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph.StarVertex;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import io.reactivex.Observable;
import io.reactivex.plugins.RxJavaPlugins;

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
     * Used to flush the current transaction. The default implementation commits the transaction.
     */
    public void flushTx() {
        commitTx();
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
            startBatchTx();
        }
    }

    /**
     * Accepts each partition key and write partition value.
     */
    public void forEachPartition(BiConsumer<String, String> consumer) {
        strategies()
                .flatMap(ofType(PartitionStrategy.class))
                .filter(s -> s.getWritePartition() != null)
                .forEachOrdered(s -> consumer.accept(s.getPartitionKey(), s.getWritePartition()));
    }

    /**
     * Performs the reduction to a map.
     */
    public final Map<StarVertex, Vertex> toMap(Map<StarVertex, Vertex> map, StarVertex baseVertex) {
        Vertex persisted = map.get(baseVertex);
        if (persisted != null) {
            // Update properties
            baseVertex.properties().forEachRemaining(vp -> persisted.property(single, vp.key(), vp.value()));

            // Update edges
            Iterator<Edge> edges = baseVertex.edges(Direction.OUT);
            if (edges.hasNext()) {
                // Worst case scenario. We need to get the old key, which means a linear search...
                // The problem is that `baseVertex` can be used for a lookup, but it's not the actual key
                for (StarVertex existingUnpersistedVertex : map.keySet()) {
                    if (existingUnpersistedVertex.equals(baseVertex)) {
                        edges.forEachRemaining(e -> existingUnpersistedVertex.addEdge(e.label(), e.inVertex()));
                        break;
                    }
                }
            }
        } else {
            // Create the new vertex
            boolean includeId = vertexFeatures.willAllowId(baseVertex.id());
            map.put(baseVertex, graph().addVertex(ElementHelper.getProperties(baseVertex, includeId, true, Collections.emptySet())));
        }

        // Batch commit update or insertion
        batchFlushTx();
        return map;
    }

    /**
     * Given a map of in-memory vertices to their persisted counterparts, create the edges from the in-memory vertices
     * using the persisted identifiers.
     */
    public final Observable<Edge> createEdges(Map<StarVertex, Vertex> persistedVertices) {
        return Observable.fromIterable(persistedVertices.keySet())

                // Gets all the outbound edges from in-memory (StarVertex) vertices
                .flatMapIterable(kv -> (Iterable<Edge>) (() -> kv.edges(Direction.OUT)))

                // Connect the edges and store their properties
                .map(e -> {
                    // Look up the vertices
                    Vertex cachedOutV = persistedVertices.get(e.outVertex());
                    Vertex cachedInV = persistedVertices.get(e.inVertex());
                    if (cachedInV == null) {
                        throw new NodeDoesNotExistException(e.outVertex().id(), e.label(), e.inVertex().id());
                    }

                    // First try to find an existing edge
                    Iterator<Edge> edges = cachedOutV.edges(OUT, e.label());
                    while (edges.hasNext()) {
                        Edge edge = edges.next();
                        if (edge.inVertex().equals(cachedInV)) {
                            return edge;
                        }
                    }

                    // Create a new edge
                    Edge edge = cachedOutV.addEdge(e.label(), cachedInV);
                    forEachPartition(edge::property);
                    return edge;
                });
    }

    /**
     * If a metadata label is configured, store the supplied BDIO metadata on a vertex in the graph.
     */
    public void createMetadata(BdioMetadata metadata) {
        mapper().metadataLabel().ifPresent(metadataLabel -> {
            GraphTraversalSource g = traversal();

            // Find or create the one vertex with the metadata label
            Vertex vertex = g.V().hasLabel(metadataLabel).tryNext()
                    .orElseGet(() -> g.addV(metadataLabel).next());

            // Preserve the identifier (if present and configured)
            if (metadata.id() != null) {
                mapper().identifierKey().ifPresent(key -> vertex.property(key, metadata.id()));
            }

            try {
                Map<String, Object> compactMetadata = compact(metadata, mapper().context(), bdioOptions().jsonLdOptions());
                ElementHelper.attachProperties(vertex, getNodeProperties(compactMetadata, false));
            } catch (JsonLdError e) {
                // If we wrapped this and re-threw it, it would go back to the document's metadata single which is
                // subscribed to without an error handler, subsequently it would get wrapped in a
                // OnErrorNotImplementedException and passed to `RxJavaPlugins.onError`. So. Just call it directly.
                RxJavaPlugins.onError(e);
            }
        });
    }

    /**
     * Returns key/value pairs for the data properties of the specified BDIO node.
     */
    public Object[] getNodeProperties(Map<String, Object> node, boolean includeSpecial) {
        Stream.Builder<Map.Entry<?, ?>> properties = Stream.builder();

        // Special properties that can be optionally included
        if (includeSpecial) {
            Optional.ofNullable(node.get(JsonLdConsts.ID))
                    .map(this::generateId)
                    .map(id -> Maps.immutableEntry(T.id, id))
                    .ifPresent(properties);

            Optional.ofNullable(node.get(JsonLdConsts.TYPE))
                    // TODO Validate that the label is a String, we can't support multiple values!
                    .map(label -> Maps.immutableEntry(T.label, label))
                    .ifPresent(properties);

            forEachPartition((k, v) -> properties.add(Maps.immutableEntry(k, v)));

            mapper().identifierKey().ifPresent(key -> {
                Optional.ofNullable(node.get(JsonLdConsts.ID))
                        .map(id -> Maps.immutableEntry(key, id))
                        .ifPresent(properties);
            });
        }

        // Data properties
        Maps.transformEntries(node, mapper().valueObjectMapper()::fromFieldValue).entrySet().stream()
                .filter(e -> mapper().isDataPropertyKey(e.getKey()))
                .sorted(comparing(Map.Entry::getKey))
                .forEachOrdered(properties);

        // Unknown properties
        mapper().preserveUnknownProperties(node, (k, v) -> properties.add(Maps.immutableEntry(k, v)));

        // Convert the whole thing into an array
        return properties.build()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                .toArray();
    }

    public Object generateId(Object id) {
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
