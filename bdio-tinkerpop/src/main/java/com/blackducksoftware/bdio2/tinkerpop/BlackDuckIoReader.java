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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphReader;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph;
import org.umlg.sqlg.structure.SqlgExceptions.InvalidIdException;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.base.Functions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;

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
                if (graph instanceof SqlgGraph) {
                    ((SqlgGraph) graph).tx().normalBatchModeOn();
                }
            }
        }
    }

    /**
     * Builder for creating BDIO documents.
     */
    private final BdioDocument.Builder documentBuilder;

    /**
     * The JSON-LD frame used to convert from BDIO to vertex data.
     */
    private final BdioFrame frame;

    /**
     * The JSON-LD value object mapper to use.
     */
    private final ValueObjectMapper valueObjectMapper;

    /**
     * The number of graph mutations before a commit is attempted.
     */
    private final int batchSize;

    /**
     * The optional partitioning strategy, if present all imported vertices will have the appropriate partition data.
     */
    @Nullable
    private final PartitionStrategy partitionStrategy;

    private BlackDuckIoReader(Builder builder) {
        documentBuilder = builder.documentBuilder.orElseGet(BdioDocument.Builder::new);
        frame = BdioFrame.create(builder.applicationContext);
        valueObjectMapper = builder.mapper.orElseGet(() -> BlackDuckIoMapper.build().create()).createMapper();
        batchSize = builder.batchSize;
        partitionStrategy = builder.partitionStrategy.orElse(null);
    }

    @Override
    public void readGraph(InputStream inputStream, Graph graphToWriteTo) throws IOException {
        RxJavaBdioDocument document = documentBuilder.build(RxJavaBdioDocument.class);

        // Create a metadata subscription
        document.metadata(metadata -> {
            GraphTraversalSource g = traversal(graphToWriteTo);
            Vertex namedGraph = g.V().hasLabel(Tokens.NamedGraph).tryNext().orElseGet(() -> g.addV(Tokens.NamedGraph).next());

            namedGraph.property(Tokens.id, metadata.id());
            try {
                // Compact the metadata using the context extracted from frame
                Map<String, Object> compactMetadata = JsonLdProcessor.compact(metadata, frame, document.jsonld().options());
                ElementHelper.attachProperties(namedGraph, getNodeProperties(compactMetadata, false));
            } catch (JsonLdError e) {
                // TODO What can we do about this?
                e.printStackTrace();
            }
        });

        // Get the sequence of BDIO graph nodes and process them according the graph type
        MutationCountCommitter batchCommit = new MutationCountCommitter(graphToWriteTo, batchSize);
        Graph.Features.EdgeFeatures edgeFeatures = graphToWriteTo.features().edge();
        Predicate<String> uniqueIdentifiers;
        if (graphToWriteTo instanceof SqlgGraph) {
            // Use a bloom filter to avoid querying the database unnecessarily (this is a quite large bloom filter)
            uniqueIdentifiers = BloomFilter.create(Funnels.unencodedCharsFunnel(), 10_000_000)::put;

            // Pre-create and index a few import columns in the database
            SqlgGraph sqlgGraph = (SqlgGraph) graphToWriteTo;
            frame.forEachTypeName(label -> {
                sqlgGraph.createVertexLabeledIndex(label, Tokens.id, "http://example.com/1");
            });

            // Commit changes and enable normal batch mode
            sqlgGraph.tx().commit();
            sqlgGraph.tx().normalBatchModeOn();
        } else {
            uniqueIdentifiers = x -> false;
        }

        document.jsonld()

                // Frame the JSON-LD and strip off metadata
                .frame(frame)
                .compose(document.withoutMetadata())

                // Convert nodes to vertices
                .map(NodeInputStream::wrapNode)
                .flatMap(this::readVertex)
                .cast(StarGraph.StarVertex.class)

                // TODO This doesn't seem like the right way to do this...
                .doOnNext(batchCommit)

                // Collect all of the vertices in a map, creating the actual vertices in the graph as we go
                .toMap(vertex -> vertex, vertex -> vertex.attach(upsert(graphToWriteTo, uniqueIdentifiers)))

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
        final Map<String, Object> node = NodeInputStream.readNode(inputStream);
        final StarGraph starGraph = StarGraph.open();

        // Use a URI object as graph identifier keeping in mind that the graph is not obligated
        // to use the URI (which is why we still keep the string version around)
        Vertex vertex = starGraph.addVertex(getNodeProperties(node, true));

        // Notify if requested
        if (vertexAttachMethod != null) {
            // Strange consumer cast is necessary to avoid warning about an unused return value from a function
            ((java.util.function.Consumer<Attachable<Vertex>>) vertexAttachMethod::apply).accept(starGraph.getStarVertex());
        }

        // Object properties (BDIO only contains outgoing edges)
        if (attachEdgesOfThisDirection == Direction.BOTH || attachEdgesOfThisDirection == Direction.OUT) {
            addVertexEdges(node, starGraph.getStarVertex(),
                    id -> starGraph.addVertex(T.id, URI.create(id.toString())),
                    edgeAttachMethod != null ? edgeAttachMethod::apply : edge -> {
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
     * Returns key/value pairs for the data properties of the specified BDIO node.
     */
    private Object[] getNodeProperties(Map<String, Object> node, boolean includeSpecial) {
        Stream.Builder<Map.Entry<?, ?>> properties = Stream.builder();

        // Special properties that can be optionally included
        if (includeSpecial) {
            Optional.ofNullable(node.get(JsonLdConsts.ID)).map(id -> URI.create((String) id))
                    .map(id -> Maps.immutableEntry(T.id, id))
                    .ifPresent(properties);

            Optional.ofNullable(node.get(JsonLdConsts.TYPE))
                    .map(label -> Maps.immutableEntry(T.label, label))
                    .ifPresent(properties);

            Optional.ofNullable(partitionStrategy)
                    .map(s -> Maps.immutableEntry(s.getPartitionKey(), s.getWritePartition()))
                    .ifPresent(properties);

            Optional.ofNullable(node.get(JsonLdConsts.ID))
                    .map(id -> Maps.immutableEntry(Tokens.id, id))
                    .ifPresent(properties);
        }

        // Sorted data properties
        // TODO Do we need a sort order that is stable across BDIO versions?
        node.entrySet().stream()
                .filter(e -> dataPropertyNames.contains(e.getKey()))
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEachOrdered(properties);

        // Unknown properties
        Optional.of(Maps.filterKeys(node, BlackDuckIoReader::isUnknownKey))
                .filter(m -> !m.isEmpty())
                .map(m -> {
                    try {
                        return JsonUtils.toString(m);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .map(json -> Maps.immutableEntry(Tokens.unknown, json))
                .ifPresent(properties);

        // Convert the whole thing into an array
        return properties.build()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                .toArray();
    }

    /**
     * Given a map representing a framed BDIO node, this method adds the appropriate edges to the supplied vertex.
     */
    private void addVertexEdges(Map<String, Object> values, Vertex vertex,
            com.google.common.base.Function<Object, Vertex> vertexFactory,
            java.util.function.Consumer<Attachable<Edge>> edgeAttachMethod) {
        // Create a map view of property names to the incoming vertex
        Map<String, Vertex> objectProperties = Maps.transformValues(
                Maps.filterKeys(values, frame::isObjectPropertyKey),
                Functions.compose(vertexFactory, valueObjectMapper::fromFieldValue));

        // Add edges for each mapping
        objectProperties.forEach((key, inVertex) -> {
            edgeAttachMethod.accept((Attachable<Edge>) vertex.addEdge(key, inVertex));
        });
    }

    /**
     * An attachable method that automatically performs an "upsert" operation; that is it incurs the existence check and
     * updates vertex if it exists.
     */
    private Function<Attachable<Vertex>, Vertex> upsert(Graph hostGraph, Predicate<String> uniqueIdentifiers) {
        return attachableVertex -> {
            Vertex baseVertex = attachableVertex.get();
            return Optional.ofNullable(baseVertex.id())
                    // If this a unique identifier, don't bother trying to look it up
                    .filter(id -> !uniqueIdentifiers.test(id.toString()))
                    .flatMap(id -> {
                        try {
                            return Optional.ofNullable(Iterators.getNext(hostGraph.vertices(id), null));
                        } catch (InvalidIdException e) {
                            return traversal(hostGraph).V().has(Tokens.id, id.toString()).tryNext();
                        }
                    })

                    // If we still have a vertex, update all of the properties
                    .map(vertex -> {
                        baseVertex.properties().forEachRemaining(vp -> {
                            VertexProperty<?> vertexProperty = hostGraph.features().vertex().properties().willAllowId(vp.id())
                                    ? vertex.property(hostGraph.features().vertex().getCardinality(vp.key()), vp.key(), vp.value(), T.id, vp.id())
                                    : vertex.property(hostGraph.features().vertex().getCardinality(vp.key()), vp.key(), vp.value());
                            vp.properties().forEachRemaining(p -> vertexProperty.property(p.key(), p.value()));
                        });
                        return vertex;
                    })

                    // If we do not have a vertex, create it
                    .orElseGet(() -> {
                        boolean includeId = hostGraph.features().vertex().willAllowId(baseVertex.id());
                        return hostGraph.addVertex(ElementHelper.getProperties(baseVertex, includeId, true, Collections.emptySet()));
                    });
        };
    }

    /**
     * Implementation of {@link #readVertex(InputStream, Function, Function, Direction)} that encapsulates result/errors
     * in a flowable. This is useful for flat mapping. Hint. Hint.
     */
    private Flowable<Vertex> readVertex(InputStream in) {
        try {
            return Flowable.just(readVertex(in, null, null, Direction.OUT));
        } catch (IOException e) {
            return Flowable.error(e);
        }
    }

    /**
     * Returns a traversal source applying the optional partitioning strategy.
     */
    private GraphTraversalSource traversal(Graph graph) {
        return partitionStrategy != null ? graph.traversal().withStrategies(partitionStrategy) : graph.traversal();
    }

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements ReaderBuilder<BlackDuckIoReader> {

        private Optional<Mapper<ValueObjectMapper>> mapper = Optional.empty();

        private Optional<BdioDocument.Builder> documentBuilder = Optional.empty();

        private Optional<PartitionStrategy> partitionStrategy = Optional.empty();

        // TODO Do we take the expansion context from the BdioDocument?
        private Map<String, Object> applicationContext = new LinkedHashMap<>();

        private int batchSize = 10000;

        private Builder() {
        }

        public Builder mapper(@Nullable Mapper<ValueObjectMapper> mapper) {
            this.mapper = Optional.ofNullable(mapper);
            return this;
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
    private static boolean isUnknownKey(String key) {
        // If framing did not recognize the attribute, it will still have a scheme or prefix separator
        return key.indexOf(':') >= 0;
    }

}
