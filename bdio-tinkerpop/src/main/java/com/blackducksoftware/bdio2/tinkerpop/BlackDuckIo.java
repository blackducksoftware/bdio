/*
 * Copyright 2016 Black Duck Software, Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.Io;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoSpi;
import com.github.jsonldjava.core.RemoteDocument;

/**
 * Constructs BDIO I/O implementations given a {@link Graph} and some optional configuration.
 *
 * @author jgustie
 */
public class BlackDuckIo implements Io<BlackDuckIoReader.Builder, BlackDuckIoWriter.Builder, BlackDuckIoMapper.Builder> {

    /**
     * The graph to perform the I/O operations on.
     */
    private final Graph graph;

    /**
     * The version of BDIO to use.
     */
    private final BlackDuckIoVersion version;

    /**
     * Mapper configuration supplied by the graph implementation. Since this uses only uses the generic TinkerPop API,
     * it will primarily be used to add {@code IoRegistry} instances.
     */
    private final Optional<Consumer<Mapper.Builder<?>>> onMapper;

    /**
     * BDIO configuration supplied by the user. This configuration describes the graph topology as JSON-LD using a
     * context and additional information about which fully qualified identifiers are compacted to describe vertices.
     */
    private final Consumer<BlackDuckIoMapper.Builder> mapperConfiguration;

    /**
     * Options specific to the configuration of extra BDIO specific features. For example, preservation of JSON-LD
     * metadata as a vertex.
     */
    private final BlackDuckIoOptions options;

    private BlackDuckIo(Builder builder) {
        graph = Objects.requireNonNull(builder.graph);
        version = Objects.requireNonNull(builder.version);
        onMapper = Optional.ofNullable(builder.onMapper);
        mapperConfiguration = Objects.requireNonNull(builder.mapperConfiguration);
        options = Optional.ofNullable(builder.options)
                .orElseGet(() -> BlackDuckIoOptions.create(graph.configuration().subset("bdio")));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlackDuckIoMapper.Builder mapper() {
        BlackDuckIoMapper.Builder builder = BlackDuckIoMapper.build().version(version);
        onMapper.ifPresent(c -> c.accept(builder));
        mapperConfiguration.accept(builder);
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlackDuckIoReader.Builder reader() {
        return BlackDuckIoReader.build().mapper(mapper().create()).options(options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readGraph(String file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            reader().create().readGraph(in, graph);
        }
    }

    /**
     * Extended "read-graph" API support.
     *
     * @see BlackDuckIoReader#readGraph(InputStream, String, Object, List, Graph)
     */
    public void readGraph(InputStream inputStream, String base, Object expandContext, TraversalStrategy<?>... strategies) throws IOException {
        reader().create().readGraph(inputStream, base, expandContext, Arrays.asList(strategies), graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlackDuckIoWriter.Builder writer() {
        if (version == BlackDuckIoVersion.V2_1) {
            return BlackDuckIoWriter.build().mapper(mapper().create()).options(options);
        } else {
            throw new UnsupportedOperationException("write is not supported for version " + version);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeGraph(String file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            writer().create().writeGraph(out, graph);
        }
    }

    /**
     * Extended "write-graph" API support.
     *
     * @see BlackDuckIoWriter#writeGraph(StreamSupplier, List, Graph)
     */
    public void writeGraph(StreamSupplier outputStreams, TraversalStrategy<?>... strategies) throws IOException {
        writer().create().writeGraph(outputStreams, Arrays.asList(strategies), graph);
    }

    public BlackDuckIoNormalization.Builder normalization() {
        return BlackDuckIoNormalization.build().mapper(mapper().create()).options(options);
    }

    public void normalize(TraversalStrategy<?>... strategies) {
        normalization().create().normalize(Arrays.asList(strategies), graph);
    }

    public BlackDuckIoOptions options() {
        return options;
    }

    /**
     * Create a new builder using the default version of BDIO.
     */
    public static Builder build() {
        return build(BlackDuckIoVersion.defaultVersion());
    }

    /**
     * Create a new builder using the specified version of BDIO.
     */
    public static Builder build(BlackDuckIoVersion version) {
        return new Builder(version);
    }

    public final static class Builder implements Io.Builder<BlackDuckIo> {

        private final BlackDuckIoVersion version;

        private Graph graph;

        private Consumer<Mapper.Builder<?>> onMapper;

        private Consumer<BlackDuckIoMapper.Builder> mapperConfiguration = b -> {};

        private BlackDuckIoOptions options;

        /**
         * Intended for reflective use only, call {@link BlackDuckIo#build()} instead.
         *
         * @see org.apache.tinkerpop.gremlin.structure.io.IoCore#createIoBuilder(String)
         * @deprecated Use {@link BlackDuckIo#build()}
         */
        @Deprecated
        public Builder() {
            this(BlackDuckIoVersion.defaultVersion());
        }

        private Builder(BlackDuckIoVersion version) {
            this.version = Objects.requireNonNull(version);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Builder graph(@Nullable Graph graph) {
            this.graph = graph;
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Builder onMapper(@SuppressWarnings("rawtypes") @Nullable Consumer<Mapper.Builder> onMapper) {
            checkState(graph != null, "graph is required for onMapper");
            this.onMapper = BlackDuckIoSpi.getForGraph(graph).onMapper(onMapper);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <V> boolean requiresVersion(V version) {
            return this.version == version;
        }

        /**
         * Overwrite the BDIO options found in the graph configuration.
         */
        public Builder options(@Nullable BlackDuckIoOptions options) {
            this.options = options;
            return this;
        }

        /**
         * @see BlackDuckIoMapper.Builder#injectDocument(RemoteDocument)
         */
        public Builder injectDocument(RemoteDocument document) {
            mapperConfiguration = mapperConfiguration.andThen(b -> b.injectDocument(document));
            return this;
        }

        /**
         * @see BlackDuckIoMapper.Builder#context(String, Object)
         */
        public Builder context(String base, Object expandContext) {
            mapperConfiguration = mapperConfiguration.andThen(b -> b.context(base, expandContext));
            return this;
        }

        /**
         * @see BlackDuckIoMapper.Builder#classDetails(Collection, Collection)
         */
        public Builder classDetails(Collection<String> classIdentifiers, Collection<String> embeddedTypes) {
            mapperConfiguration = mapperConfiguration.andThen(b -> b.classDetails(classIdentifiers, embeddedTypes));
            return this;
        }

        /**
         * @see BlackDuckIoMapper.Builder#fromExistingFrame(BdioFrame)
         */
        public Builder fromExistingFrame(BdioFrame existingFrame) {
            mapperConfiguration = mapperConfiguration.andThen(b -> b.fromExistingFrame(existingFrame));
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public BlackDuckIo create() {
            checkArgument(graph != null, "The graph argument was not specified");
            return new BlackDuckIo(this);
        }
    }

}
