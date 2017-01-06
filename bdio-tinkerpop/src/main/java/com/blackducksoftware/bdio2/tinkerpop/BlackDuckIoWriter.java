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
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphWriter;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.utils.JsonUtils;

import io.reactivex.Flowable;

public class BlackDuckIoWriter implements GraphWriter {

    private final BdioDocument.Builder documentBuilder;

    private TraversalStrategy<?>[] strategies;

    private BlackDuckIoWriter(Builder builder) {
        documentBuilder = builder.documentBuilder.orElseGet(BdioDocument.Builder::new);
        strategies = builder.partitionStrategy.map(s -> new TraversalStrategy<?>[] { s }).orElse(new TraversalStrategy<?>[0]);
    }

    @Override
    public void writeGraph(OutputStream outputStream, Graph graph) throws IOException {
        GraphTraversalSource g = graph.traversal().withStrategies(strategies);

        BdioMetadata metadata = new BdioMetadata();
        // TODO Merge the metadata in from the NamedGraph vertex

        // TODO Do we need to generate a context like we do in reader (basically the inverse of the frame)?
        RxJavaBdioDocument document = documentBuilder
                .expandContext(null)
                .build(RxJavaBdioDocument.class)
                .writeToFile(metadata, outputStream);

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
                switch (vp.key()) {
                case Tokens.id:
                    result.put(JsonLdConsts.ID, vp.value());
                    break;
                case Tokens.unknown:
                    try {
                        Object unknown = JsonUtils.fromString((String) vp.value());
                        if (unknown instanceof Map<?, ?>) {
                            result.putAll((Map<String, ?>) unknown);
                        }
                    } catch (IOException e) {
                        // Ignore this...
                    }
                    break;
                default:
                    if (!isPrivate(vp.key())) {
                        // TODO Types?! Is this what the mapper would be for?
                        // TODO We might want something like "{ @value : Foo, @type : Bar }"
                        // That requires knowing the FQ name so we can get the data type from Bdio.DataProperty
                        // ...Unless we get an actual Java object (e.g. Integer) from the graph...
                        result.put(vp.key(), vp.value());
                    }
                }
            });

            // TODO Iterate the vertex outgoing edges

            return result;
        }).subscribe(document.asNodeSubscriber(metadata)); // TODO Technically metadata is ignored here...
    }

    private static boolean isPrivate(String key) {
        return key.startsWith("_");
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

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements WriterBuilder<BlackDuckIoWriter> {

        private Optional<BdioDocument.Builder> documentBuilder = Optional.empty();

        private Optional<PartitionStrategy> partitionStrategy = Optional.empty();

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

        @Override
        public BlackDuckIoWriter create() {
            return new BlackDuckIoWriter(this);
        }
    }

}
