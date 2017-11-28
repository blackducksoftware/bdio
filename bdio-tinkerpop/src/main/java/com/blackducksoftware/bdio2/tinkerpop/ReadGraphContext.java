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

import static java.util.Comparator.comparing;
import static org.apache.tinkerpop.gremlin.structure.Direction.OUT;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Graph.Features.VertexFeatures;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph.StarVertex;
import org.umlg.sqlg.structure.SqlgExceptions.InvalidIdException;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
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
class ReadGraphContext extends GraphContext {

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

    protected ReadGraphContext(Graph graph, GraphMapper mapper, int batchSize) {
        super(graph, mapper);
        this.batchSize = batchSize;
        this.count = new AtomicLong();
        vertexFeatures = graph.features().vertex();
    }

    /**
     * Used to initiate batch processing.
     */
    public void startBatchTx() {
        // By default, do nothing
    }

    /**
     * Used to perform batch commits, each invocation increments the mutation count and {@link #commitTx()} is called on
     * batch boundaries.
     */
    public void batchCommitTx() {
        if (count.incrementAndGet() % batchSize == 0) {
            commitTx();
            startBatchTx();
        }
    }

    /**
     * Test for checking if a BDIO identifier has been seen during this read.
     */
    protected boolean isIdentifierUnique(String identifier) {
        // Default is to return false suggesting that we might have seen the identifier before
        return false;
    }

    /**
     * Performs an "upsert" operation against the current state of this context.
     */
    public final Vertex upsert(Attachable<Vertex> attachableVertex) {
        Vertex baseVertex = attachableVertex.get();
        return Optional.ofNullable(baseVertex.id())
                // If this is a unique identifier, don't bother trying to look it up
                .filter(id -> !isIdentifierUnique(id.toString()))
                .flatMap(id -> {
                    try {
                        return Optional.ofNullable(Iterators.getNext(graph().vertices(id), null));
                    } catch (InvalidIdException e) {
                        return topology().identifierKey().flatMap(key -> traversal().V().has(key, id.toString()).tryNext());
                    }
                })

                // If we still have a vertex, update all of the properties
                .map(vertex -> {
                    baseVertex.properties().forEachRemaining(vp -> {
                        VertexProperty<?> vertexProperty = graph().features().vertex().properties().willAllowId(vp.id())
                                ? vertex.property(graph().features().vertex().getCardinality(vp.key()), vp.key(), vp.value(), T.id, vp.id())
                                : vertex.property(graph().features().vertex().getCardinality(vp.key()), vp.key(), vp.value());
                        vp.properties().forEachRemaining(p -> vertexProperty.property(p.key(), p.value()));
                    });
                    return vertex;
                })

                // If we do not have a vertex, create it
                .orElseGet(() -> {
                    boolean includeId = graph().features().vertex().willAllowId(baseVertex.id());
                    return graph().addVertex(ElementHelper.getProperties(baseVertex, includeId, true, Collections.emptySet()));
                });
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
                    topology().partitionStrategy().ifPresent(p -> {
                        edge.property(p.getPartitionKey(), p.getWritePartition());
                    });
                    return edge;
                });
    }

    /**
     * If a metadata label is configured, store the supplied BDIO metadata on a vertex in the graph.
     */
    public void createMetadata(BdioMetadata metadata) {
        topology().metadataLabel().ifPresent(metadataLabel -> {
            GraphTraversalSource g = traversal();

            // Find or create the one vertex with the metadata label
            Vertex vertex = g.V().hasLabel(metadataLabel).tryNext()
                    .orElseGet(() -> g.addV(metadataLabel).next());

            // Preserve the identifier (if present and configured)
            if (metadata.id() != null) {
                topology().identifierKey().ifPresent(key -> vertex.property(key, metadata.id()));
            }

            try {
                Map<String, Object> compactMetadata = mapper().compact(metadata);
                ElementHelper.attachProperties(vertex, getNodeProperties(compactMetadata, false));
            } catch (JsonLdError e) {
                // If we wrapped this and re-threw it, it would go back to the document's metadata single which is
                // subscribed to without an error handler, subsequently it would get wrapped in a
                // OnErrorNotImplementedException and passed to `RxJavaPlugins.onError`. So. Just call it directly.
                RxJavaPlugins.onError(e);
            }
        });
    }

    public long countVerticesByLabel(String label) {
        return traversal().V().hasLabel(label).count().next();
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
                    .map(label -> Maps.immutableEntry(T.label, label))
                    .ifPresent(properties);

            topology().partitionStrategy()
                    .map(s -> Maps.immutableEntry(s.getPartitionKey(), s.getWritePartition()))
                    .ifPresent(properties);

            topology().identifierKey().ifPresent(key -> {
                Optional.ofNullable(node.get(JsonLdConsts.ID))
                        .map(id -> Maps.immutableEntry(key, id))
                        .ifPresent(properties);
            });
        }

        // Data properties
        Maps.transformEntries(node, mapper().valueObjectMapper()::fromFieldValue).entrySet().stream()
                .filter(e -> topology().isDataPropertyKey(e.getKey()))
                .sorted(comparing(Map.Entry::getKey))
                .forEachOrdered(properties);

        // Unknown properties
        topology().unknownKey().ifPresent(key -> {
            mapper().preserveUnknownProperties(node)
                    .map(json -> Maps.immutableEntry(key, json))
                    .ifPresent(properties);
        });

        // Convert the whole thing into an array
        return properties.build()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                .toArray();
    }

    public Object generateId(Object id) {
        // If user supplied identifiers are not supported this value will only be used by the star graph elements
        // (for example, this is the identifier used by star vertices prior to being attached, and is later used to look
        // up the persisted identifier of the star edge incoming vertex)
        if (vertexFeatures.supportsUserSuppliedIds() && topology().partitionStrategy().isPresent()) {
            // The effective identifier must include the write partition value to avoid problems where the
            // same node imported into separate partitions gets recreated instead merged
            String partitionKey = topology().partitionStrategy().get().getPartitionKey();
            Object partitionValue = topology().partitionStrategy().get().getWritePartition();

            Map<String, Object> mapId = ImmutableMap.of(JsonLdConsts.ID, id, partitionKey, partitionValue);
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
