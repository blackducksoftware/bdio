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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioOptions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * Core helper for encapsulating shared BDIO configuration.
 *
 * @author jgustie
 */
public final class BlackDuckIoCore {

    /**
     * @see BlackDuckIo#build()
     */
    public static BlackDuckIo.Builder bdio() {
        return BlackDuckIo.build();
    }

    private final Graph graph;

    private final BlackDuckIoVersion version;

    private final Optional<Object> expandContext;

    private final Optional<BlackDuckIoTokens> tokens;

    private final ImmutableList<TraversalStrategy<?>> strategies;

    public BlackDuckIoCore(Graph graph) {
        this(graph, BlackDuckIoVersion.defaultVersion());
    }

    public BlackDuckIoCore(Graph graph, BlackDuckIoVersion version) {
        this(graph, version, Optional.empty(), Optional.empty(), ImmutableList.of());
    }

    private BlackDuckIoCore(Graph graph, BlackDuckIoVersion version,
            Optional<Object> expandContext, Optional<BlackDuckIoTokens> tokens, ImmutableList<TraversalStrategy<?>> strategies) {
        this.graph = Objects.requireNonNull(graph);
        this.version = Objects.requireNonNull(version);
        this.tokens = Objects.requireNonNull(tokens);
        this.expandContext = Objects.requireNonNull(expandContext);
        this.strategies = Objects.requireNonNull(strategies);
    }

    // Configuration

    public BlackDuckIoCore withExpandContext(Object expandContext) {
        return new BlackDuckIoCore(graph, version, Optional.of(expandContext), tokens, strategies);
    }

    public BlackDuckIoCore withTokens(BlackDuckIoTokens tokens) {
        return new BlackDuckIoCore(graph, version, expandContext, Optional.of(tokens), strategies);
    }

    public BlackDuckIoCore withStrategies(@SuppressWarnings("rawtypes") TraversalStrategy... strategies) {
        return new BlackDuckIoCore(graph, version, expandContext, tokens, ImmutableList.copyOf(strategies));
    }

    public BlackDuckIoCore withConfiguration(Configuration config) {
        BlackDuckIoTokens tokens = DefaultBlackDuckIoTokens.create(config);
        ImmutableList.Builder<TraversalStrategy<?>> strategies = ImmutableList.builder();
        strategies.addAll(this.strategies);
        if (config.containsKey("partitionStrategy.partitionKey")) {
            strategies.add(PartitionStrategy.create(config.subset("partitionStrategy")));
        }
        return new BlackDuckIoCore(graph, version, expandContext, Optional.of(tokens), strategies.build());
    }

    public BlackDuckIoCore withGraphConfiguration() {
        return withConfiguration(graph.configuration().subset("bdio"));
    }

    // High level

    public void initializeSchema() {
        operations().initializeSchema(graph);
    }

    public void applySemanticRules() {
        operations().applySemanticRules(graph);
    }

    public void readGraph(InputStream inputStream) throws IOException {
        reader().readGraph(inputStream, graph);
    }

    public void writeGraph(OutputStream outputStream) throws IOException {
        writer().writeGraph(outputStream, graph);
    }

    // Middle level

    public BlackDuckIoMapper mapper() {
        BlackDuckIoMapper.Builder builder = io().mapper();
        tokens.ifPresent(builder::tokens);
        return builder.create();
    }

    public BlackDuckIoReader reader() {
        BlackDuckIoReader.Builder builder = io().reader();
        builder.mapper(mapper()).addStrategies(strategies).version(version);
        expandContext.ifPresent(builder::expandContext);
        return builder.create();
    }

    public BlackDuckIoWriter writer() {
        BlackDuckIoWriter.Builder builder = io().writer();
        builder.mapper(mapper()).addStrategies(strategies);
        return builder.create();
    }

    public BlackDuckIoOperations operations() {
        BlackDuckIoOperations.Builder builder = BlackDuckIoOperations.build();
        builder.mapper(mapper()).addStrategies(strategies);
        return builder.create();
    }

    // Low level

    public GraphTraversalSource traversal() {
        return readerWrapper().traversal();
    }

    public <B extends BdioDocument> B newDocument(Function<BdioOptions, B> documentFactory) {
        return documentFactory.apply(readerWrapper().bdioOptions());
    }

    @VisibleForTesting
    GraphReaderWrapper readerWrapper() {
        return graphWrapper().wrapReader(graph);
    }

    @VisibleForTesting
    GraphWriterWrapper writerWrapper() {
        return graphWrapper().wrapWriter(graph);
    }

    private BlackDuckIo io() {
        return graph.io(BlackDuckIo.build(version));
    }

    private GraphIoWrapperFactory graphWrapper() {
        return new GraphIoWrapperFactory().mapper(mapper()::createMapper).addStrategies(strategies);
    }

}
