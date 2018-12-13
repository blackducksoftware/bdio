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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphWriter;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.BdioWriter;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoSpi;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoWriterSpi;

import io.reactivex.Flowable;

/**
 * A {@link GraphWriter} implementation that writes a graph and it's elements to a BDIO representation.
 *
 * @author jgustie
 */
public class BlackDuckIoWriter implements GraphWriter {

    private final BlackDuckIoOptions options;

    private final BdioFrame frame;

    private final int batchSize;

    private BlackDuckIoWriter(Builder builder) {
        options = Objects.requireNonNull(builder.options);
        frame = builder.mapper.createMapper();
        batchSize = builder.batchSize;
    }

    public void writeGraph(StreamSupplier out, List<TraversalStrategy<?>> strategies, Graph graph) throws IOException {
        // Create a new BDIO document using the graph context
        RxJavaBdioDocument document = new RxJavaBdioDocument(frame.context());

        // The writer SPI allows for graph implementation specific optimizations
        GraphTraversalSource g = graph.traversal().withStrategies(strategies.toArray(new TraversalStrategy<?>[strategies.size()]));
        BlackDuckIoWriterSpi spi = BlackDuckIoSpi.getForGraph(graph).writer(g, options, frame);

        try {
            Flowable.fromPublisher(spi.retrieveCompactedNodes())
                    // TODO How do exceptions come through here?
                    .buffer(batchSize)
                    .blockingSubscribe(document.write(spi.retrieveMetadata(), out));
        } catch (UncheckedIOException e) {
            // TODO We loose the stack of the unchecked wrapper: `e.getCause().addSuppressed(e)`?
            throw e.getCause();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeGraph(OutputStream outputStream, Graph graph) throws IOException {
        writeGraph(new BdioWriter.BdioFile(outputStream), Collections.emptyList(), graph);
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoWriter}.
     */
    @Override
    public void writeVertex(OutputStream outputStream, Vertex v, Direction direction) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoWriter}.
     */
    @Override
    public void writeVertex(OutputStream outputStream, Vertex v) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoWriter}.
     */
    @Override
    public void writeEdge(OutputStream outputStream, Edge e) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoWriter}.
     */
    @Override
    public void writeVertexProperty(OutputStream outputStream, @SuppressWarnings("rawtypes") VertexProperty vp) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoWriter}.
     */
    @Override
    public void writeProperty(OutputStream outputStream, @SuppressWarnings("rawtypes") Property p) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoWriter}.
     */
    @Override
    public void writeObject(OutputStream outputStream, Object object) throws IOException {
        throw new UnsupportedOperationException();
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder implements WriterBuilder<BlackDuckIoWriter> {

        private Mapper<BdioFrame> mapper;

        private BlackDuckIoOptions options;

        private int batchSize;

        private Builder() {
            mapper = BlackDuckIoMapper.build().create();
            options = BlackDuckIoOptions.build().create();
            batchSize = 1000;
        }

        public Builder mapper(Mapper<BdioFrame> mapper) {
            this.mapper = Objects.requireNonNull(mapper);
            return this;
        }

        public Builder options(BlackDuckIoOptions options) {
            this.options = Objects.requireNonNull(options);
            return this;
        }

        // TODO Batch size...slightly different concept here as it applies to the node buffer size...
        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        @Override
        public BlackDuckIoWriter create() {
            return new BlackDuckIoWriter(this);
        }
    }

}
