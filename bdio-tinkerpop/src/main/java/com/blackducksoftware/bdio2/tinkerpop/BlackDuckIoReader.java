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

import static com.google.common.base.Throwables.propagateIfPossible;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
import com.blackducksoftware.bdio2.tinkerpop.GraphContextFactory.AbstractContextBuilder;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;

import io.reactivex.plugins.RxJavaPlugins;

public final class BlackDuckIoReader implements GraphReader {

    private final GraphContextFactory contextFactory;

    private BlackDuckIoReader(Builder builder) {
        contextFactory = builder.contextFactory();
    }

    @SuppressWarnings("CheckReturnValue")
    @Override
    public void readGraph(InputStream inputStream, Graph graph) throws IOException {
        ReadGraphContext context = contextFactory.forBdioReadingInto(graph);
        RxJavaBdioDocument document = context.mapper().newBdioDocument(RxJavaBdioDocument.class);

        // Create a metadata subscription
        document.metadata(metadata -> createMetadata(metadata, context, document.jsonld().options()));

        // Get the sequence of BDIO graph nodes and transform them in vertices and edges
        document.jsonld().frame(context.mapper().frame()).compose(document.withoutMetadata())

                // Convert nodes to vertices
                .map(node -> createVertex(node, null, null, Direction.OUT, context))

                // Collect all of the vertices in a map, creating the actual vertices in the graph as we go
                // THIS IS THE PART WHERE WE READ THE WHOLE GRAPH INTO MEMORY SO WE CAN CREATE THE EDGES
                .toMap(vertex -> vertex, vertex -> vertex.attach(context::upsert), BlackDuckIoReader::vertexMap)

                // Create all the edges
                .flatMapObservable(context::createEdges)

                // Setup batch commits
                .doOnSubscribe(x -> context.startBatchTx())
                .doOnNext(x -> context.batchCommitTx())
                .doOnComplete(context::commitTx)

                // Ignore values, send errors directly to the default handler (nothing we can do with them)
                .subscribe(ignored -> {}, RxJavaPlugins::onError);

        // Read the supplied input stream
        document.read(inputStream);

        // If reading BDIO encountered a problem (e.g. an IOException), the error will be on the processor
        propagateIfPossible(document.processor().getThrowable(), IOException.class);
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
    private StarVertex createVertex(Map<String, Object> node,
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
                if (context.mapper().isObjectPropertyKey(property.getKey())) {
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

    /**
     * If a metadata label is configured, store the supplied BDIO metadata on a vertex in the graph.
     */
    private void createMetadata(BdioMetadata metadata, ReadGraphContext context, JsonLdOptions options) {
        context.mapper().metadataLabel().ifPresent(metadataLabel -> {
            GraphTraversalSource g = context.traversal();

            // Find or create the one vertex with the metadata label
            Vertex vertex = g.V().hasLabel(metadataLabel).tryNext()
                    .orElseGet(() -> g.addV(metadataLabel).next());

            // Preserve the identifier (if configured)
            context.mapper().identifierKey().ifPresent(key -> vertex.property(key, metadata.id()));

            try {
                // Compact the metadata using the context extracted from frame
                Map<String, Object> compactMetadata = JsonLdProcessor.compact(metadata, context.mapper().frame(), options);
                ElementHelper.attachProperties(vertex, context.getNodeProperties(compactMetadata, false));
            } catch (JsonLdError e) {
                // If we wrapped this and re-threw it, it would go back to the document's metadata single which is
                // subscribed to without an error handler, subsequently it would get wrapped in a
                // OnErrorNotImplementedException and passed to `RxJavaPlugins.onError`. So. Just call it directly.
                RxJavaPlugins.onError(e);
            }
        });
    }

    /**
     * Mutable map keys with equality defined such that state gets lost. What joy.
     */
    private static Map<StarVertex, Vertex> vertexMap() {
        return new HashMap<StarVertex, Vertex>() {
            @Override
            public Vertex put(StarVertex key, Vertex value) {
                Vertex oldValue = super.put(key, value);
                if (oldValue != null) {
                    // If we re-used the old key, we may need to go back and add edges from the new key
                    Iterator<Edge> edges = key.edges(Direction.OUT);
                    if (edges.hasNext()) {
                        // Worst case scenario. We need to get the old key, which means a linear search...
                        for (StarVertex oldKey : keySet()) {
                            if (oldKey.equals(key)) {
                                edges.forEachRemaining(e -> {
                                    oldKey.addEdge(e.label(), e.inVertex());
                                });
                                break;
                            }
                        }
                    }
                }
                return oldValue;
            }
        };
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
