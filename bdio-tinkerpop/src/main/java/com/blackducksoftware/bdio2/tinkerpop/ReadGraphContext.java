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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.umlg.sqlg.structure.SqlgExceptions.InvalidIdException;

import com.google.common.collect.Iterators;

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
     * The optional partitioning strategy.
     */
    @Nullable
    private final PartitionStrategy partitionStrategy;

    protected ReadGraphContext(Graph graph, int batchSize, @Nullable PartitionStrategy partitionStrategy) {
        this.graph = Objects.requireNonNull(graph);
        this.supportsTransactions = graph.features().graph().supportsTransactions();
        this.batchSize = batchSize;
        this.count = new AtomicLong();
        this.partitionStrategy = partitionStrategy;
    }

    /**
     * Returns a traversal source for the graph.
     */
    public GraphTraversalSource traversal() {
        if (partitionStrategy != null) {
            return graph.traversal().withStrategies(partitionStrategy);
        } else {
            return graph.traversal();
        }
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
                        return traversal().V().has(Tokens.id, id.toString()).tryNext();
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
     * Test for checking if a BDIO identifier has been seen during this read.
     */
    protected boolean isIdentifierUnique(String identifier) {
        // Default is to return false suggesting that we might have seen the identifier before
        return false;
    }
}
