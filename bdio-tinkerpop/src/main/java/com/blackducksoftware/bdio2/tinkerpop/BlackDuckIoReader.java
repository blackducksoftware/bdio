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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality;
import org.apache.tinkerpop.gremlin.structure.io.GraphReader;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public final class BlackDuckIoReader implements GraphReader {

    /**
     * An action that counts each object passed in, periodically committing the graph.
     */
    private static class MutationCountCommitter implements Consumer<Object>, Action {

        /**
         * The graph to commit. Left as {@code null} if the original graph did not support transactions.
         */
        @Nullable
        private final Graph graph;

        /**
         * The number of mutations between commits.
         */
        private final int batchSize;

        /**
         * The number of observed mutations; incremented on each call to {@link #accept(Object)}.
         */
        private final AtomicLong count = new AtomicLong();

        private MutationCountCommitter(Graph graph, int batchSize) {
            this.graph = graph.features().graph().supportsTransactions() ? graph : null;
            this.batchSize = batchSize;
        }

        @Override
        public void run() {
            if (graph != null) {
                graph.tx().commit();
            }
        }

        @Override
        public void accept(Object t) {
            if (graph != null && count.incrementAndGet() % batchSize == 0) {
                graph.tx().commit();
            }
        }
    }

    /**
     * A key used to aggregate edges by label. This is used to batch load into Sqlg backed graphs.
     */
    private static class EdgeLabels {

        private static final Pair<String, String> ID_FIELDS = Pair.of(Tokens.id, Tokens.id);

        private final String inVertexLabel;

        private final String outVertexLabel;

        private final String edgeLabel;

        public EdgeLabels(String inVertexLabel, String outVertexLabel, String edgeLabel) {
            this.inVertexLabel = inVertexLabel;
            this.outVertexLabel = outVertexLabel;
            this.edgeLabel = edgeLabel;
        }

        public static EdgeLabels fromEdge(Edge edge) {
            return new EdgeLabels(edge.inVertex().label(), edge.outVertex().label(), edge.label());
        }

        public static Pair<String, String> vertexIdentifiers(Edge edge) {
            return Pair.of(
                    (String) edge.inVertex().property(ID_FIELDS.getLeft()).value(),
                    (String) edge.outVertex().property(ID_FIELDS.getRight()).value());
        }

        public void bulkAdd(SqlgGraph graphToWriteTo, Collection<Pair<String, String>> uids) {
            // TODO Remove cast when fixed in Sqlg
            graphToWriteTo.bulkAddEdges(inVertexLabel, outVertexLabel, edgeLabel, ID_FIELDS, (List<Pair<String, String>>) uids);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inVertexLabel, outVertexLabel, edgeLabel);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof EdgeLabels) {
                EdgeLabels other = (EdgeLabels) obj;
                return inVertexLabel.equals(other.inVertexLabel)
                        && outVertexLabel.equals(other.outVertexLabel)
                        && edgeLabel.equals(other.edgeLabel);
            } else {
                return false;
            }
        }
    }

    /**
     * The BDIO document builder representing the BDIO configuration.
     */
    private final BdioDocument.Builder documentBuilder;

    /**
     * The JSON-LD value object mapper to use.
     */
    private final ValueObjectMapper valueObjectMapper = new ValueObjectMapper();

    /**
     * The JSON-LD frame used to convert linked data into graph nodes.
     */
    private final Map<String, Object> frame;

    /**
     * The number of graph mutations before a commit is attempted.
     */
    private final int batchSize;

    /**
     * The optional partitioning strategy, if present all imported vertices will have the appropriate partition data.
     */
    @Nullable
    private final PartitionStrategy partitionStrategy;

    /**
     * The list of known data property names.
     */
    private final Set<String> dataPropertyNames;

    /**
     * The list of known object property names.
     */
    private final Set<String> objectPropertyNames;

    private BlackDuckIoReader(Builder builder) {
        documentBuilder = builder.documentBuilder.orElseGet(BdioDocument.Builder::new);
        batchSize = builder.batchSize;
        partitionStrategy = builder.partitionStrategy.orElse(null);

        Map<String, Object> frame = new LinkedHashMap<>();
        Set<String> dataPropertyNames = new LinkedHashSet<>();
        Set<String> objectPropertyNames = new LinkedHashSet<>();

        poplateContext(builder.applicationContext, frame, dataPropertyNames, objectPropertyNames);

        this.frame = frame;
        this.dataPropertyNames = ImmutableSet.copyOf(dataPropertyNames);
        this.objectPropertyNames = ImmutableSet.copyOf(objectPropertyNames);
    }

    /**
     * Generates the frame and collections of graph property names.
     */
    private static void poplateContext(Map<String, Object> initialContext,
            Map<String, Object> frame, Set<String> dataPropertyNames, Set<String> objectPropertyNames) {
        Map<String, Object> context = new LinkedHashMap<>();
        List<String> type = new ArrayList<>();

        // Application specific entries to the context
        for (Map.Entry<String, Object> entry : initialContext.entrySet()) {
            if (entry.getValue() instanceof String) {
                context.put(entry.getKey(), entry.getValue());
            } else if (entry.getValue() instanceof Map<?, ?>) {
                Map<?, ?> definition = (Map<?, ?>) entry.getValue();
                Object id = definition.get(JsonLdConsts.ID);
                if (id != null) {
                    context.put(entry.getKey(), id);
                }
                if (Objects.equals(definition.get(JsonLdConsts.TYPE), JsonLdConsts.ID)) {
                    objectPropertyNames.add(entry.getKey());
                } else {
                    dataPropertyNames.add(entry.getKey());
                }
            }
        }

        // Standard BDIO
        for (Bdio.Class bdioClass : Bdio.Class.values()) {
            context.put(bdioClass.name(), bdioClass.toString());
            type.add(bdioClass.toString());
        }
        for (Bdio.DataProperty bdioDataProperty : Bdio.DataProperty.values()) {
            context.put(bdioDataProperty.name(), bdioDataProperty.toString());
            dataPropertyNames.add(bdioDataProperty.name());
        }
        for (Bdio.ObjectProperty bdioObjectProperty : Bdio.ObjectProperty.values()) {
            context.put(bdioObjectProperty.name(), bdioObjectProperty.toString());
            objectPropertyNames.add(bdioObjectProperty.name());
        }

        frame.put(JsonLdConsts.CONTEXT, context);
        frame.put(JsonLdConsts.TYPE, type);
    }

    @Override
    public void readGraph(InputStream inputStream, Graph graphToWriteTo) throws IOException {
        RxJavaBdioDocument document = documentBuilder.build(RxJavaBdioDocument.class);
        GraphTraversalSource g = partitionStrategy != null ? graphToWriteTo.traversal().withStrategies(partitionStrategy) : graphToWriteTo.traversal();

        // Create a metadata subscription
        document.metadata(metadata -> {
            Vertex namedGraph = g.V().hasLabel(Tokens.NamedGraph).tryNext().orElseGet(() -> g.addV(Tokens.NamedGraph).next());

            namedGraph.property(Tokens.id, metadata.id());
            try {
                // Compact the metadata using the context extracted from frame
                Object context = frame.get(JsonLdConsts.CONTEXT);
                Map<String, Object> compactMetadata = JsonLdProcessor.compact(metadata, context, document.jsonld().options());
                addVertexProperties(compactMetadata, namedGraph);
            } catch (JsonLdError e) {
                // TODO What can we do about this?
                e.printStackTrace();
            }
        });

        // Get the sequence of BDIO graph nodes and process them according the graph type
        Flowable<Map<String, Object>> graphNodes = document.jsonld().frame(frame).compose(document.withoutMetadata());
        if (graphToWriteTo instanceof SqlgGraph) {
            readSqlgGraph(graphNodes, (SqlgGraph) graphToWriteTo);
        } else {
            readOtherGraph(graphNodes, graphToWriteTo);
        }

        // Read the supplied input stream
        document.read(inputStream);

        // TODO Error handling? Get the throwable off the processor?
    }

    private void readSqlgGraph(Flowable<Map<String, Object>> graphNodes, SqlgGraph graphToWriteTo) {
        graphNodes.groupBy(node -> node.get(JsonLdConsts.TYPE))
                .flatMap(nodes -> {
                    return nodes
                            .doOnSubscribe(sub -> {
                                System.err.println("STARTING STREAMING ON " + Thread.currentThread().getName());
                                graphToWriteTo.tx().streamingBatchModeOn();
                            })
                            .map(NodeInputStream::wrapNode)
                            .flatMap(in -> {
                                try {
                                    return Flowable.just(readVertex(in, null, null, Direction.OUT));
                                } catch (IOException e) {
                                    return Flowable.error(e);
                                }
                            })
                            .map(vertex -> {
                                // TODO Need to pass the StarVertex into streamVertex
                                // graphToWriteTo.streamVertex();
                                return vertex;
                            })
                            .doOnComplete(() -> {
                                System.err.println("COMMITING ON " + Thread.currentThread().getName());
                                graphToWriteTo.tx().commit();
                            })
                            .subscribeOn(Schedulers.newThread());
                })
                .doOnSubscribe(sub -> {
                    System.err.println("STARTING STREAMING EDGES ON " + Thread.currentThread().getName());
                    graphToWriteTo.tx().streamingBatchModeOn();
                })
                .flatMapIterable(vertex -> (Iterable<Edge>) (() -> vertex.edges(Direction.OUT)))
                .toMultimap(EdgeLabels::fromEdge, EdgeLabels::vertexIdentifiers)
                .flatMapObservable(multimap -> Observable.fromIterable(multimap.entrySet()))
                .doOnComplete(() -> {
                    System.err.println("FINAL COMMIT ON " + Thread.currentThread().getName());
                    graphToWriteTo.tx().commit();
                })
                // TODO Edge properties?
                .subscribe(edge -> edge.getKey().bulkAdd(graphToWriteTo, edge.getValue()));
    }

    private void readOtherGraph(Flowable<Map<String, Object>> graphNodes, Graph graphToWriteTo) {
        MutationCountCommitter batchCommit = new MutationCountCommitter(graphToWriteTo, batchSize);
        Graph.Features.EdgeFeatures edgeFeatures = graphToWriteTo.features().edge();

        graphNodes.map(NodeInputStream::wrapNode)

                // Convert to vertices
                .flatMap(in -> {
                    try {
                        return Flowable.just(readVertex(in, null, null, Direction.OUT));
                    } catch (IOException e) {
                        return Flowable.error(e);
                    }
                })
                .cast(StarGraph.StarVertex.class)

                // TODO This doesn't seem like the right way to do this...
                .doOnNext(batchCommit)

                // Collect all of the vertices in a map, creating the actual vertices in the graph as we go
                .toMap(vertex -> (StarGraph.StarVertex) ((Attachable<Vertex>) vertex).get(),
                        vertex -> ((Attachable<Vertex>) vertex).attach(upsert(graphToWriteTo)))

                // Get all of the outgoing edges from the cached vertices
                .flatMapObservable(cache -> Observable.fromIterable(cache.keySet())
                        .flatMapIterable(kv -> (Iterable<Edge>) (() -> kv.edges(Direction.OUT)))

                        // Connect the edges
                        .map(e -> {
                            final Vertex cachedOutV = cache.get(e.outVertex());
                            final Vertex cachedInV = cache.get(e.inVertex());
                            final Edge newEdge = edgeFeatures.willAllowId(e.id())
                                    ? cachedOutV.addEdge(e.label(), cachedInV, T.id, e.id())
                                    : cachedOutV.addEdge(e.label(), cachedInV);

                            e.properties().forEachRemaining(p -> newEdge.property(p.key(), p.value()));

                            return newEdge;
                        }))

                // TODO This doesn't seem like the right way to do this...
                .doOnNext(batchCommit)
                .doOnComplete(batchCommit)

                .subscribe();
    }

    @Override
    public Vertex readVertex(InputStream inputStream, Function<Attachable<Vertex>, Vertex> vertexAttachMethod) throws IOException {
        return readVertex(inputStream, vertexAttachMethod, null, null);
    }

    @Override
    public Vertex readVertex(InputStream inputStream, Function<Attachable<Vertex>, Vertex> vertexAttachMethod,
            Function<Attachable<Edge>, Edge> edgeAttachMethod, Direction attachEdgesOfThisDirection) throws IOException {
        final Map<String, Object> node = NodeInputStream.readNode(inputStream);
        final StarGraph starGraph = StarGraph.open();

        // Use a URI object as graph identifier keeping in mind that the graph is not obligated
        // to use the URI (which is why we still keep the string version around)
        Vertex vertex = starGraph.addVertex(
                T.label, node.get(JsonLdConsts.TYPE),
                T.id, URI.create((String) node.get(JsonLdConsts.ID)),
                Tokens.id, node.get(JsonLdConsts.ID));

        // We are not using a traversal strategy so manually add the partition value if available
        if (partitionStrategy != null) {
            vertex.property(Cardinality.set, partitionStrategy.getPartitionKey(), partitionStrategy.getWritePartition());
        }

        // Add the vertex properties and notify if requested
        addVertexProperties(node, vertex);
        if (vertexAttachMethod != null) {
            ((java.util.function.Consumer<Attachable<Vertex>>) vertexAttachMethod::apply).accept(starGraph.getStarVertex());
        }

        // Object properties (BDIO only contains outgoing edges)
        if (attachEdgesOfThisDirection == Direction.BOTH || attachEdgesOfThisDirection == Direction.OUT) {
            addVertexEdges(node, starGraph.getStarVertex(),
                    id -> starGraph.addVertex(T.id, URI.create(id.toString()), Tokens.id, id),
                    edgeAttachMethod != null ? edgeAttachMethod::apply : edge -> {
                    });
        }

        return vertex;
    }

    /**
     * Given a map representing a framed BDIO node, this method adds the appropriate properties to the supplied vertex.
     */
    private void addVertexProperties(Map<String, Object> values, Vertex vertex) {
        // Partition the framed node
        Map<String, Object> dataProperties = Maps.filterKeys(values, dataPropertyNames::contains);
        Map<String, Object> unknown = Maps.filterKeys(values, BlackDuckIoReader::isUnknown);

        // Persist the properties
        Maps.transformValues(dataProperties, valueObjectMapper::fromFieldValue).forEach(vertex::property);
        if (!unknown.isEmpty()) {
            try {
                vertex.property(Tokens.unknown, JsonUtils.toString(unknown));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Given a map representing a framed BDIO node, this method adds the appropriate edges to the supplied vertex.
     */
    private void addVertexEdges(Map<String, Object> values, Vertex vertex,
            com.google.common.base.Function<Object, Vertex> vertexFactory,
            java.util.function.Consumer<Attachable<Edge>> edgeAttachMethod) {
        // Partition the framed node
        Map<String, Object> objectProperites = Maps.filterKeys(values, objectPropertyNames::contains);

        // Persist the edges
        Maps.transformValues(objectProperites, Functions.compose(vertexFactory, valueObjectMapper::fromFieldValue))
                .forEach((key, inVertex) -> {
                    edgeAttachMethod.accept((Attachable<Edge>) vertex.addEdge(key, inVertex));
                });
    }

    /**
     * An attachable method that automatically performs an "upsert" operation; that is it incurs the existence check and
     * updates vertex if it exists.
     */
    private Function<Attachable<Vertex>, Vertex> upsert(Graph hostGraph) {
        // `Attachable.Method.getOrCreate` doesn't update
        return attachableVertex -> {
            return getVertex(attachableVertex, hostGraph)
                    .map(vertex -> {
                        attachableVertex.get().properties().forEachRemaining(vp -> {
                            VertexProperty<?> vertexProperty = hostGraph.features().vertex().properties().willAllowId(vp.id())
                                    ? vertex.property(hostGraph.features().vertex().getCardinality(vp.key()), vp.key(), vp.value(), T.id, vp.id())
                                    : vertex.property(hostGraph.features().vertex().getCardinality(vp.key()), vp.key(), vp.value());
                            vp.properties().forEachRemaining(p -> vertexProperty.property(p.key(), p.value()));
                        });
                        return vertex;
                    })
                    .orElseGet(() -> Attachable.Method.createVertex(attachableVertex, hostGraph));
        };
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

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements ReaderBuilder<BlackDuckIoReader> {

        private Optional<BdioDocument.Builder> documentBuilder = Optional.empty();

        private Optional<PartitionStrategy> partitionStrategy = Optional.empty();

        // TODO Do we take the expansion context from the BdioDocument?
        private Map<String, Object> applicationContext = new LinkedHashMap<>();

        private int batchSize = 10000;

        private Builder() {
        }

        public Builder documentBuilder(@Nullable BdioDocument.Builder documentBuilder) {
            this.documentBuilder = Optional.ofNullable(documentBuilder);
            return this;
        }

        public Builder partitionStrategy(@Nullable PartitionStrategy partitionStrategy) {
            this.partitionStrategy = Optional.ofNullable(partitionStrategy);
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

    /**
     * Check if a key represents an unknown property.
     */
    private static boolean isUnknown(String key) {
        // If framing did not recognize the attribute, it will still have a scheme or prefix separator
        return key.indexOf(':') >= 0;
    }

    private static Optional<Vertex> getVertex(Attachable<Vertex> attachableVertex, Graph hostGraph) {
        // Add some extra protection to `Attachable.Method.getVertex`
        try {
            Iterator<Vertex> vertexIterator = hostGraph.vertices(attachableVertex.get().id());
            return vertexIterator.hasNext() ? Optional.of(vertexIterator.next()) : Optional.empty();
        } catch (InvalidIdException e) {
            // It is possible that we do not have a Sqlg assigned identifier yet, just return empty
            return Optional.empty();
        }
    }
}
