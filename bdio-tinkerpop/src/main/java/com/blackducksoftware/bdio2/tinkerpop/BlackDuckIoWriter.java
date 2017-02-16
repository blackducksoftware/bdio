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
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphWriter;
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

    private final BlackDuckIoConfig config;

    private BlackDuckIoWriter(Builder builder) {
        config = builder.config.orElseGet(() -> BlackDuckIoConfig.build().create());
    }

    @Override
    public void writeGraph(OutputStream outputStream, Graph graph) throws IOException {
        RxJavaBdioDocument document = config.newBdioDocument(RxJavaBdioDocument.class);
        WriteGraphContext context = config.newWriteContext(graph, null);

        // Create the writer with the parsed metadata
        document.writeToFile(readMetadata(context, document.jsonld().options()), outputStream);

        GraphTraversalSource g = context.traversal();

        // TODO Stay in the traversal longer to feed the node subscriber
        // TODO limit what g.V() traverses (since we don't want stuff like NamedGraph vertices)
        Flowable.<Vertex, Traversal<Vertex, Vertex>> generate(() -> g.V(), (traversal, emitter) -> {
            try {
                if (traversal.hasNext()) {
                    emitter.onNext(traversal.next());
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                emitter.onError(e);
            }
        }).map(vertex -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(JsonLdConsts.TYPE, vertex.label());
            vertex.properties().forEachRemaining(vp -> {
                if (vp.key().equals(config.identifierKey().orElse(null))) {
                    result.put(JsonLdConsts.ID, vp.value());
                } else if (vp.key().equals(config.unknownKey().orElse(null))) {
                    BdioHelper.restoreUnknownProperties(vp.value(), result::put);
                } else {
                    result.put(vp.key(), config.valueObjectMapper().toValueObject(vp.value()));
                }
            });

            // TODO Iterate the vertex outgoing edges

            return result;
        }).subscribe(document.asNodeSubscriber(BdioMetadata.createRandomUUID()));
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
        return config.metadataLabel()
                .flatMap(label -> context.traversal().V().hasLabel(label).tryNext())
                .map(vertex -> {
                    BdioMetadata metadata = new BdioMetadata();
                    config.identifierKey().ifPresent(key -> {
                        metadata.id(vertex.value(key));
                    });
                    try {
                        Object expandedMetadata = Iterables.getOnlyElement(JsonLdProcessor.expand(ElementHelper.propertyValueMap(vertex), options));
                        if (expandedMetadata instanceof Map<?, ?>) {
                            ((Map<?, ?>) expandedMetadata).forEach((key, value) -> {
                                if (key instanceof String) {
                                    metadata.put((String) key, value);
                                }
                            });
                        }
                    } catch (JsonLdError e) {

                    }
                    return metadata;
                })
                .orElse(BdioMetadata.createRandomUUID());
    }

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements WriterBuilder<BlackDuckIoWriter> {

        private Optional<BlackDuckIoConfig> config = Optional.empty();

        private Builder() {
        }

        public Builder config(@Nullable BlackDuckIoConfig config) {
            this.config = Optional.ofNullable(config);
            return this;
        }

        @Override
        public BlackDuckIoWriter create() {
            return new BlackDuckIoWriter(this);
        }
    }

}
