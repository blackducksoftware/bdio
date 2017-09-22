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

import static com.blackducksoftware.common.base.ExtraThrowables.nullPointer;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.Io;
import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

public class BlackDuckIo implements Io<BlackDuckIoReader.Builder, BlackDuckIoWriter.Builder, BlackDuckIoMapper.Builder> {

    private final Graph graph;

    private final Consumer<BlackDuckIoMapper.Builder> onMapper;

    private BlackDuckIo(Builder builder) {
        graph = builder.graph.orElseThrow(nullPointer("The graph argument was not specified"));
        onMapper = mapperBuilder -> {
            // The direct registry addition API is deprecated by TinkerPop
            builder.registry.ifPresent(registry -> mapperBuilder.addRegistry(registry));

            // Allow user supplied mapper configuration
            builder.onMapper.ifPresent(onMapper -> onMapper.accept(mapperBuilder));

            // Always add the BDIO registry which customizes behavior based on the graph type
            BlackDuckIoRegistry registry = new BlackDuckIoRegistry(graph.configuration());
            builder.onGraphMapper.ifPresent(m -> registry.register(m::accept));
            mapperBuilder.addRegistry(registry);
        };
    }

    @Override
    public BlackDuckIoReader.Builder reader() {
        return BlackDuckIoReader.build().mapper(mapper().create());
    }

    @Override
    public BlackDuckIoWriter.Builder writer() {
        return BlackDuckIoWriter.build().mapper(mapper().create());
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

    // TODO Do we want to expose BlackDuckIoOperations here also?

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements Io.Builder<BlackDuckIo> {

        @Deprecated
        private Optional<IoRegistry> registry = Optional.empty();

        private Optional<Consumer<Mapper.Builder<?>>> onMapper = Optional.empty();

        private Optional<Graph> graph = Optional.empty();

        private Optional<Consumer<GraphMapper.Builder>> onGraphMapper = Optional.empty();

        // This is only public for IoCore.createIoBuilder
        public Builder() {
        }

        @Deprecated
        @Override
        public Builder registry(@Nullable IoRegistry registry) {
            this.registry = Optional.ofNullable(registry);
            return this;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Builder onMapper(@Nullable Consumer<Mapper.Builder> onMapper) {
            this.onMapper = onMapper != null ? Optional.of(onMapper::accept) : Optional.empty();
            return this;
        }

        @Override
        public Builder graph(Graph graph) {
            this.graph = Optional.of(graph);
            return this;
        }

        @Override
        public BlackDuckIo create() {
            return new BlackDuckIo(this);
        }

        public Builder onGraphMapper(@Nullable Consumer<GraphMapper.Builder> onGraphMapper) {
            this.onGraphMapper = Optional.ofNullable(onGraphMapper);
            return this;
        }

    }

}
