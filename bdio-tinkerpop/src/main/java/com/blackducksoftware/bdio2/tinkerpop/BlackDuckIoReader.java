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
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphReader;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph.StarEdge;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph.StarVertex;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.blackducksoftware.bdio2.tinkerpop.GraphContextFactory.AbstractContextBuilder;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.google.common.base.Functions;
import com.google.common.collect.Maps;

public final class BlackDuckIoReader implements GraphReader {

    private final GraphContextFactory contextFactory;

    private BlackDuckIoReader(Builder builder) {
        contextFactory = builder.contextFactory();
    }

    @Override
    public void readGraph(InputStream inputStream, Graph graph) throws IOException {
        ReadGraphContext context = contextFactory.read(graph);
        RxJavaBdioDocument document = context.mapper().newBdioDocument(RxJavaBdioDocument.class);

        // Create a metadata subscription
        document.metadata(metadata -> createMetadata(metadata, context, document.jsonld().options()));

        // Get the sequence of BDIO graph nodes and transform them in vertices and edges
        document.jsonld().frame(context.mapper().frame()).compose(document.withoutMetadata())

                // Convert nodes to vertices
                .map(node -> createVertex(node, null, null, Direction.OUT, context))

                // Collect all of the vertices in a map, creating the actual vertices in the graph as we go
                .toMap(vertex -> vertex, vertex -> vertex.attach(context::upsert))

                // Create all the edges
                .flatMapObservable(context::createEdges)

                // Setup batch commits
                .doOnSubscribe(x -> context.startBatchTx())
                .doOnNext(x -> context.batchCommitTx())
                .doOnComplete(context::commitTx)
                .subscribe();

        // Read the supplied input stream
        document.read(inputStream);

        // TODO Error handling? Right now it just goes to the RxJavaPlugin
    }

    @Override
    public Vertex readVertex(InputStream inputStream, Function<Attachable<Vertex>, Vertex> vertexAttachMethod) throws IOException {
        return readVertex(inputStream, vertexAttachMethod, null, null);
    }

    @Override
    public Vertex readVertex(InputStream inputStream, Function<Attachable<Vertex>, Vertex> vertexAttachMethod,
            Function<Attachable<Edge>, Edge> edgeAttachMethod, Direction attachEdgesOfThisDirection) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Vertex> readVertices(InputStream inputStream, Function<Attachable<Vertex>, Vertex> vertexAttachMethod,
            Function<Attachable<Edge>, Edge> edgeAttachMethod, Direction attachEdgesOfThisDirection) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Edge readEdge(InputStream inputStream, Function<Attachable<Edge>, Edge> edgeAttachMethod) throws IOException {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public VertexProperty readVertexProperty(InputStream inputStream, Function<Attachable<VertexProperty>, VertexProperty> vertexPropertyAttachMethod)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Property readProperty(InputStream inputStream, Function<Attachable<Property>, Property> propertyAttachMethod) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <C> C readObject(InputStream inputStream, Class<? extends C> clazz) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates an in-memory vertex from the supplied node data.
     */
    private StarVertex createVertex(Map<String, Object> node, Function<Attachable<Vertex>, Vertex> vertexAttachMethod,
            Function<Attachable<Edge>, Edge> edgeAttachMethod, Direction attachEdgesOfThisDirection, ReadGraphContext context) {
        // Create a new StarGraph whose primary vertex is the converted node
        StarGraph starGraph = StarGraph.open();
        StarVertex vertex = (StarVertex) starGraph.addVertex(context.getNodeProperties(node, true));
        if (vertexAttachMethod != null) {
            vertex.attach(vertexAttachMethod);
        }

        // Add outgoing edges for object properties (if requested)
        if (attachEdgesOfThisDirection == Direction.BOTH || attachEdgesOfThisDirection == Direction.OUT) {
            Map<String, Object> objectProperties = Maps.filterKeys(node, context.mapper()::isObjectPropertyKey);

            com.google.common.base.Function<Object, Vertex> toVertex = Functions.compose(
                    // Using the identifier returned by "fromFieldValue", create a vertex
                    id -> starGraph.addVertex(T.id, context.generateId(id)),

                    // There is no "fromReferenceFieldValue", data and object properties share "fromFieldValue"
                    context.mapper().valueObjectMapper()::fromFieldValue);

            Maps.transformValues(objectProperties, toVertex).forEach((label, inVertex) -> {
                StarEdge edge = (StarEdge) vertex.addEdge(label, inVertex);
                if (edgeAttachMethod != null) {
                    edge.attach(edgeAttachMethod);
                }
            });
        }

        return vertex;
    }

    /**
     * If a metadata label is configured, store the supplied BDIO metadata on a vertex in the graph.
     */
    private void createMetadata(BdioMetadata metadata, ReadGraphContext context, JsonLdOptions options) {
        if (context.mapper().metadataLabel().isPresent()) {
            GraphTraversalSource g = context.traversal();
            Vertex metadataVertex = g.V().hasLabel(context.mapper().metadataLabel().get()).tryNext()
                    .orElseGet(() -> g.addV(context.mapper().metadataLabel().get()).next());

            context.mapper().identifierKey().ifPresent(key -> {
                metadataVertex.property(key, metadata.id());
            });
            try {
                // Compact the metadata using the context extracted from frame
                Map<String, Object> compactMetadata = JsonLdProcessor.compact(metadata, context.mapper().frame(), options);
                ElementHelper.attachProperties(metadataVertex, context.getNodeProperties(compactMetadata, false));
            } catch (JsonLdError e) {
                // TODO What can we do about this?
                e.printStackTrace();
            }
        }
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
