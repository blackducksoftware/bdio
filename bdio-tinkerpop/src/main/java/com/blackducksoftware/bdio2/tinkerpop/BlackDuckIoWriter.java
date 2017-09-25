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

import static org.apache.tinkerpop.gremlin.process.traversal.P.within;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.hasLabel;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.hasNot;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.identity;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

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
import com.blackducksoftware.bdio2.tinkerpop.GraphContextFactory.AbstractContextBuilder;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.google.common.collect.Iterables;

import io.reactivex.Flowable;

public class BlackDuckIoWriter implements GraphWriter {

    private final GraphContextFactory contextFactory;

    private BlackDuckIoWriter(Builder builder) {
        contextFactory = builder.contextFactory();
    }

    @Override
    public void writeGraph(OutputStream outputStream, Graph graph) throws IOException {
        WriteGraphContext context = contextFactory.write(graph);
        RxJavaBdioDocument document = context.mapper().newBdioDocument(RxJavaBdioDocument.class);

        // Create the writer with the parsed metadata
        document.writeToFile(createMetadata(context, document.jsonld().options()), outputStream);

        // Collect the vertex labels which should be excluded from the output
        Collection<String> excludedLabels = new LinkedHashSet<>();
        context.mapper().metadataLabel().ifPresent(excludedLabels::add);
        context.mapper().forEachEmbeddedLabel(excludedLabels::add);

        // Construct a flowable using the vertex traversal as the iterator
        Flowable.fromIterable(() -> context.traversal().V()

                // Strip out the exclude labels
                // TODO .hasLabel(without(excludedLabels)) is currently broken on Sqlg
                .not(hasLabel(within(excludedLabels)))

                // Strip out the implicit vertices since they weren't originally included
                .where(context.mapper().implicitKey().map(propertyKey -> hasNot(propertyKey)).orElse(identity()))

                // Convert to JSON-LD
                .map(t -> createNode(t.get(), context)))
                .doOnTerminate(context::rollbackTx)
                .subscribe(document.asNodeSubscriber());
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
     * Creates a JSON-LD node from a vertex (or multiple vertices in the case of embedded objects).
     */
    private Map<String, Object> createNode(Vertex vertex, WriteGraphContext context) {
        Map<String, Object> result = context.getNodeProperties(vertex);

        vertex.edges(Direction.OUT).forEachRemaining(e -> {
            // Skip implicit edges
            if (context.mapper().implicitKey().filter(e.keys()::contains).isPresent()) {
                return;
            }

            // Convert the in vertex to a reference
            Vertex inVertex = e.inVertex();
            Object ref;
            if (context.mapper().isEmbeddedLabel(inVertex.label())) {
                // Recursively create the entire node (without an '@id') for embedded types
                ref = createNode(inVertex, context);
                ((Map<?, ?>) ref).remove(JsonLdConsts.ID);
            } else {
                // For non-embedded types we just need the identifier
                ref = context.generateId(inVertex);
            }

            // Store the result as a reference value object
            // TODO This needs to be a multi-map!
            result.put(e.label(), context.mapper().valueObjectMapper().toReferenceValueObject(ref));
        });

        return result;
    }

    /**
     * If a metadata label is configured, read the vertex from the graph into a new BDIO metadata instance.
     */
    private BdioMetadata createMetadata(WriteGraphContext context, JsonLdOptions options) {
        return context.mapper().metadataLabel()
                .flatMap(label -> context.traversal().V().hasLabel(label).tryNext())
                .map(vertex -> {
                    BdioMetadata metadata = new BdioMetadata();
                    context.mapper().identifierKey().ifPresent(key -> {
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

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractContextBuilder<BlackDuckIoWriter, Builder>
            implements WriterBuilder<BlackDuckIoWriter> {
        private Builder() {
            super(BlackDuckIoWriter::new);
        }
    }

}
