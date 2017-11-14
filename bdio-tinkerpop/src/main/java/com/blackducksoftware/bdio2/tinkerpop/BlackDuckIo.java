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

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.Io;
import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

/**
 * Constructs BDIO I/O implementations given a {@link Graph} and some optional configuration.
 *
 * @author jgustie
 */
public class BlackDuckIo implements Io<BlackDuckIoReader.Builder, BlackDuckIoWriter.Builder, BlackDuckIoMapper.Builder> {

    /**
     * The graph to apply I/O operations to.
     */
    private final Graph graph;

    /**
     * The BDIO configuration represented as {@link BlackDuckIoMapper} initialization callback.
     */
    private final Consumer<BlackDuckIoMapper.Builder> onMapper;

    private BlackDuckIo(Builder builder) {
        graph = builder.graph.orElseThrow(nullPointer("The graph argument was not specified"));
        onMapper = m -> {
            // Extract settings from the graph's configuration and user supplied callbacks on the builder
            Configuration configuration = graph.configuration().subset("bdio");
            m.onGraphTopology(gt -> {
                gt.withConfiguration(configuration);
                builder.onGraphTopology.ifPresent(c -> c.accept(gt));
            });
            m.onGraphMapper(gm -> {
                gm.withConfiguration(configuration);
                builder.onGraphMapper.ifPresent(c -> c.accept(gm));
            });

            // The BDIO registry MUST be first as it provides the default schema and other graph specific features
            m.addRegistry(new BlackDuckIoRegistry(graph));

            // These registrations are required by the TinkerPop API
            builder.registry.ifPresent(m::addRegistry);
            builder.onMapper.ifPresent(c -> c.accept(m));
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlackDuckIoMapper.Builder mapper() {
        BlackDuckIoMapper.Builder builder = BlackDuckIoMapper.build();
        onMapper.accept(builder);
        return builder;
    }

    // #########
    // # Input #
    // #########

    /**
     * {@inheritDoc}
     */
    @Override
    public BlackDuckIoReader.Builder reader() {
        return BlackDuckIoReader.build().mapper(mapper().create());
    }

    /**
     * Read a {@link Graph} from an arbitrary input stream using the default configuration of the {@link #reader()} and
     * its supplied {@link #mapper()}.
     */
    public void readGraph(InputStream inputStream) throws IOException {
        reader().create().readGraph(inputStream, graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readGraph(String file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            readGraph(in);
        }
    }

    // ##########
    // # Output #
    // ##########

    /**
     * {@inheritDoc}
     */
    @Override
    public BlackDuckIoWriter.Builder writer() {
        return BlackDuckIoWriter.build().mapper(mapper().create());
    }

    /**
     * Write a {@link Graph} to an arbitrary output stream using the default configuration of the {@link #writer()} and
     * its supplied {@link #mapper()}.
     */
    public void writeGraph(OutputStream outputStream) throws IOException {
        writer().create().writeGraph(outputStream, graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeGraph(String file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            writeGraph(out);
        }
    }

    // #################
    // # Normalization #
    // #################

    /**
     * Creates a {@link BlackDuckIoOperations.Builder} implementation with the default configuration of the
     * {@link #mapper()}. While operations are do not directly read or write, they may be required as a post operation
     * to normalize data after a read and before a write.
     */
    public BlackDuckIoOperations.Builder operations() {
        return BlackDuckIoOperations.build().mapper(mapper().create());
    }

    /**
     * Apply BDIO semantic rules to a {@link Graph} using the default configuration of the {@link #operations()} and
     * its supplied {@link #mapper()}.
     */
    public void applySemanticRules() {
        operations().create().applySemanticRules(graph);
    }

    // #########
    // # Setup #
    // #########

    /**
     * Create a new builder.
     */
    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements Io.Builder<BlackDuckIo> {

        @Deprecated
        private Optional<IoRegistry> registry = Optional.empty();

        private Optional<Consumer<Mapper.Builder<?>>> onMapper = Optional.empty();

        private Optional<Graph> graph = Optional.empty();

        private Optional<Consumer<GraphTopology.Builder>> onGraphTopology = Optional.empty();

        private Optional<Consumer<GraphMapper.Builder>> onGraphMapper = Optional.empty();

        /**
         * Intended for reflective use only, call {@link BlackDuckIo#build()} instead.
         *
         * @see org.apache.tinkerpop.gremlin.structure.io.IoCore#createIoBuilder(String)
         */
        public Builder() {
        }

        @Deprecated
        @Override
        public Builder registry(@Nullable IoRegistry registry) {
            this.registry = Optional.ofNullable(registry);
            return this;
        }

        // This exposes the API for calling "add registry" multiple times
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

        /**
         * Sets or replaces the topology configuration callback on this I/O builder.
         */
        public Builder onGraphTopology(@Nullable Consumer<GraphTopology.Builder> onGraphTopology) {
            this.onGraphTopology = Optional.ofNullable(onGraphTopology);
            return this;
        }

        /**
         * Sets or replaces the mapper configuration callback on this I/O builder.
         */
        public Builder onGraphMapper(@Nullable Consumer<GraphMapper.Builder> onGraphMapper) {
            this.onGraphMapper = Optional.ofNullable(onGraphMapper);
            return this;
        }

    }

}
