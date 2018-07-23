/*
 * Copyright 2017 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2.tinkerpop;

import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static com.github.jsonldjava.core.JsonLdProcessor.expand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.google.common.collect.Lists;

/**
 * Context used when performing a
 * {@link org.apache.tinkerpop.gremlin.structure.io.GraphWriter#writeGraph(java.io.OutputStream, Graph)
 * GraphWrite.writeGraph} operation on BDIO data.
 *
 * @author jgustie
 */
class GraphWriterWrapper extends GraphIoWrapper {

    protected GraphWriterWrapper(Graph graph, GraphMapper mapper, List<TraversalStrategy<?>> strategies) {
        super(graph, mapper, strategies, Optional.empty());
    }

    /**
     * If a metadata label is configured, read the vertex from the graph into a new BDIO metadata instance.
     */
    public BdioMetadata createMetadata() {
        return mapper().metadataLabel()
                .flatMap(label -> traversal().V().hasLabel(label).tryNext())
                .map(vertex -> {
                    BdioMetadata metadata = new BdioMetadata();
                    mapper().identifierKey().ifPresent(key -> {
                        metadata.id(vertex.value(key));
                    });
                    try {
                        Map<String, Object> metadataProperties = new LinkedHashMap<>();
                        vertex.properties().forEachRemaining(vp -> {
                            // TODO Some properties come back without type information; how do we restore it?
                            // e.g. a product list will get serialized without it's type
                            metadataProperties.put(vp.key(), mapper().valueObjectMapper().toValueObject(vp.value()));
                        });
                        for (Object expanded : expand(metadataProperties, bdioOptions().jsonLdOptions())) {
                            if (expanded instanceof Map<?, ?>) {
                                ((Map<?, ?>) expanded).forEach((k, v) -> {
                                    metadata.put((String) k, v);
                                });
                            }
                        }
                    } catch (JsonLdError e) {
                        // TODO How should we handle this?
                    }
                    return metadata;
                })
                .orElse(BdioMetadata.createRandomUUID());
    }

    /**
     * Return the JSON-LD node constructed from the properties on the supplied vertex.
     */
    public Map<String, Object> getNodeProperties(Vertex vertex) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(JsonLdConsts.TYPE, vertex.label());
        result.put(JsonLdConsts.ID, generateId(vertex));
        vertex.properties().forEachRemaining(vp -> {
            if (mapper().unknownKey().filter(Predicate.isEqual(vp.key())).isPresent()) {
                // Restore unknown properties by putting them all back into the result map
                mapper().restoreUnknownProperties(vp.value(), result::put);
            } else if (isIgnoredKey(vp.key())) {
                // Skip all of the "special" vertex properties used internally on the graph
                return;
            } else {
                // Convert the vertex value to a JSON-LD value object
                result.put(vp.key(), mapper().valueObjectMapper().toValueObject(vp.value()));
            }
        });
        return result;
    }

    private boolean isIgnoredKey(String key) {
        Predicate<String> isKey = Predicate.isEqual(key);
        return isKey.test(GraphMapper.FILE_PARENT_KEY)
                || mapper().identifierKey().filter(isKey).isPresent()
                || mapper().implicitKey().filter(isKey).isPresent()
                || strategies().flatMap(ofType(PartitionStrategy.class))
                        .map(PartitionStrategy::getPartitionKey).anyMatch(isKey);
    }

    /**
     * Produces the identifier (the "@id" value) for a vertex based on the current configuration.
     */
    public String generateId(Vertex vertex) {
        Object identifier = mapper().identifierKey()
                .map(key -> vertex.property(key))
                .orElse(VertexProperty.empty())
                .orElseGet(() -> vertex.id());
        return mapper().valueObjectMapper().toValueObject(identifier).toString();
    }

    /**
     * Merges two values into a single list value.
     */
    @SuppressWarnings("unchecked")
    public Object combine(Object oldValue, Object value) {
        if (oldValue instanceof List<?>) {
            ((List<Object>) oldValue).add(value);
            return oldValue;
        } else {
            return Lists.newArrayList(oldValue, value);
        }
    }

}
