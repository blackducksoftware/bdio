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
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

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

import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;

import io.reactivex.Observable;

/**
 * Context used when performing a
 * {@link org.apache.tinkerpop.gremlin.structure.io.GraphReader#readGraph(java.io.InputStream, Graph)
 * GraphReader.readGraph} operation on BDIO data.
 *
 * @author jgustie
 */
class ReadGraphContext extends GraphContext {

    // TODO Do we need a sort order that is stable across BDIO versions?
    // TODO Do we need to promote the File's HID column?
    private static Comparator<Map.Entry<String, Object>> DATA_PROPERTY_ORDER = Comparator.<Map.Entry<String, Object>, String> comparing(Map.Entry::getKey)
            .reversed();

    /**
     * The number of mutations between commits.
     */
    private final int batchSize;

    /**
     * The number of observed mutations.
     */
    private final AtomicLong count;

    protected ReadGraphContext(Graph graph, GraphMapper mapper, int batchSize) {
        super(graph, mapper);
        this.batchSize = batchSize;
        this.count = new AtomicLong();
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
                // If this a unique identifier, don't bother trying to look it up
                .filter(id -> !isIdentifierUnique(id.toString()))
                .flatMap(id -> {
                    try {
                        return Optional.ofNullable(Iterators.getNext(graph().vertices(id), null));
                    } catch (InvalidIdException e) {
                        return mapper().identifierKey().flatMap(key -> traversal().V().has(key, id.toString()).tryNext());
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
                    Vertex cachedOutV = persistedVertices.get(e.outVertex());
                    Vertex cachedInV = persistedVertices.get(e.inVertex());
                    Edge newEdge = cachedOutV.addEdge(e.label(), cachedInV);

                    e.properties().forEachRemaining(p -> newEdge.property(p.key(), p.value()));

                    mapper().partitionStrategy().ifPresent(p -> {
                        newEdge.property(p.getPartitionKey(), p.getWritePartition());
                    });

                    return newEdge;
                });
    }

    public long countVerticesByLabel(String label) {
        return traversal().V().hasLabel(label).count().next();
    }

    /**
     * Returns key/value pairs for the data properties of the specified BDIO node.
     */
    public Object[] getNodeProperties(Map<String, Object> node, boolean includeSpecial) {
        // IMPORTANT: Add elements in reverse order of importance (e.g. T.id should be last!)
        // TODO This could be a restriction from an old version of Sqlg
        // TODO Or does it matter because Sqlg pushes them through a ConcurrentHashMap?
        // TODO Should this just use a LinkedHashMap?
        Stream.Builder<Map.Entry<?, ?>> properties = Stream.builder();

        // Unknown properties
        mapper().unknownKey().ifPresent(key -> {
            mapper().preserveUnknownProperties(node)
                    .map(json -> Maps.immutableEntry(key, json))
                    .ifPresent(properties);
        });

        // Sorted data properties
        Maps.transformValues(node, mapper().valueObjectMapper()::fromFieldValue).entrySet().stream()
                .filter(e -> mapper().isDataPropertyKey(e.getKey()))
                .sorted(DATA_PROPERTY_ORDER)
                .forEachOrdered(properties);

        // Special properties that can be optionally included
        if (includeSpecial) {
            // TODO Can we use ElementIdStrategy instead?
            mapper().identifierKey().ifPresent(key -> {
                Optional.ofNullable(node.get(JsonLdConsts.ID))
                        .map(id -> Maps.immutableEntry(key, id))
                        .ifPresent(properties);
            });

            mapper().partitionStrategy()
                    .map(s -> Maps.immutableEntry(s.getPartitionKey(), s.getWritePartition()))
                    .ifPresent(properties);

            Optional.ofNullable(node.get(JsonLdConsts.TYPE))
                    .map(label -> Maps.immutableEntry(T.label, label))
                    .ifPresent(properties);

            // NOTE: If the graph does not support user identifiers, this value gets ignored
            // TODO If user identifiers aren't support, skip the computation...
            // NOTE: If the graph supports user identifiers, we need both the JSON-LD identifier
            // and the write partition (since the same identifier can exist in multiple partitions)

            Optional.ofNullable(node.get(JsonLdConsts.ID))
                    .map(id -> generateId(id))
                    .map(id -> Maps.immutableEntry(T.id, id))
                    .ifPresent(properties);
        }

        // Convert the whole thing into an array
        return properties.build()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                .toArray();
    }

    public Object generateId(Object id) {
        // TODO Can we use a list here instead of strings?
        return mapper().partitionStrategy()
                .map(PartitionStrategy::getWritePartition)
                // TODO Use a query parameter instead of the fragment
                .map(writePartition -> (Object) (id + "#" + writePartition))
                .orElse(id);
    }

}
