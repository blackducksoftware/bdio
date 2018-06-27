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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex;

import com.blackducksoftware.bdio2.NodeDoesNotExistException;

/**
 * A node accumulator for populating a TinkerGraph. When loading into a TinkerGraph, this implementation beats the
 * {@link DefaultNodeAccumulator} because it leverages the in-memory graph without the need for a separate cache.
 *
 * @author jgustie
 */
class TinkerGraphNodeAccumulator extends NodeAccumulator {

    public static boolean acceptWrapper(GraphReaderWrapper wrapper) {
        return wrapper.graph() instanceof TinkerGraph;
    }

    public TinkerGraphNodeAccumulator(GraphReaderWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public TinkerGraphNodeAccumulator addNode(Map<String, Object> node) {
        // Directly create the vertex and it's edges in the graph
        Vertex vertex = createVertex(node);
        createEdges(node, vertex);
        return this;
    }

    @Override
    public void finish() throws NodeDoesNotExistException {
        // Fail if there are any place holders left
        Optional<Edge> anyPlaceHolderEdge = graph().traversal().V().hasLabel(TinkerVertex.DEFAULT_LABEL).inE().tryNext();
        if (anyPlaceHolderEdge.isPresent()) {
            throw new NodeDoesNotExistException(
                    anyPlaceHolderEdge.get().outVertex().id(),
                    anyPlaceHolderEdge.get().label(),
                    anyPlaceHolderEdge.get().inVertex().id());
        }
    }

    private Vertex createVertex(Map<String, Object> node) {
        Object[] keyValues = wrapper().getNodeProperties(node, true);
        return single(graph().vertices(ElementHelper.getIdValue(keyValues).get()),
                v -> {
                    if (v.label().equals(TinkerVertex.DEFAULT_LABEL)) {
                        // If the existing vertex has a default label, it is a place holder that must be replaced
                        Vertex replacementVertex = graph().addVertex(keyValues);

                        // Place holders only have incoming edges (and identifiers)
                        v.edges(Direction.IN).forEachRemaining(e -> {
                            Edge replacementEdge = e.outVertex().addEdge(e.label(), replacementVertex);
                            e.properties().forEachRemaining(p -> replacementEdge.property(p.key(), p.value()));
                        });
                        v.remove();

                        return replacementVertex;
                    } else {
                        // Attach the additional properties
                        wrapper().mergeProperties(v, null, keyValues);
                        return v;
                    }
                },
                () -> graph().addVertex(keyValues));
    }

    private void createEdges(Map<String, Object> node, Vertex vertex) {
        wrapper().getNodeObjectProperties(node, (edgeLabel, inVertexId) -> {
            Vertex inVertex = single(graph().vertices(inVertexId), x -> x,
                    // If the in-vertex does not exist yet, add a place holder
                    () -> graph().addVertex(placeHolder(inVertexId)));
            Edge edge = vertex.addEdge(edgeLabel, inVertex);
            wrapper().forEachPartition(edge::property);
        });
    }

    private static <E, R> R single(Iterator<E> iter, Function<E, R> next, Supplier<R> defaultIfEmpty) {
        if (iter.hasNext()) {
            E value = iter.next();
            checkArgument(!iter.hasNext());
            return next.apply(value);
        } else {
            return defaultIfEmpty.get();
        }
    }

    private static Object[] placeHolder(Object id) {
        return new Object[] { T.id, id };
    }

}
