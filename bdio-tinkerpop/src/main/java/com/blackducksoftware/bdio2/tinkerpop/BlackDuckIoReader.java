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
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.google.common.base.Functions;
import com.google.common.collect.Maps;

import io.reactivex.Flowable;

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
        frame = BdioFrame.create(builder.applicationContext);
        batchSize = builder.batchSize;
    }

    @Override
    public void readGraph(InputStream inputStream, Graph graphToWriteTo) throws IOException {
        RxJavaBdioDocument document = config.newBdioDocument(RxJavaBdioDocument.class);
        ReadGraphContext context = createReadGraphContext(graphToWriteTo);

        // Create a metadata subscription
        document.metadata(metadata -> context.createMetadata(metadata, frame, document.jsonld().options()));

        // Get the sequence of BDIO graph nodes and transform them in vertices and edges
        document.jsonld().frame(frame).compose(document.withoutMetadata())

                // Convert nodes to vertices and commit
                .flatMap(this::readVertex)
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
        // Create a new StarGraph whose primary vertex is the converted node
        Map<String, Object> node = NodeInputStream.readNode(inputStream);
        StarGraph starGraph = StarGraph.open();
        StarVertex vertex = (StarVertex) starGraph
                .addVertex(BdioHelper.getNodeProperties(node, true, frame, config.valueObjectMapper(), config.partitionStrategy().orElse(null)));
        if (vertexAttachMethod != null) {
            vertex.attach(vertexAttachMethod);
        }

        // Add outgoing edges for object properties (if requested)
        if (attachEdgesOfThisDirection == Direction.BOTH || attachEdgesOfThisDirection == Direction.OUT) {
            Maps.transformValues(Maps.filterKeys(node, frame::isObjectPropertyKey),
                    Functions.compose(id -> starGraph.addVertex(T.id, URI.create(id.toString())), config.valueObjectMapper()::fromFieldValue))
                    .forEach((label, inVertex) -> {
                        StarEdge edge = (StarEdge) vertex.addEdge(label, inVertex);
                        if (edgeAttachMethod != null) {
                            edge.attach(edgeAttachMethod);
                        }
                    });
        }

        return vertex;
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
     * Creates and initializes a new context for reading BDIO data into the supplied graph.
     */
    private ReadGraphContext createReadGraphContext(Graph graphToWriteTo) {
        ReadGraphContext context;
        if (graphToWriteTo instanceof SqlgGraph) {
            context = new SqlgReadGraphContext(config, (SqlgGraph) graphToWriteTo, batchSize);
        } else {
            context = new ReadGraphContext(config, graphToWriteTo, batchSize);
        }
        context.initialize(frame);
        return context;
    }

    /**
     * Implementation of {@link #readVertex(InputStream, Function, Function, Direction)} that encapsulates result/errors
     * in a flowable. This is useful for flat mapping. Hint. Hint.
     */
    private Flowable<StarVertex> readVertex(Map<String, Object> node) {
        try {
            return Flowable.just((StarVertex) readVertex(NodeInputStream.wrapNode(node), null, null, Direction.OUT));
        } catch (IOException e) {
            return Flowable.error(e);
        }
    }

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements ReaderBuilder<BlackDuckIoReader> {

        private Optional<BlackDuckIoConfig> config = Optional.empty();

        // TODO Do we take the expansion context from the BdioDocument? How does this relate to the BdioFrame?
        private Map<String, Object> applicationContext = new LinkedHashMap<>();

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
