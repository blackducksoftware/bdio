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

import static org.apache.tinkerpop.gremlin.process.traversal.P.without;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.hasNot;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.identity;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphWriter;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

import com.blackducksoftware.bdio2.BdioWriter;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.github.jsonldjava.core.JsonLdConsts;

import io.reactivex.Flowable;

/**
 * A {@link GraphWriter} implementation that writes a graph and it's elements to a BDIO representation.
 *
 * @author jgustie
 */
public class BlackDuckIoWriter implements GraphWriter {

    /**
     * Function that applies a graph wrapper.
     */
    private final Function<Graph, GraphWriterWrapper> graphWrapper;

    /**
     * The number of nodes to generate before batching to the output stream.
     */
    private final int batchSize;

    private BlackDuckIoWriter(Builder builder) {
        graphWrapper = builder.wrapperFactory::wrapWriter;
        batchSize = 1000;
    }

    /**
     * Writes the supplied graph to one or more output streams (partitioning based on size).
     *
     * @param out
     *            the supplier of output streams to write the graph to
     * @param graph
     *            the graph to write
     * @throws IOException
     *             if an error occurs writing the graph
     * @see {@link #writeGraph(OutputStream, Graph)}
     */
    public void writeGraph(StreamSupplier out, Graph graph) throws IOException {
        GraphWriterWrapper wrapper = graphWrapper.apply(graph);
        RxJavaBdioDocument document = new RxJavaBdioDocument(wrapper.bdioOptions());

        try {
            Flowable.fromIterable(() -> wrapper.traversal().V()
                    // Strip out the excluded labels
                    .hasLabel(without(wrapper.mapper().excludedLabels()))

                    // Strip out the implicit vertices since they weren't originally included
                    .where(wrapper.mapper().implicitKey().map(propertyKey -> hasNot(propertyKey)).orElse(identity()))

                    // Convert to JSON-LD
                    .map(t -> createNode(t.get(), wrapper)))
                    .doOnTerminate(wrapper::rollbackTx)

                    // TODO How do exceptions come through here?
                    .buffer(batchSize)
                    .blockingSubscribe(document.write(wrapper.createMetadata(), out));
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
        writeGraph(new BdioWriter.BdioFile(outputStream), graph);
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

    /**
     * Creates a JSON-LD node from a vertex (or multiple vertices in the case of embedded objects).
     */
    private Map<String, Object> createNode(Vertex vertex, GraphWriterWrapper wrapper) {
        GraphMapper mapper = wrapper.mapper();
        Map<String, Object> result = wrapper.getNodeProperties(vertex);

        vertex.edges(Direction.OUT).forEachRemaining(e -> {
            // Skip implicit edges
            if (mapper.implicitKey().isPresent() && e.keys().stream().anyMatch(mapper.implicitKey().get()::equals)) {
                return;
            }

            // Convert the in vertex to a reference
            Vertex inVertex = e.inVertex();
            Object ref;
            if (mapper.isEmbeddedLabel(inVertex.label())) {
                // Recursively create the entire node (without an '@id') for embedded types
                ref = createNode(inVertex, wrapper);
                ((Map<?, ?>) ref).remove(JsonLdConsts.ID);
            } else {
                // For non-embedded types we just need the identifier
                ref = wrapper.generateId(inVertex);
            }

            // Store the result as a reference value object
            Object valueObject = mapper.valueObjectMapper().toReferenceValueObject(ref);
            if (valueObject != null) {
                result.merge(e.label(), valueObject, wrapper::combine);
            }
        });

        return result;
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder implements WriterBuilder<BlackDuckIoWriter> {

        private final GraphIoWrapperFactory wrapperFactory;

        private Builder() {
            wrapperFactory = new GraphIoWrapperFactory();
        }

        public Builder mapper(Mapper<GraphMapper> mapper) {
            wrapperFactory.mapper(mapper::createMapper);
            return this;
        }

        // TODO Batch size...slightly different concept here as it applies to the node buffer size...

        public Builder addStrategies(Collection<TraversalStrategy<?>> strategies) {
            wrapperFactory.addStrategies(strategies);
            return this;
        }

        @Override
        public BlackDuckIoWriter create() {
            return new BlackDuckIoWriter(this);
        }
    }

}
