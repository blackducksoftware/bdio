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

import java.util.Objects;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;

/**
 * Base class for a context object encapsulating the configuration and graph currently being manipulated.
 *
 * @author jgustie
 */
abstract class GraphContext {

    private final Graph graph;

    private final GraphMapper mapper;

    private final GraphTopology topology;

    private final boolean supportsTransactions;

    protected GraphContext(Graph graph, GraphMapper mapper) {
        this.graph = Objects.requireNonNull(graph);
        this.mapper = Objects.requireNonNull(mapper);

        // Store extra references that will be needed frequently
        this.topology = mapper.topology();
        this.supportsTransactions = graph.features().graph().supportsTransactions();
    }

    /**
     * Returns the graph for this context.
     */
    public final Graph graph() {
        return graph;
    }

    /**
     * Returns the topology used in this context.
     */
    public final GraphTopology topology() {
        return topology;
    }

    /**
     * Returns the mapper used in this context.
     */
    public final GraphMapper mapper() {
        return mapper;
    }

    /**
     * Returns a traversal source for the graph.
     */
    public GraphTraversalSource traversal() {
        GraphTraversalSource traversal = graph().traversal();
        return topology().partitionStrategy().map(traversal::withStrategies).orElse(traversal);
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
