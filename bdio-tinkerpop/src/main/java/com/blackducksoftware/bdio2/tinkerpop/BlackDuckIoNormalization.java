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

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Objects;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoNormalizationSpi;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoSpi;

public class BlackDuckIoNormalization {

    private final BlackDuckIoOptions options;

    private final BdioFrame frame;

    private BlackDuckIoNormalization(Builder builder) {
        options = Objects.requireNonNull(builder.options);
        frame = builder.mapper.createMapper();
    }

    public void normalize(List<TraversalStrategy<?>> strategies, Graph graph) {
        GraphTraversalSource g = graph.traversal().withStrategies(strategies.toArray(new TraversalStrategy<?>[strategies.size()]));
        BlackDuckIoNormalizationSpi spi = BlackDuckIoSpi.getForGraph(graph).normalization(g, options, frame);

        spi.identifyRoot();
        spi.addMissingFileParents();
        spi.addMissingProjectDependencies();
        spi.implyFileSystemTypes();
    }

    public void normalize(Graph graph) {
        normalize(emptyList(), graph);
    }

    public static Builder build() {
        return new Builder();
    }

    public static class Builder {

        private Mapper<BdioFrame> mapper;

        private BlackDuckIoOptions options;

        private Builder() {
            mapper = BlackDuckIoMapper.build().create();
            options = BlackDuckIoOptions.build().create();
        }

        public Builder mapper(Mapper<BdioFrame> mapper) {
            this.mapper = Objects.requireNonNull(mapper);
            return this;
        }

        public Builder options(BlackDuckIoOptions options) {
            this.options = Objects.requireNonNull(options);
            return this;
        }

        public BlackDuckIoNormalization create() {
            return new BlackDuckIoNormalization(this);
        }
    }

}
