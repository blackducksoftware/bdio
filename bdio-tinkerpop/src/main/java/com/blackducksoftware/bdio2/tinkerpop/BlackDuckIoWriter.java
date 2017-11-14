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

import static org.apache.tinkerpop.gremlin.process.traversal.P.without;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.hasNot;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.identity;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphWriter;

import com.blackducksoftware.bdio2.BdioWriter;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.blackducksoftware.bdio2.tinkerpop.GraphContextFactory.AbstractContextBuilder;
import com.github.jsonldjava.core.JsonLdConsts;

import io.reactivex.Flowable;

/**
 * A {@link GraphWriter} implementation that writes a graph and it's elements to a BDIO representation.
 *
 * @author jgustie
 */
public class BlackDuckIoWriter implements GraphWriter {

    private final GraphContextFactory contextFactory;

    private BlackDuckIoWriter(Builder builder) {
        contextFactory = builder.contextFactory();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeGraph(OutputStream outputStream, Graph graph) throws IOException {
        WriteGraphContext context = contextFactory.forBdioWritingFrom(graph);
        RxJavaBdioDocument document = context.mapper().newBdioDocument();

        // Collect the vertex labels which should be excluded from the output
        Collection<String> excludedLabels = new LinkedHashSet<>();
        context.topology().metadataLabel().ifPresent(excludedLabels::add);
        context.topology().forEachEmbeddedType((label, id) -> excludedLabels.add(label));

        try {
            Flowable.fromIterable(() -> context.traversal().V()

                    // Strip out the exclude labels
                    .hasLabel(without(excludedLabels))

                    // Strip out the implicit vertices since they weren't originally included
                    .where(context.topology().implicitKey().map(propertyKey -> hasNot(propertyKey)).orElse(identity()))

                    // Convert to JSON-LD
                    .map(t -> createNode(t.get(), context)))
                    .doOnTerminate(context::rollbackTx)

                    // TODO How do exceptions come through here?
                    .buffer(1000)
                    .blockingSubscribe(document.write(context.createMetadata(), new BdioWriter.BdioFile(outputStream)));
        } catch (UncheckedIOException e) {
            // TODO We loose the stack of the unchecked wrapper: `e.getCause().addSuppressed(e)`?
            throw e.getCause();
        }
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
    private Map<String, Object> createNode(Vertex vertex, WriteGraphContext context) {
        Map<String, Object> result = context.getNodeProperties(vertex);

        vertex.edges(Direction.OUT).forEachRemaining(e -> {
            // Skip implicit edges
            if (context.topology().implicitKey().filter(e.keys()::contains).isPresent()) {
                return;
            }

            // Convert the in vertex to a reference
            Vertex inVertex = e.inVertex();
            Object ref;
            if (context.topology().isEmbeddedLabel(inVertex.label())) {
                // Recursively create the entire node (without an '@id') for embedded types
                ref = createNode(inVertex, context);
                ((Map<?, ?>) ref).remove(JsonLdConsts.ID);
            } else {
                // For non-embedded types we just need the identifier
                ref = context.generateId(inVertex);
            }

            // Store the result as a reference value object
            Object valueObject = context.mapper().valueObjectMapper().toReferenceValueObject(ref);
            if (valueObject != null) {
                result.merge(e.label(), valueObject, context::combine);
            }
        });

        return result;
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractContextBuilder<BlackDuckIoWriter, Builder>
            implements WriterBuilder<BlackDuckIoWriter> {
        private Builder() {
            super(BlackDuckIoWriter::new);
        }
    }

}
