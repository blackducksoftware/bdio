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

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphReader;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;

import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoReaderSpi;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoSpi;

import io.reactivex.Flowable;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * A {@link GraphReader} that constructs a graph from a BDIO representation.
 *
 * @author jgustie
 */
public final class BlackDuckIoReader implements GraphReader {

    private final BlackDuckIoOptions options;

    private final BdioFrame frame;

    private final int batchSize;

    private BlackDuckIoReader(Builder builder) {
        options = Objects.requireNonNull(builder.options);
        frame = builder.mapper.createMapper();
        batchSize = builder.batchSize;
    }

    @SuppressWarnings("CheckReturnValue")
    public void readGraph(InputStream inputStream, String base, Object expandContext, List<TraversalStrategy<?>> strategies, Graph graph) throws IOException {
        // Create a new BDIO document using a new document context derived from the graph context
        RxJavaBdioDocument doc = new RxJavaBdioDocument(frame.context().newBuilder().base(base).expandContext(expandContext).build());

        // The reader SPI allows for graph implementation specific optimizations
        GraphTraversalSource g = graph.traversal().withStrategies(strategies.toArray(new TraversalStrategy<?>[strategies.size()]));
        BlackDuckIoReaderSpi spi = BlackDuckIoSpi.getForGraph(graph).reader(g, options, frame, batchSize);

        try {
            // Read the input stream as sequence of "entries" (individual JSON-LD documents)
            Flowable<Object> entries = doc.read(inputStream);

            // If we are persisting metadata, create a separate subscription just for that
            if (options.metadataLabel().isPresent()) {
                entries = entries.publish().autoConnect(2);
                doc.metadata(entries).singleOrError().subscribe(spi::persistMetadata, RxJavaPlugins::onError);
            }

            // Frame the entries and do a blocking persist
            doc.jsonLd(entries).frame(frame.serialize()).compose(spi::persistFramedEntries).blockingSubscribe();
        } catch (RuntimeException e) {
            Throwable failure = unwrap(e);
            throwIfInstanceOf(failure, IOException.class);
            throwIfUnchecked(failure);
            if (failure instanceof NodeDoesNotExistException) {
                throw new BlackDuckIoReadGraphException("Failed to load BDIO due to invalid references in the input", failure);
            } else if (failure instanceof SQLException) {
                throw new BlackDuckIoReadGraphException("Failed to load BDIO due to a database error", failure);
            }

            // Add a check above and throw a BlackDuckIoReadGraphException with a nice message instead
            throw new IllegalStateException("Unexpected checked exception in readGraph", failure);
        }
    }

    /**
     * Unwraps an exception thrown by {@code Flowable.blockingSubscribe()}.
     */
    private Throwable unwrap(RuntimeException failure) {
        // Blocking subscribe uses a raw RuntimeException to wrap checked exceptions so check the actual type
        if (failure.getClass().equals(RuntimeException.class) || failure instanceof UncheckedIOException) {
            // Only unwrap checked exceptions
            if (failure.getCause() != null && !(failure.getCause() instanceof RuntimeException)) {
                return failure.getCause();
            }
        }
        return failure;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readGraph(InputStream inputStream, Graph graph) throws IOException {
        readGraph(inputStream, "", null, Collections.emptyList(), graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Vertex readVertex(InputStream inputStream, Function<Attachable<Vertex>, Vertex> vertexAttachMethod) throws IOException {
        return readVertex(inputStream, vertexAttachMethod, null, null);
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoReader}.
     */
    @Override
    public Vertex readVertex(InputStream inputStream, Function<Attachable<Vertex>, Vertex> vertexAttachMethod,
            Function<Attachable<Edge>, Edge> edgeAttachMethod, Direction attachEdgesOfThisDirection) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoReader}.
     */
    @Override
    public Iterator<Vertex> readVertices(InputStream inputStream, Function<Attachable<Vertex>, Vertex> vertexAttachMethod,
            Function<Attachable<Edge>, Edge> edgeAttachMethod, Direction attachEdgesOfThisDirection) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoReader}.
     */
    @Override
    public Edge readEdge(InputStream inputStream, Function<Attachable<Edge>, Edge> edgeAttachMethod) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoReader}.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public VertexProperty readVertexProperty(InputStream inputStream, Function<Attachable<VertexProperty>, VertexProperty> vertexPropertyAttachMethod)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoReader}.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public Property readProperty(InputStream inputStream, Function<Attachable<Property>, Property> propertyAttachMethod) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported by the {@code BlackDuckIoReader}.
     */
    @Override
    public <C> C readObject(InputStream inputStream, Class<? extends C> clazz) throws IOException {
        throw new UnsupportedOperationException();
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder implements ReaderBuilder<BlackDuckIoReader> {

        private Mapper<BdioFrame> mapper;

        private BlackDuckIoOptions options;

        private int batchSize;

        private Builder() {
            mapper = BlackDuckIoMapper.build().create();
            options = BlackDuckIoOptions.build().create();
            batchSize = 10_000;
        }

        public Builder mapper(Mapper<BdioFrame> mapper) {
            this.mapper = Objects.requireNonNull(mapper);
            return this;
        }

        public Builder options(BlackDuckIoOptions options) {
            this.options = Objects.requireNonNull(options);
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        @Override
        public BlackDuckIoReader create() {
            return new BlackDuckIoReader(this);
        }
    }

}
