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

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.Io;
import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

public class BlackDuckIo implements Io<BlackDuckIoReader.Builder, BlackDuckIoWriter.Builder, BlackDuckIoMapper.Builder> {

    private final Graph graph;

    private final Consumer<BlackDuckIoMapper.Builder> onMapper;

    private final Consumer<BlackDuckIoConfig.Builder> onConfig;

    private BlackDuckIo(Builder builder) {
        graph = builder.graph.orElseThrow(() -> new NullPointerException("The graph argument was not specified"));

        onMapper = mapperBuilder -> {
            // Deprecated support
            builder.registry.ifPresent(mapperBuilder::addRegistry);

            // TODO Eventually we just need to save `builder.onMapper.orElse(b -> {})`
            builder.onMapper.ifPresent(c -> c.accept(mapperBuilder));
        };

        // Setup the on-configuration hook to override the value object mapper
        onConfig = builder.onConfig
                .orElse(BlackDuckIo::noop)
                .andThen(configBuilder -> configBuilder.valueObjectMapper(mapper().create().createMapper()));
    }

    /**
     * Formatting rules make <code>x -> {}</code> super ugly.
     */
    private static <I> void noop(I ignored) {
    }

    @Override
    public BlackDuckIoReader.Builder reader() {
        BlackDuckIoReader.Builder builder = BlackDuckIoReader.build();
        builder.config(config().create());
        return builder;
    }

    @Override
    public BlackDuckIoWriter.Builder writer() {
        BlackDuckIoWriter.Builder builder = BlackDuckIoWriter.build();
        builder.config(config().create());
        return builder;
    }

    @Override
    public BlackDuckIoMapper.Builder mapper() {
        BlackDuckIoMapper.Builder builder = BlackDuckIoMapper.build();
        onMapper.accept(builder);
        return builder;
    }

    public BlackDuckIoConfig.Builder config() {
        BlackDuckIoConfig.Builder builder = BlackDuckIoConfig.build();
        onConfig.accept(builder);
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

        private Optional<Graph> graph = Optional.empty();

        @SuppressWarnings("rawtypes")
        private Optional<Consumer<Mapper.Builder>> onMapper = Optional.empty();

        private Optional<Consumer<BlackDuckIoConfig.Builder>> onConfig = Optional.empty();

        @Deprecated
        private Optional<IoRegistry> registry = Optional.empty();

        private Builder() {
        }

        @Override
        public Builder graph(Graph graph) {
            this.graph = Optional.of(graph);
            return this;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Builder onMapper(@Nullable Consumer<Mapper.Builder> onMapper) {
            this.onMapper = Optional.ofNullable(onMapper);
            return this;
        }

        /**
         * Sets the on-configuration hook for this IO instance.
         * <p>
         * <em>WARNING</em>: the value object mapper will be overwritten!
         */
        public Builder onConfig(@Nullable Consumer<BlackDuckIoConfig.Builder> onConfig) {
            this.onConfig = Optional.ofNullable(onConfig);
            return this;
        }

        @Override
        public BlackDuckIo create() {
            return new BlackDuckIo(this);
        }

        /**
         * @deprecated Instead, use {@link #onMapper(Consumer)}, as in
         *             {@code onMapper(builder -> builder.addRegistry(registry))}. Just remember that you can only have
         *             one on-mapper.
         */
        @Override
        @Deprecated
        public Builder registry(@Nullable IoRegistry registry) {
            this.registry = Optional.ofNullable(registry);
            return this;
        }
    }

}
