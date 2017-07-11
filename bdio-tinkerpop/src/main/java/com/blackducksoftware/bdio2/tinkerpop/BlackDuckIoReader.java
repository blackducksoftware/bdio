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
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nullable;

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
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public final class BlackDuckIoReader implements GraphReader {

    /**
     * The configuration used for this reader.
     */
    private final BlackDuckIoConfig config;

    /**
     * The JSON-LD frame used to convert from BDIO to vertex data.
     */
    private final BdioFrame frame;

    /**
     * The number of graph mutations before a commit is attempted.
     */
    private final int batchSize;

    private BlackDuckIoReader(Builder builder) {
        config = builder.config.orElseGet(() -> BlackDuckIoConfig.build().create());
        frame = BdioFrame.create(ImmutableMap.of());
        batchSize = builder.batchSize;
    }

    @Override
    public void readGraph(InputStream inputStream, Graph graphToWriteTo) throws IOException {
        RxJavaBdioDocument document = config.newBdioDocument(RxJavaBdioDocument.class);
        ReadGraphContext context = config.newReadContext(graphToWriteTo, frame, batchSize);

        // Create a metadata subscription
        document.metadata(metadata -> createMetadata(metadata, context, frame, document.jsonld().options()));

        // Get the sequence of BDIO graph nodes and transform them in vertices and edges
        document.jsonld().frame(frame).compose(document.withoutMetadata())

                // Convert nodes to vertices and commit
                .map(node -> createVertex(node, null, null, Direction.OUT))
                .doOnNext(context::batchCommitTx)

                // Collect all of the vertices in a map, creating the actual vertices in the graph as we go
                .toMap(vertex -> vertex, vertex -> vertex.attach(context::upsert))

                // Create all the edges and commit
                .flatMapObservable(context::createEdges)
                .doOnNext(context::batchCommitTx)

                // Perform a final commit
                .doOnComplete(context::commitTx)
                .subscribe();

        // Read the supplied input stream
        document.read(inputStream);

        // TODO Error handling? Get the throwable off the processor?
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
            Function<Attachable<Edge>, Edge> edgeAttachMethod, Direction attachEdgesOfThisDirection) {
        // Create a new StarGraph whose primary vertex is the converted node
        StarGraph starGraph = StarGraph.open();
        StarVertex vertex = (StarVertex) starGraph.addVertex(BdioHelper.getNodeProperties(node, true, config, frame));
        if (vertexAttachMethod != null) {
            vertex.attach(vertexAttachMethod);
        }

        // Add outgoing edges for object properties (if requested)
        if (attachEdgesOfThisDirection == Direction.BOTH || attachEdgesOfThisDirection == Direction.OUT) {
            Maps.transformValues(Maps.filterKeys(node, frame::isObjectPropertyKey),
                    // TODO Does the ID mapping here need to have the partition ID applied to it for TinkerGraph?
                    Functions.compose(
                            id -> starGraph.addVertex(T.id, BdioHelper.generateId(config.partitionStrategy(), id)),
                            config.valueObjectMapper()::fromFieldValue))
                    .forEach((label, inVertex) -> {
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
    private void createMetadata(BdioMetadata metadata, ReadGraphContext context, BdioFrame frame, JsonLdOptions options) {
        if (config.metadataLabel().isPresent()) {
            GraphTraversalSource g = context.traversal();
            Vertex metadataVertex = g.V().hasLabel(config.metadataLabel().get()).tryNext().orElseGet(() -> g.addV(config.metadataLabel().get()).next());

            config.identifierKey().ifPresent(key -> {
                metadataVertex.property(key, metadata.id());
            });
            try {
                // Compact the metadata using the context extracted from frame
                Map<String, Object> compactMetadata = JsonLdProcessor.compact(metadata, frame, options);
                ElementHelper.attachProperties(metadataVertex, BdioHelper.getNodeProperties(compactMetadata, false, config, frame));
            } catch (JsonLdError e) {
                // TODO What can we do about this?
                e.printStackTrace();
            }
        }
    }

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements ReaderBuilder<BlackDuckIoReader> {

        private Optional<BlackDuckIoConfig> config = Optional.empty();

        private int batchSize = 10000;

        private Builder() {
        }

        public Builder config(@Nullable BlackDuckIoConfig config) {
            this.config = Optional.ofNullable(config);
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
