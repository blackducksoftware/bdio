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
import java.util.Objects;
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

    @Nullable
    private final BdioDocument.Builder documentBuilder;

    @Nullable
    private final PartitionStrategy partitionStrategy;

    private BlackDuckIo(Builder builder) {
        graph = Objects.requireNonNull(builder.graph, "The graph argument was not specified");
        documentBuilder = builder.documentBuilder;
        partitionStrategy = builder.partitionStrategy;
    }

    @Override
    public BlackDuckIoReader.Builder reader() {
        return BlackDuckIoReader.build();
    }

    @Override
    public BlackDuckIoWriter.Builder writer() {
        return BlackDuckIoWriter.build();
    }

    @Override
    public BlackDuckIoMapper.Builder mapper() {
        return BlackDuckIoMapper.build();
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
        writer().documentBuilder(documentBuilder).partitionStrategy(partitionStrategy).create().writeGraph(outputStream, graph);
    }

    public void readGraph(InputStream inputStream) throws IOException {
        reader().documentBuilder(documentBuilder).partitionStrategy(partitionStrategy).create().readGraph(inputStream, graph);
    }

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements Io.Builder<BlackDuckIo> {

        private Graph graph;

        @Nullable
        private BdioDocument.Builder documentBuilder;

        @Nullable
        private PartitionStrategy partitionStrategy;

        @Override
        @Deprecated
        public Builder registry(IoRegistry registry) {
            // Ignore this, BDIO does not support custom mapping logic
            return this;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Builder onMapper(Consumer<Mapper.Builder> onMapper) {
            return this;
        }

        @Override
        public Builder graph(Graph graph) {
            this.graph = graph;
            return this;
        }

        public Builder documentBuilder(@Nullable BdioDocument.Builder documentBuilder) {
            this.documentBuilder = documentBuilder;
            return this;
        }

        public Builder partitionStrategy(@Nullable PartitionStrategy partitionStrategy) {
            this.partitionStrategy = partitionStrategy;
            return this;
        }

        @Override
        public BlackDuckIo create() {
            return new BlackDuckIo(this);
        }
    }

}
