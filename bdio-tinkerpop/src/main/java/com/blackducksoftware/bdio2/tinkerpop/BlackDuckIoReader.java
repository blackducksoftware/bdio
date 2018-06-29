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
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphReader;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.reactivestreams.Publisher;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;

import io.reactivex.Flowable;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * A {@link GraphReader} that constructs a graph from a BDIO representation.
 *
 * @author jgustie
 */
public final class BlackDuckIoReader implements GraphReader {

    /**
     * Function that applies a graph wrapper.
     */
    private final Function<Graph, GraphReaderWrapper> graphWrapper;

    private BlackDuckIoReader(Builder builder) {
        graphWrapper = builder.wrapperFactory::wrapReader;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("CheckReturnValue")
    @Override
    public void readGraph(InputStream inputStream, Graph graph) throws IOException {
        GraphReaderWrapper wrapper = graphWrapper.apply(graph);
        RxJavaBdioDocument doc = new RxJavaBdioDocument(wrapper.bdioOptions());

        try {
            Flowable<Object> entries = doc.read(inputStream).publish().autoConnect(2);

            // Just call the default onError handler directly instead of wrapping it in OnErrorNotImplementedException
            doc.metadata(entries).singleOrError().subscribe(wrapper::createMetadata, RxJavaPlugins::onError);

            doc.jsonLd(entries)
                    .frame(wrapper.mapper().frame())
                    .compose(framedEntries -> readNodes(wrapper, framedEntries))
                    .doOnComplete(wrapper::commitTx)
                    .doOnError(x -> wrapper.rollbackTx())
                    .blockingSubscribe();

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
     * Reads framed JSON-LD input into the graph.
     *
     * @see SqlgNodeAccumulator
     * @see TinkerGraphNodeAccumulator
     * @see DefaultNodeAccumulator
     */
    private Publisher<?> readNodes(GraphReaderWrapper wrapper, Flowable<Map<String, Object>> framedEntries) {
        // `framedEntries` is a sequence of node lists, each list should have an upper bound around 2^15 elements
        if (SqlgNodeAccumulator.acceptWrapper(wrapper)) {
            return framedEntries
                    .map(BdioDocument::toGraphNodes)
                    .map(nodes -> nodes.stream()
                            .sorted(SqlgNodeAccumulator::nodeTypeOrder)
                            .reduce(new SqlgNodeAccumulator(wrapper), SqlgNodeAccumulator::addNode, SqlgNodeAccumulator::combine)
                            .flush())
                    .reduce(SqlgNodeAccumulator::combine)
                    .doOnSuccess(SqlgNodeAccumulator::finish)
                    .doOnSubscribe(x -> ((SqlgGraph) wrapper.graph()).tx().streamingBatchModeOn())
                    .toFlowable();
        } else if (TinkerGraphNodeAccumulator.acceptWrapper(wrapper)) {
            return framedEntries
                    .flatMapIterable(BdioDocument::toGraphNodes)
                    .reduce(new TinkerGraphNodeAccumulator(wrapper), TinkerGraphNodeAccumulator::addNode)
                    .doOnSuccess(TinkerGraphNodeAccumulator::finish)
                    .toFlowable();
        } else {
            return framedEntries
                    .flatMapIterable(BdioDocument::toGraphNodes)
                    .reduce(new DefaultNodeAccumulator(wrapper), DefaultNodeAccumulator::addNode)
                    .doOnSuccess(DefaultNodeAccumulator::finish)
                    .doOnSubscribe(x -> wrapper.startBatchTx())
                    .toFlowable();
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

        private final GraphIoWrapperFactory wrapperFactory;

        private Builder() {
            wrapperFactory = new GraphIoWrapperFactory();
        }

        public Builder mapper(Mapper<GraphMapper> mapper) {
            wrapperFactory.mapper(mapper::createMapper);
            return this;
        }

        public Builder addStrategies(Collection<TraversalStrategy<?>> strategies) {
            wrapperFactory.addStrategies(strategies);
            return this;
        }

        public Builder batchSize(int batchSize) {
            wrapperFactory.batchSize(batchSize);
            return this;
        }

        public Builder version(BlackDuckIoVersion version) {
            wrapperFactory.version(version);
            return this;
        }

        public Builder expandContext(Object expandContext) {
            wrapperFactory.expandContext(expandContext);
            return this;
        }

        @Override
        public BlackDuckIoReader create() {
            return new BlackDuckIoReader(this);
        }
    }

}
