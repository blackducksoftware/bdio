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

import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.hasLabel;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.hasNot;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.identity;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.not;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphWriter;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.google.common.collect.Iterables;

import io.reactivex.Flowable;

public class BlackDuckIoWriter implements GraphWriter {

    private final GraphMapper mapper;

    private BlackDuckIoWriter(Builder builder) {
        mapper = builder.mapper.orElseGet(() -> BlackDuckIoMapper.build().create()).createMapper();
    }

    @Override
    public void writeGraph(OutputStream outputStream, Graph graph) throws IOException {
        RxJavaBdioDocument document = mapper.newBdioDocument(RxJavaBdioDocument.class);
        WriteGraphContext context = new WriteGraphContext(graph, mapper);

        // Create the writer with the parsed metadata
        document.writeToFile(readMetadata(context, document.jsonld().options()), outputStream);

        // Construct a flowable using the vertex traversal as the iterator
        Flowable.fromIterable(() -> nodes(context.traversal()))
                .doOnTerminate(context::rollbackTx)
                .subscribe(document.asNodeSubscriber(BdioMetadata.createRandomUUID()));
    }

    @Override
    public void writeVertex(OutputStream outputStream, Vertex v, Direction direction) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeVertex(OutputStream outputStream, Vertex v) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeEdge(OutputStream outputStream, Edge e) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeVertexProperty(OutputStream outputStream, @SuppressWarnings("rawtypes") VertexProperty vp) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeProperty(OutputStream outputStream, @SuppressWarnings("rawtypes") Property p) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeObject(OutputStream outputStream, Object object) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * If a metadata label is configured, read the vertex from the graph into a new BDIO metadata instance.
     */
    private BdioMetadata readMetadata(WriteGraphContext context, JsonLdOptions options) {
        return mapper.metadataLabel()
                .flatMap(label -> context.traversal().V().hasLabel(label).tryNext())
                .map(vertex -> {
                    BdioMetadata metadata = new BdioMetadata();
                    mapper.identifierKey().ifPresent(key -> {
                        metadata.id(vertex.value(key));
                    });
                    try {
                        Object expandedMetadata = Iterables.getOnlyElement(JsonLdProcessor.expand(ElementHelper.propertyValueMap(vertex), options), null);
                        if (expandedMetadata instanceof Map<?, ?>) {
                            ((Map<?, ?>) expandedMetadata).forEach((key, value) -> {
                                if (key instanceof String) {
                                    metadata.put((String) key, value);
                                }
                            });
                        }
                    } catch (JsonLdError e) {
                        // TODO How should we handle this?
                        e.printStackTrace();
                    }
                    return metadata;
                })
                .orElse(BdioMetadata.createRandomUUID());
    }

    /**
     * Produces the identifier (the "@id" value) for a vertex based on the current configuration.
     */
    private String nodeId(Vertex vertex) {
        Object identifier = mapper.identifierKey()
                .map(key -> vertex.property(key))
                .orElse(VertexProperty.empty())
                .orElseGet(() -> vertex.id());
        return mapper.valueObjectMapper().toValueObject(identifier).toString();
    }

    /**
     * Returns an iterator (really a graph traversal) over the BDIO nodes to emit.
     */
    private Iterator<Map<String, Object>> nodes(GraphTraversalSource g) {
        return g.V()
                // Strip out the metadata vertex
                .where(mapper.metadataLabel().map(label -> not(hasLabel(label)))
                        .orElse(identity()))

                // Strip out the implicit vertices
                .where(mapper.implicitKey().map(propertyKey -> hasNot(propertyKey))
                        .orElse(identity()))

                // TODO This is a big ass lambda step, can we do more with the traversal?
                .map(t -> {
                    Vertex vertex = t.get();

                    // Construct a map to represent the BDIO version of the vertex
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put(JsonLdConsts.TYPE, vertex.label());
                    result.put(JsonLdConsts.ID, nodeId(vertex));

                    // Handle vertex properties
                    vertex.properties().forEachRemaining(vp -> {
                        if (vp.key().equals(mapper.unknownKey().orElse(null))) {
                            mapper.restoreUnknownProperties(vp.value(), result::put);
                        } else {
                            result.put(vp.key(), mapper.valueObjectMapper().toValueObject(vp.value()));
                        }
                    });

                    // Handle vertex edges
                    vertex.edges(Direction.OUT).forEachRemaining(e -> {
                        Set<String> keys = e.keys();
                        if (keys.contains(mapper.implicitKey().orElse(null))) {
                            return;
                        } else {
                            // TODO Edge properties?
                            result.put(e.label(), mapper.valueObjectMapper().toReferenceValueObject(nodeId(e.inVertex())));
                        }
                    });

                    // If the identifier key was used, we already stored it as the "@id" value
                    mapper.identifierKey().ifPresent(result::remove);

                    return result;
                });
    }

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements WriterBuilder<BlackDuckIoWriter> {

        private Optional<Mapper<GraphMapper>> mapper = Optional.empty();

        private Builder() {
        }

        public Builder mapper(@Nullable Mapper<GraphMapper> mapper) {
            this.mapper = Optional.ofNullable(mapper);
            return this;
        }

        @Override
        public BlackDuckIoWriter create() {
            return new BlackDuckIoWriter(this);
        }
    }

}
