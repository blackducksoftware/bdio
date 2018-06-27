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
package com.blackducksoftware.bdio2.tinkerpop;

import static org.apache.tinkerpop.gremlin.structure.Direction.OUT;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph.Features.VertexFeatures;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph.StarVertex;

import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.google.common.base.Throwables;
import com.google.common.collect.Streams;

/**
 * Node accumulator used when reading into a generic graph. This accumulator loads the entire input into memory to
 * prevent potentially expensive lookups when performing insertions, as such, it is not suitable for use with large
 * inputs.
 *
 * @author jgustie
 */
class DefaultNodeAccumulator extends NodeAccumulator {

    /**
     * Cached instance of vertex features.
     */
    private final VertexFeatures vertexFeatures;

    /**
     * The map used to store two copies of the entire graph at once.
     */
    private final Map<StarVertex, Vertex> persistedVertices = new HashMap<>();

    public DefaultNodeAccumulator(GraphReaderWrapper wrapper) {
        super(wrapper);
        this.vertexFeatures = graph().features().vertex();
    }

    @Override
    public DefaultNodeAccumulator addNode(Map<String, Object> node) {
        StarVertex baseVertex = createVertex(node);
        Vertex persisted = persistedVertices.get(baseVertex);
        if (persisted != null) {
            // Update properties
            wrapper().mergeProperties(persisted, baseVertex);

            // Update edges
            Iterator<Edge> edges = baseVertex.edges(Direction.OUT);
            if (edges.hasNext()) {
                // Worst case scenario. We need to get the old key, which means a linear search...
                // The problem is that `baseVertex` can be used for a lookup, but it's not the actual key
                for (StarVertex existingUnpersistedVertex : persistedVertices.keySet()) {
                    if (existingUnpersistedVertex.equals(baseVertex)) {
                        edges.forEachRemaining(e -> existingUnpersistedVertex.addEdge(e.label(), e.inVertex()));
                        break;
                    }
                }
            }
        } else {
            // Create the new vertex
            boolean includeId = vertexFeatures.willAllowId(baseVertex.id());
            persistedVertices.put(baseVertex, graph().addVertex(ElementHelper.getProperties(baseVertex, includeId, true, Collections.emptySet())));
        }

        // Batch commit update or insertion
        wrapper().batchFlushTx();
        return this;
    }

    @Override
    public void finish() throws NodeDoesNotExistException {
        try {
            addEdges();
        } catch (RuntimeException e) {
            // Unwrap NodeDoesNotExistException
            Throwables.propagateIfPossible(e.getCause(), NodeDoesNotExistException.class);
            throw e;
        }
    }

    private StarVertex createVertex(Map<String, Object> node) {
        StarGraph starGraph = StarGraph.open();
        StarVertex vertex = (StarVertex) starGraph.addVertex(wrapper().getNodeProperties(node, true));
        wrapper().getNodeObjectProperties(node, (edgeLabel, inVertexId) -> vertex.addEdge(edgeLabel, starGraph.addVertex(T.id, inVertexId)));
        return vertex;
    }

    private void addEdges() {
        // Stream all the edges from the StarVertex keys in the persisted map
        persistedVertices.keySet().stream().flatMap(v -> Streams.stream(v.edges(OUT))).forEach(e -> {
            // Look up the vertices
            Vertex cachedOutV = persistedVertices.get(e.outVertex());
            Vertex cachedInV = persistedVertices.get(e.inVertex());
            if (cachedInV == null) {
                throw new RuntimeException(new NodeDoesNotExistException(e.outVertex().id(), e.label(), e.inVertex().id()));
            }

            // First try to find an existing edge
            Iterator<Edge> edges = cachedOutV.edges(OUT, e.label());
            while (edges.hasNext()) {
                Edge edge = edges.next();
                if (edge.inVertex().equals(cachedInV)) {
                    return;
                }
            }

            // Create a new edge
            Edge edge = cachedOutV.addEdge(e.label(), cachedInV);
            wrapper().forEachPartition(edge::property);
            wrapper().batchFlushTx();
        });
    }

}
