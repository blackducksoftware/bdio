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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphReader;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph.StarEdge;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph.StarVertex;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.blackducksoftware.bdio2.tinkerpop.GraphContextFactory.AbstractContextBuilder;

import io.reactivex.Flowable;

/**
 * A {@link GraphReader} that constructs a graph from a BDIO repreresentation.
 *
 * @author jgustie
 */
public final class BlackDuckIoReader implements GraphReader {

    private final GraphContextFactory contextFactory;

    private BlackDuckIoReader(Builder builder) {
        contextFactory = builder.contextFactory();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("CheckReturnValue")
    @Override
    public void readGraph(InputStream inputStream, Graph graph) throws IOException {
        ReadGraphContext context = contextFactory.forBdioReadingInto(graph);
        try {
            RxJavaBdioDocument doc = context.mapper().newBdioDocument();
            Flowable<Object> entries = doc.read(inputStream).publish().autoConnect(2);
            doc.metadata(entries).singleOrError().subscribe(context::createMetadata);
            doc.jsonLd(entries)
                    // Convert entries to individual nodes
                    .frame(context.mapper().frame())
                    .flatMapIterable(BdioDocument::toGraphNodes)

                    // Convert nodes to vertices
                    .map(node -> toVertex(node, null, null, Direction.OUT, context))

                    // Collect all of the vertices in a map, creating the actual vertices in the graph as we go
                    // THIS IS THE PART WHERE WE READ THE WHOLE GRAPH INTO MEMORY SO WE CAN CREATE THE EDGES
                    .reduce(new HashMap<>(), context::toMap)
                    .flatMapObservable(context::createEdges)

                    // Setup batch commits
                    .doOnSubscribe(x -> context.startBatchTx())
                    .doOnNext(x -> context.batchCommitTx())
                    .doOnError(x -> context.rollbackTx())
                    .doOnComplete(context::commitTx)

                    // Ignore values, propagate errors
                    .blockingSubscribe();

        } catch (UncheckedIOException e) {
            // TODO We loose the stack of the unchecked wrapper: `e.getCause().addSuppressed(e)`?
            throw e.getCause();
        }
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

    /**
     * Creates an in-memory vertex from the supplied node data.
     */
    private StarVertex toVertex(Map<String, Object> node,
            @Nullable Function<Attachable<Vertex>, Vertex> vertexAttachMethod,
            @Nullable Function<Attachable<Edge>, Edge> edgeAttachMethod,
            Direction attachEdgesOfThisDirection,
            ReadGraphContext context) {
        // Create a new StarGraph whose primary vertex is the converted node
        StarGraph starGraph = StarGraph.open();
        StarVertex vertex = (StarVertex) starGraph.addVertex(context.getNodeProperties(node, true));
        if (vertexAttachMethod != null) {
            vertex.attach(vertexAttachMethod);
        }

        // Add outgoing edges for object properties (if requested)
        if (attachEdgesOfThisDirection == Direction.BOTH || attachEdgesOfThisDirection == Direction.OUT) {
            for (Map.Entry<String, Object> property : node.entrySet()) {
                if (context.topology().isObjectPropertyKey(property.getKey())) {
                    context.mapper().valueObjectMapper().fromReferenceValueObject(property.getValue())
                            .map(id -> starGraph.addVertex(T.id, context.generateId(id)))
                            .map(inVertex -> (StarEdge) vertex.addEdge(property.getKey(), inVertex))
                            .forEach(edge -> {
                                if (edgeAttachMethod != null) {
                                    edge.attach(edgeAttachMethod);
                                }
                            });
                }
            }
        }

        return vertex;
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractContextBuilder<BlackDuckIoReader, Builder>
            implements ReaderBuilder<BlackDuckIoReader> {
        private Builder() {
            super(BlackDuckIoReader::new);
        }
    }

}
