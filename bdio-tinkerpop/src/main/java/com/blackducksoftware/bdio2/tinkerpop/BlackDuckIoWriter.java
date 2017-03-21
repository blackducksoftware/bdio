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
        return config.metadataLabel()
                .flatMap(label -> context.traversal().V().hasLabel(label).tryNext())
                .map(vertex -> {
                    BdioMetadata metadata = new BdioMetadata();
                    config.identifierKey().ifPresent(key -> {
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
                    }
                    return metadata;
                })
                .orElse(BdioMetadata.createRandomUUID());
    }

    private Object nodeType(Vertex vertex) {
        return vertex.label();
    }

    private Object nodeId(Vertex vertex) {
        return config.valueObjectMapper().toValueObject(config.identifierKey()
                .map(key -> vertex.<Object> property(key))
                .orElse(VertexProperty.empty())
                .orElseGet(() -> vertex.id()))
                .toString();
    }

    /**
     * Returns an iterator (really a graph traversal) over the BDIO nodes to emit.
     */
    private Iterator<Map<String, Object>> nodes(GraphTraversalSource g) {
        return g.V()
                // Strip out the metadata vertex
                .where(config.metadataLabel().map(label -> not(hasLabel(label)))
                        .orElse(identity()))

                // Strip out the implicit vertices
                .where(config.implicitKey().map(propertyKey -> hasNot(propertyKey))
                        .orElse(identity()))

                // TODO This is a big ass lambda step, can we do more with the traversal?
                .map(t -> {
                    Vertex vertex = t.get();

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put(JsonLdConsts.TYPE, nodeType(vertex));
                    result.put(JsonLdConsts.ID, nodeId(vertex));

                    vertex.properties().forEachRemaining(vp -> {
                        if (vp.key().equals(config.unknownKey().orElse(null))) {
                            BdioHelper.restoreUnknownProperties(vp.value(), result::put);
                        } else {
                            result.put(vp.key(), config.valueObjectMapper().toValueObject(vp.value()));
                        }
                    });

                    vertex.edges(Direction.OUT).forEachRemaining(e -> {
                        Set<String> keys = e.keys();
                        if (keys.contains(config.implicitKey().orElse(null))) {
                            return;
                        } else if (keys.isEmpty()) {
                            // TODO Edge properties? Is this just the behavior when there are no properties?
                            result.put(e.label(), config.valueObjectMapper().toReferenceValueObject(nodeId(e.inVertex())));
                        }
                    });

                    config.identifierKey().ifPresent(result::remove);
                    return result;
                });
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
