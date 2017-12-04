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
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.umlg.sqlg.structure.SqlgGraph;

/**
 * Factory for context instances. This is basically the builder for {@code GraphContext} instances.
 *
 * @author jgustie
 */
class GraphContextFactory {

    /**
     * The number of mutations that can occur before we commit a transaction.
     */
    private static final int DEFAULT_BATCH_SIZE = 10_000;

    private final GraphMapper mapper;

    private final int batchSize;

    private GraphContextFactory(AbstractContextBuilder<?, ?> builder) {
        mapper = builder.mapper.get();
        batchSize = builder.batchSize;
    }

    /**
     * Returns a new context for reading BDIO into a graph.
     */
    public ReadGraphContext forBdioReadingInto(Graph graph) {
        // Initialize the topology
        mapper.topology().initialize();

        // Subclasses for specific graph implementations provide optimization
        if (graph instanceof SqlgGraph) {
            return new SqlgReadGraphContext((SqlgGraph) graph, mapper, batchSize);
        } else {
            return new ReadGraphContext(graph, mapper, batchSize);
        }
    }

    /**
     * Returns a new context for writing BDIO out from a graph.
     */
    public WriteGraphContext forBdioWritingFrom(Graph graph) {
        return new WriteGraphContext(graph, mapper);
    }

    /**
     * Common base class for configuring a context factory.
     */
    public static abstract class AbstractContextBuilder<T, Builder extends AbstractContextBuilder<T, Builder>> {

        private final Function<Builder, T> factory;

        private Supplier<GraphMapper> mapper;

        private int batchSize;

        protected AbstractContextBuilder(Function<Builder, T> factory) {
            this.factory = Objects.requireNonNull(factory);
            mapper = () -> GraphMapper.build().create();
            batchSize = DEFAULT_BATCH_SIZE;
        }

        @SuppressWarnings("unchecked")
        private Builder self() {
            return (Builder) this;
        }

        public Builder mapperFactory(Supplier<GraphMapper> mapper) {
            this.mapper = Objects.requireNonNull(mapper);
            return self();
        }

        /**
         * Helper to generate the graph mapper using the TinkerPop API.
         */
        public Builder mapper(Mapper<GraphMapper> mapper) {
            return mapperFactory(mapper::createMapper);
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return self();
        }

        public final T create() {
            return factory.apply(self());
        }

        /**
         * Used by subclasses to produce a context factory.
         */
        // We could have used a BiFunction<Builder, ContextFactory, T> for the factory method instead...
        protected final GraphContextFactory contextFactory() {
            return new GraphContextFactory(this);
        }
    }

}
