/*
 * Copyright 2018 Synopsys, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blackducksoftware.bdio2.tinkerpop.spi;

import static com.blackducksoftware.common.base.ExtraStreams.fromOptional;
import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.reactivestreams.Publisher;

import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.bdio2.tinkerpop.util.UnknownProperties;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdProcessor;

public abstract class BlackDuckIoWriterSpi extends AbstractBlackDuckIoSpi {

    public BlackDuckIoWriterSpi(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        super(traversal, options, frame);
    }

    public abstract Publisher<Map<String, Object>> retrieveCompactedNodes();

    public BdioMetadata retrieveMetadata() {
        GraphTraversalSource g = traversal();
        return options().metadataLabel()
                .flatMap(label -> g.V().hasLabel(label).tryNext())
                .map(vertex -> {
                    BdioMetadata metadata = new BdioMetadata();

                    options().identifierKey().ifPresent(key -> {
                        metadata.id(vertex.value(key));
                    });

                    Map<String, Object> metadataProperties = new LinkedHashMap<>();
                    vertex.properties().forEachRemaining(vp -> {
                        // TODO Some properties come back without type information; how do we restore it?
                        // e.g. a product list will get serialized without it's type
                        metadataProperties.put(vp.key(), frame().context().toFieldValue(vp.key(), vp.value()));
                    });
                    for (Object expanded : JsonLdProcessor.expand(metadataProperties, frame().context().jsonLdOptions())) {
                        if (expanded instanceof Map<?, ?>) {
                            ((Map<?, ?>) expanded).forEach((k, v) -> {
                                metadata.put((String) k, v);
                            });
                        }
                    }

                    return metadata;
                })
                .orElseGet(BdioMetadata::createRandomUUID);
    }

    protected void getVertexFields(Vertex vertex, BiConsumer<String, Object> fields) {
        fields.accept(JsonLdConsts.TYPE, vertex.label());
        fields.accept(JsonLdConsts.ID, generateId(vertex));
        vertex.properties().forEachRemaining(vp -> {
            if (options().unknownKey().filter(Predicate.isEqual(vp.key())).isPresent()) {
                // Restore unknown properties by putting them all back into the result map
                UnknownProperties.restore(vp.value(), fields);
            } else if (isIgnoredKey(vp.key())) {
                // Skip all of the "special" vertex properties used internally on the graph
                return;
            } else {
                // Convert the vertex value to a JSON-LD value object
                fields.accept(vp.key(), frame().context().toFieldValue(vp.key(), vp.value()));
            }
        });
    }

    protected void getEdgeFields(Edge edge, Function<Vertex, Object> vertexToNode, BiConsumer<String, Object> fields) {
        Vertex inVertex = edge.inVertex();
        Object value;
        if (frame().context().isEmbedded(inVertex.label())) {
            value = vertexToNode.apply(inVertex);
            if (value instanceof Map<?, ?>) {
                ((Map<?, ?>) value).remove(JsonLdConsts.ID);
            }
        } else {
            value = generateId(inVertex);
        }
        fields.accept(edge.label(), frame().context().toFieldValue(edge.label(), value));
    }

    protected Collection<String> includedLabels() {
        return ((List<?>) frame().serialize().get(JsonLdConsts.TYPE)).stream()
                .flatMap(ofType(String.class))
                .flatMap(fromOptional(frame().context()::lookupTerm))
                .collect(toList());
    }

    private boolean isIgnoredKey(String key) {
        // TODO Filter out constants?
        Predicate<String> isKey = Predicate.isEqual(Objects.requireNonNull(key));
        return isKey.test(options().fileParentKey().orElse(null))
                || options().identifierKey().filter(isKey).isPresent()
                || traversal().getStrategies().toList().stream().flatMap(ofType(PartitionStrategy.class))
                        .map(PartitionStrategy::getPartitionKey).anyMatch(isKey);
    }

    /**
     * Produces the identifier (the "@id" value) for a vertex based on the current configuration.
     */
    protected String generateId(Vertex vertex) {
        // TODO This is probably broken
        return frame().context().toFieldValue(JsonLdConsts.ID, options().identifierKey()
                .map(key -> vertex.property(key))
                .orElse(VertexProperty.empty())
                .orElseGet(() -> vertex.id()))
                .toString();
    }

}
