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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategies;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;

import com.blackducksoftware.bdio2.BdioOptions;

/**
 * Base class for a context object encapsulating the configuration and graph currently being manipulated.
 *
 * @author jgustie
 */
abstract class GraphIoWrapper {

    /**
     * The graph in context.
     */
    private final Graph graph;

    /**
     * The mapper used in this context to map between graph elements and BDIO.
     */
    private final GraphMapper mapper;

    /**
     * The traversal strategies to use.
     */
    private final List<TraversalStrategy<?>> strategies;

    /**
     * The BDIO document options to use.
     */
    private final BdioOptions bdioOptions;

    /**
     * A reference to the transaction support flag of the {@link #graph}.
     */
    private final boolean supportsTransactions;

    protected GraphIoWrapper(Graph graph, GraphMapper mapper, List<TraversalStrategy<?>> strategies, Optional<String> base, Optional<Object> expandContext) {
        this.graph = Objects.requireNonNull(graph);
        this.mapper = Objects.requireNonNull(mapper);
        this.strategies = Collections.unmodifiableList(TraversalStrategies.sortStrategies(strategies));

        BdioOptions.Builder optionsBuilder = new BdioOptions.Builder()
                .base(base.orElse(null))
                .expandContext(expandContext.orElseGet(mapper::context));
        mapper.injectedDocuments(optionsBuilder::injectDocument);
        bdioOptions = optionsBuilder.build();

        // Store extra references that will be needed frequently
        this.supportsTransactions = graph.features().graph().supportsTransactions();

        // TODO Perform other feature validation here, e.g. user identifier support and context.identifierKey...
    }

    /**
     * Returns the graph for this context.
     */
    public Graph graph() {
        return graph;
    }

    /**
     * Returns the mapper used in this context.
     */
    public GraphMapper mapper() {
        return mapper;
    }

    /**
     * Returns a stream of the configured traversal strategies.
     */
    protected Stream<TraversalStrategy<?>> strategies() {
        return strategies.stream();
    }

    /**
     * Returns the BDIO document options.
     */
    protected BdioOptions bdioOptions() {
        return bdioOptions;
    }

    /**
     * Returns a traversal source for the graph.
     */
    public GraphTraversalSource traversal() {
        if (strategies.isEmpty()) {
            return graph().traversal();
        } else {
            // Generally not good form to serialize a list for varargs invocation, but we have little choice here
            return graph().traversal().withStrategies(strategies.toArray(new TraversalStrategy<?>[strategies.size()]));
        }
    }

    /**
     * Commits the current transaction if the underlying graph supports it.
     */
    public void commitTx() {
        if (supportsTransactions) {
            graph().tx().commit();
        }
    }

    /**
     * Rolls back the current transaction if the underlying graph supports it.
     */
    public void rollbackTx() {
        if (supportsTransactions) {
            graph.tx().rollback();
        }
    }

}
