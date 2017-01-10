/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio2.tinkerpop;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.Io;
import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

import com.blackducksoftware.bdio2.BdioDocument;

public class BlackDuckIo implements Io<BlackDuckIoReader.Builder, BlackDuckIoWriter.Builder, BlackDuckIoMapper.Builder> {

    private final Graph graph;

    private final Consumer<BlackDuckIoMapper.Builder> onMapper;

    @Nullable
    private final BdioDocument.Builder documentBuilder;

    @Nullable
    private final PartitionStrategy partitionStrategy;

    private BlackDuckIo(Builder builder) {
        graph = builder.graph.orElseThrow(() -> new NullPointerException("The graph argument was not specified"));
        documentBuilder = builder.documentBuilder.orElse(null);
        partitionStrategy = builder.partitionStrategy.orElse(null);
        onMapper = mapperBuilder -> {
            builder.registry.ifPresent(mapperBuilder::addRegistry);
            builder.onMapper.ifPresent(c -> c.accept(mapperBuilder));
        };
    }

    @Override
    public BlackDuckIoReader.Builder reader() {
        return BlackDuckIoReader.build()
                .mapper(mapper().create())
                .documentBuilder(documentBuilder)
                .partitionStrategy(partitionStrategy);
    }

    @Override
    public BlackDuckIoWriter.Builder writer() {
        return BlackDuckIoWriter.build()
                .mapper(mapper().create())
                .documentBuilder(documentBuilder)
                .partitionStrategy(partitionStrategy);
    }

    @Override
    public BlackDuckIoMapper.Builder mapper() {
        BlackDuckIoMapper.Builder builder = BlackDuckIoMapper.build();
        onMapper.accept(builder);
        return builder;
    }

    @Override
    public void writeGraph(String file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            writeGraph(out);
        }
    }

    @Override
    public void readGraph(String file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            readGraph(in);
        }
    }

    public void writeGraph(OutputStream outputStream) throws IOException {
        writer().create().writeGraph(outputStream, graph);
    }

    public void readGraph(InputStream inputStream) throws IOException {
        reader().create().readGraph(inputStream, graph);
    }

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements Io.Builder<BlackDuckIo> {

        private Optional<IoRegistry> registry = Optional.empty();

        private Optional<Graph> graph = Optional.empty();

        @SuppressWarnings("rawtypes")
        private Optional<Consumer<Mapper.Builder>> onMapper = Optional.empty();

        private Optional<BdioDocument.Builder> documentBuilder = Optional.empty();

        private Optional<PartitionStrategy> partitionStrategy = Optional.empty();

        private Builder() {
        }

        @Override
        @Deprecated
        public Builder registry(@Nullable IoRegistry registry) {
            this.registry = Optional.ofNullable(registry);
            return this;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Builder onMapper(@Nullable Consumer<Mapper.Builder> onMapper) {
            this.onMapper = Optional.ofNullable(onMapper);
            return this;
        }

        @Override
        public Builder graph(Graph graph) {
            this.graph = Optional.of(graph);
            return this;
        }

        public Builder documentBuilder(@Nullable BdioDocument.Builder documentBuilder) {
            this.documentBuilder = Optional.ofNullable(documentBuilder);
            return this;
        }

        public Builder partitionStrategy(@Nullable PartitionStrategy partitionStrategy) {
            this.partitionStrategy = Optional.ofNullable(partitionStrategy);
            return this;
        }

        @Override
        public BlackDuckIo create() {
            return new BlackDuckIo(this);
        }
    }

}
