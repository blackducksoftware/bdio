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

    private final BlackDuckIoConfig config;

    private final Graph graph;

    private final boolean supportsTransactions;

    protected GraphContext(BlackDuckIoConfig config, Graph graph) {
        this.config = Objects.requireNonNull(config);
        this.graph = Objects.requireNonNull(graph);
        this.supportsTransactions = graph.features().graph().supportsTransactions();
    }

    /**
     * Initialize this context.
     */
    public void initialize(BdioFrame frame) {
        // Default is a noop
    }

    /**
     * Returns the configuration for this context.
     */
    protected final BlackDuckIoConfig config() {
        return config;
    }

    /**
     * Returns the graph for this context.
     */
    protected final Graph graph() {
        return graph;
    }

    /**
     * Returns a traversal source for the graph.
     */
    public GraphTraversalSource traversal() {
        GraphTraversalSource traversal = graph().traversal();
        return config.partitionStrategy().map(traversal::withStrategies).orElse(traversal);
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
