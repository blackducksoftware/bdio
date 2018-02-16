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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.Io;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.umlg.sqlg.structure.SqlgGraph;

/**
 * Constructs BDIO I/O implementations given a {@link Graph} and some optional configuration.
 *
 * @author jgustie
 */
public class BlackDuckIo implements Io<BlackDuckIoReader.Builder, BlackDuckIoWriter.Builder, BlackDuckIoMapper.Builder> {

    private final Graph graph;

    private final BlackDuckIoVersion version;

    private final Optional<Consumer<Mapper.Builder<?>>> onMapper;

    private BlackDuckIo(Builder builder) {
        graph = Objects.requireNonNull(builder.graph);
        version = Objects.requireNonNull(builder.version);
        onMapper = Optional.ofNullable(builder.onMapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlackDuckIoMapper.Builder mapper() {
        BlackDuckIoMapper.Builder builder = BlackDuckIoMapper.build().version(version);
        if (graph instanceof SqlgGraph) {
            builder.addRegistry(SqlgIoRegistryBdio.instance());
        }
        onMapper.ifPresent(c -> c.accept(builder));
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlackDuckIoReader.Builder reader() {
        return BlackDuckIoReader.build().mapper(mapper().create());
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
     * {@inheritDoc}
     */
    @Override
    public BlackDuckIoWriter.Builder writer() {
        return BlackDuckIoWriter.build().mapper(mapper().create());
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

        /**
         * Intended for reflective use only, call {@link BlackDuckIo#build()} instead.
         *
         * @see org.apache.tinkerpop.gremlin.structure.io.IoCore#createIoBuilder(String)
         */
        public Builder() {
            this(BlackDuckIoVersion.defaultVersion());
        }

        private Builder(BlackDuckIoVersion version) {
            this.version = Objects.requireNonNull(version);
        }

        /**
         * Graph implementation provider use only.
         */
        @SuppressWarnings("rawtypes")
        @Override
        public Builder onMapper(@Nullable Consumer<Mapper.Builder> onMapper) {
            this.onMapper = onMapper != null ? onMapper::accept : null;
            return this;
        }

        @Override
        public Builder graph(@Nullable Graph graph) {
            this.graph = graph;
            return this;
        }

        @Override
        public <V> boolean requiresVersion(V version) {
            return this.version == version;
        }

        @Override
        public BlackDuckIo create() {
            checkArgument(graph != null, "The graph argument was not specified");
            return new BlackDuckIo(this);
        }
    }

}
