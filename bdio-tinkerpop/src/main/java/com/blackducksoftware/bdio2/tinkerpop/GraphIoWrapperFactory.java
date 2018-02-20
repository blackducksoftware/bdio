/*
 * Copyright 2018 Synopsys, Inc.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.umlg.sqlg.structure.SqlgGraph;

/**
 * Factory class for constructing new graph wrappers.
 *
 * @author jgustie
 */
class GraphIoWrapperFactory {

    private Supplier<GraphMapper> mapper;

    private int batchSize;

    private List<TraversalStrategy<?>> strategies = new ArrayList<>();

    public GraphIoWrapperFactory() {
        mapper = () -> GraphMapper.build().create();
        batchSize = 10_000;
    }

    public GraphIoWrapperFactory mapper(Supplier<GraphMapper> mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        return this;
    }

    public GraphIoWrapperFactory batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public GraphIoWrapperFactory addStrategies(Collection<TraversalStrategy<?>> strategies) {
        this.strategies.addAll(strategies);
        return this;
    }

    public GraphReaderWrapper wrapReader(Graph graph) {
        if (graph instanceof SqlgGraph) {
            return new SqlgGraphReaderWrapper((SqlgGraph) graph, mapper.get(), strategies, batchSize);
        } else {
            return new GraphReaderWrapper(graph, mapper.get(), strategies, batchSize);
        }
    }

    public GraphWriterWrapper wrapWriter(Graph graph) {
        return new GraphWriterWrapper(graph, mapper.get(), strategies);
    }

}
