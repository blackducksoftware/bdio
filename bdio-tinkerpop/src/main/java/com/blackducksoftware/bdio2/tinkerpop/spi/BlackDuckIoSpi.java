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
package com.blackducksoftware.bdio2.tinkerpop.spi;

import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.SqlgBlackDuckIo;
import com.blackducksoftware.bdio2.tinkerpop.tinkergraph.TinkerGraphBlackDuckIo;

public abstract class BlackDuckIoSpi {

    public static BlackDuckIoSpi getForGraph(Graph graph) {
        if (graph.getClass().getName().equals("org.umlg.sqlg.structure.SqlgGraph")) {
            return SqlgBlackDuckIo.getInstance();
        } else if (graph.getClass().getName().equals("org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph")) {
            return TinkerGraphBlackDuckIo.getInstance();
        } else {
            return DefaultBlackDuckIo.getInstance();
        }
    }

    @Nullable
    public Consumer<Mapper.Builder<?>> onMapper(@SuppressWarnings("rawtypes") @Nullable Consumer<Mapper.Builder> onMapper) {
        return onMapper != null ? onMapper::accept : null;
    }

    public final BlackDuckIoReaderSpi reader(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame, int batchSize) {
        Optional<BlackDuckIoReaderSpi> reader = Optional.empty();
        if (allowProviderImplementation(traversal.getGraph())) {
            reader = providerReader(traversal, options, frame, batchSize);
        }
        return reader.orElseGet(() -> new DefaultBlackDuckIoReader(traversal, options, frame));
    }

    public final BlackDuckIoWriterSpi writer(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        Optional<BlackDuckIoWriterSpi> writer = Optional.empty();
        if (allowProviderImplementation(traversal.getGraph())) {
            writer = providerWriter(traversal, options, frame);
        }
        return writer.orElseGet(() -> new DefaultBlackDuckIoWriter(traversal, options, frame));
    }

    public final BlackDuckIoNormalizationSpi normalization(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        Optional<BlackDuckIoNormalizationSpi> normalization = Optional.empty();
        if (allowProviderImplementation(traversal.getGraph())) {
            normalization = providerNormalization(traversal, options, frame);
        }
        return normalization.orElseGet(() -> new DefaultBlackDuckIoNormalization(traversal, options, frame));
    }

    protected Optional<BlackDuckIoReaderSpi> providerReader(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame, int batchSize) {
        return Optional.empty();
    }

    protected Optional<BlackDuckIoWriterSpi> providerWriter(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        return Optional.empty();
    }

    protected Optional<BlackDuckIoNormalizationSpi> providerNormalization(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        return Optional.empty();
    }

    /**
     * Check to allow if provider specific implementations should be allowed. Generally the only time this is not true
     * is while we are running tests.
     */
    private boolean allowProviderImplementation(Graph graph) {
        return graph.configuration().subset("bdio").getBoolean("allowProviderImplementation", true);
    }

}
