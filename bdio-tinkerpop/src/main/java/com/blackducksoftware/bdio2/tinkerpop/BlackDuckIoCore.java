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
import java.util.function.Consumer;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.structure.Graph;

import com.blackducksoftware.bdio2.Bdio;
import com.google.common.annotations.VisibleForTesting;

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

    private Consumer<BlackDuckIoMapper.Builder> onMapper;

    private Consumer<BlackDuckIoReader.Builder> onReader;

    private Consumer<BlackDuckIoWriter.Builder> onWriter;

    private Consumer<BlackDuckIoOperations.Builder> onOperations;

    private Consumer<GraphIoWrapperFactory> onWrapper;

    private Optional<String> writePartition = Optional.empty();

    public BlackDuckIoCore(Graph graph) {
        this(graph, BlackDuckIoVersion.defaultVersion());
    }

    public BlackDuckIoCore(Graph graph, BlackDuckIoVersion version) {
        this.graph = Objects.requireNonNull(graph);
        this.version = Objects.requireNonNull(version);
        onMapper = b -> {};
        onReader = b -> {
            b.mapper(mapper());
            writePartition.ifPresent(b::partition);
        };
        onWriter = b -> b.mapper(mapper());
        onOperations = b -> {
            b.mapper(mapper());
            writePartition.ifPresent(b::partition);
        };
        onWrapper = f -> {
            f.mapper(mapper()::createMapper);
            writePartition.ifPresent(f::writePartition);
        };
    }

    // Configuration

    public BlackDuckIoCore withConfiguration(Configuration config) {
        onMapper = onMapper.andThen(b -> b.tokens(BlackDuckIoContext.create(config)));
        writePartition = Optional.ofNullable(config.getString("bdio.partitionStrategy.writePartition", null));
        return this;
    }

    public BlackDuckIoCore withGraphConfiguration() {
        return withConfiguration(graph.configuration().subset("bdio"));
    }

    public BlackDuckIoCore withTokens(BlackDuckIoTokens tokens) {
        onMapper = onMapper.andThen(b -> b.tokens(tokens));
        return this;
    }

    public BlackDuckIoCore withContentType(Bdio.ContentType contentType, Object expandContext) {
        onMapper = onMapper.andThen(b -> b.contentType(contentType, expandContext));
        return this;
    }

    public BlackDuckIoCore withWritePartition(String writePartition) {
        this.writePartition = Optional.of(writePartition);
        return this;
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
        onMapper.accept(builder);
        return builder.create();
    }

    public BlackDuckIoReader reader() {
        BlackDuckIoReader.Builder builder = io().reader();
        onReader.accept(builder);
        return builder.create();
    }

    public BlackDuckIoWriter writer() {
        BlackDuckIoWriter.Builder builder = io().writer();
        onWriter.accept(builder);
        return builder.create();
    }

    public BlackDuckIoOperations operations() {
        BlackDuckIoOperations.Builder builder = BlackDuckIoOperations.build();
        onOperations.accept(builder);
        return builder.create();
    }

    // Low level

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
        GraphIoWrapperFactory factory = new GraphIoWrapperFactory();
        onWrapper.accept(factory);
        return factory;
    }

}
