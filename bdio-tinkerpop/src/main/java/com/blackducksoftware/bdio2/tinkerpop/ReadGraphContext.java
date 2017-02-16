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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph.StarVertex;
import org.umlg.sqlg.structure.SqlgExceptions.InvalidIdException;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.tinkerpop.BdioGraph.B;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

import io.reactivex.Observable;

/**
 * Context used when performing a
 * {@link org.apache.tinkerpop.gremlin.structure.io.GraphReader#readGraph(java.io.InputStream, Graph)
 * GraphReader.readGraph} operation on BDIO data.
 *
 * @author jgustie
 */
class ReadGraphContext {

    /**
     * The graph being imported to.
     */
    private final Graph graph;

    /**
     * Flag indicating if the graph supports transactions or not.
     */
    private final boolean supportsTransactions;

    /**
     * The number of mutations between commits.
     */
    private final int batchSize;

    /**
     * The number of observed mutations.
     */
    private final AtomicLong count;

    /**
     * The optional metadata label;
     */
    private final Optional<String> metadataLabel;

    /**
     * The optional partitioning strategy.
     */
    private final Optional<PartitionStrategy> partitionStrategy;

    protected ReadGraphContext(Graph graph, int batchSize, @Nullable String metadataLabel, @Nullable PartitionStrategy partitionStrategy) {
        this.graph = Objects.requireNonNull(graph);
        this.supportsTransactions = graph.features().graph().supportsTransactions();
        this.batchSize = batchSize;
        this.count = new AtomicLong();
        this.metadataLabel = Optional.ofNullable(metadataLabel);
        this.partitionStrategy = Optional.ofNullable(partitionStrategy);
    }

    /**
     * Returns the current metadata label, if any.
     */
    protected final Optional<String> metadataLabel() {
        return metadataLabel;
    }

    /**
     * Returns the current partitioning strategy, if any.
     */
    protected final Optional<PartitionStrategy> partitionStrategy() {
        return partitionStrategy;
    }

    /**
     * Returns a traversal source for the graph.
     */
    public GraphTraversalSource traversal() {
        GraphTraversalSource traversal = graph.traversal();
        return partitionStrategy().map(traversal::withStrategies).orElse(traversal);
    }

    /**
     * Initialize this context.
     */
    public void initialize(BdioFrame frame) {
        // Default is a noop
    }

    /**
     * Commits the current transaction if the underlying graph supports it.
     */
    public void commitTx() {
        if (supportsTransactions) {
            graph.tx().commit();
        }
    }

    /**
     * Used to perform batch commits, each invocation increments the mutation count and {@link #commitTx()} is called on
     * batch boundaries.
     */
    public void batchCommitTx(Object obj) {
        if (supportsTransactions && count.incrementAndGet() % batchSize == 0) {
            commitTx();
        }
    }

    /**
     * Performs an "upsert" operation against the current state of this context.
     */
    public final Vertex upsert(Attachable<Vertex> attachableVertex) {
        Vertex baseVertex = attachableVertex.get();
        return Optional.ofNullable(baseVertex.id())
                // If this a unique identifier, don't bother trying to look it up
                .filter(id -> !isIdentifierUnique(id.toString()))
                .flatMap(id -> {
                    try {
                        return Optional.ofNullable(Iterators.getNext(graph.vertices(id), null));
                    } catch (InvalidIdException e) {
                        return traversal().V().has(B.id, id.toString()).tryNext();
                    }
                })

                // If we still have a vertex, update all of the properties
                .map(vertex -> {
                    baseVertex.properties().forEachRemaining(vp -> {
                        VertexProperty<?> vertexProperty = graph.features().vertex().properties().willAllowId(vp.id())
                                ? vertex.property(graph.features().vertex().getCardinality(vp.key()), vp.key(), vp.value(), T.id, vp.id())
                                : vertex.property(graph.features().vertex().getCardinality(vp.key()), vp.key(), vp.value());
                        vp.properties().forEachRemaining(p -> vertexProperty.property(p.key(), p.value()));
                    });
                    return vertex;
                })

                // If we do not have a vertex, create it
                .orElseGet(() -> {
                    boolean includeId = graph.features().vertex().willAllowId(baseVertex.id());
                    return graph.addVertex(ElementHelper.getProperties(baseVertex, includeId, true, Collections.emptySet()));
                });
    }

    /**
     * If a metadata label is configured, store the supplied BDIO metadata on a vertex in the graph.
     */
    public final void createMetadata(BdioMetadata metadata, BdioFrame frame, JsonLdOptions options, ValueObjectMapper valueObjectMapper) {
        if (metadataLabel.isPresent()) {
            GraphTraversalSource g = traversal();
            Vertex metadataVertex = g.V().hasLabel(metadataLabel.get()).tryNext().orElseGet(() -> g.addV(metadataLabel.get()).next());

            // TODO Don't use B.id here because this metadata is exposed
            metadataVertex.property(B.id, metadata.id());
            try {
                // Compact the metadata using the context extracted from frame
                Map<String, Object> compactMetadata = JsonLdProcessor.compact(metadata, frame, options);
                ElementHelper.attachProperties(metadataVertex, BdioHelper.getNodeProperties(compactMetadata, false, frame, valueObjectMapper, null));
            } catch (JsonLdError e) {
                // TODO What can we do about this?
                e.printStackTrace();
            }
        }
    }

    /**
     * If a metadata label is configured, read the vertex from the graph into a new BDIO metadata instance.
     */
    public final BdioMetadata readMetadata(JsonLdOptions options) {
        return metadataLabel
                .flatMap(label -> traversal().V().hasLabel(label).tryNext())
                .map(vertex -> {
                    BdioMetadata metadata = new BdioMetadata();
                    metadata.id(vertex.value(B.id));
                    try {
                        Object expandedMetadata = Iterables.getOnlyElement(JsonLdProcessor.expand(ElementHelper.propertyValueMap(vertex), options));
                        if (expandedMetadata instanceof Map<?, ?>) {
                            ((Map<?, ?>) expandedMetadata).forEach((key, value) -> {
                                if (key instanceof String) {
                                    metadata.put((String) key, value);
                                }
                            });
                        }
                    } catch (JsonLdError e) {

                    }
                    return metadata;
                })
                .orElse(BdioMetadata.createRandomUUID());
    }

    /**
     * Given a map of in-memory vertices to their persisted counterparts, create the edges from the in-memory vertices
     * using the persisted identifiers.
     */
    public final Observable<Edge> createEdges(Map<StarVertex, Vertex> persistedVertices) {
        Graph.Features.EdgeFeatures edgeFeatures = graph.features().edge();
        return Observable.fromIterable(persistedVertices.keySet())

                // Gets all the outbound edges from in-memory (StarVertex) vertices
                .flatMapIterable(kv -> (Iterable<Edge>) (() -> kv.edges(Direction.OUT)))

                // Connect the edges and store their properties
                .map(e -> {
                    final Vertex cachedOutV = persistedVertices.get(e.outVertex());
                    final Vertex cachedInV = persistedVertices.get(e.inVertex());
                    final Edge newEdge = edgeFeatures.willAllowId(e.id())
                            ? cachedOutV.addEdge(e.label(), cachedInV, T.id, e.id())
                            : cachedOutV.addEdge(e.label(), cachedInV);

                    e.properties().forEachRemaining(p -> newEdge.property(p.key(), p.value()));

                    return newEdge;
                });
    }

    /**
     * Test for checking if a BDIO identifier has been seen during this read.
     */
    protected boolean isIdentifierUnique(String identifier) {
        // Default is to return false suggesting that we might have seen the identifier before
        return false;
    }
}
