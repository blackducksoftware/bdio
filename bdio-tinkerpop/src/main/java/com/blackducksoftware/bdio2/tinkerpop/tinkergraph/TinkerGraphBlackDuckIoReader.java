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
package com.blackducksoftware.bdio2.tinkerpop.tinkergraph;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.tinkerpop.gremlin.structure.Vertex.DEFAULT_LABEL;
import static org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality.single;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedEdge;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedFactory;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import org.reactivestreams.Publisher;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoReaderSpi;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;

/**
 * BDIO reader implementation optimized for the TinkerGraph.
 *
 * @author jgustie
 */
class TinkerGraphBlackDuckIoReader extends BlackDuckIoReaderSpi {

    // IMPORTANT: This code MUST NOT directly reference the TinkerGraph! We do not have a compile time dependency on the
    // TinkerGraph as we do not want that transitive dependency leaking out. This implementation uses the standard
    // TinkerPop API with an understanding of how the TinkerGraph implementation works.

    /**
     * Returns the only element from the supplied iterator mapped using a function. If the iterator is empty, a default
     * is lazily obtained from a supplier.
     */
    private static <E, R> R single(Iterator<E> iter, Function<E, R> next, Supplier<R> defaultIfEmpty) {
        if (iter.hasNext()) {
            E value = iter.next();
            checkArgument(!iter.hasNext());
            return next.apply(value);
        } else {
            return defaultIfEmpty.get();
        }
    }

    public TinkerGraphBlackDuckIoReader(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        super(traversal, options, frame);
    }

    @Override
    public Publisher<?> persistFramedEntries(Flowable<Map<String, Object>> framedEntries) {
        return framedEntries
                .flatMapIterable(BdioDocument::toGraphNodes)
                .reduce(this, TinkerGraphBlackDuckIoReader::accumulate)
                .flatMapCompletable(TinkerGraphBlackDuckIoReader::checkForMissingNodes)
                .toFlowable();
    }

    private TinkerGraphBlackDuckIoReader accumulate(Map<String, Object> node) {
        // Create (or update) the vertex and edges
        createEdges(node, createOrUpdateVertex(node));
        return this;
    }

    private Vertex createOrUpdateVertex(Map<String, Object> node) {
        // Convert the node into a key/value property list
        List<Object> keyValueList = new ArrayList<>();
        getNodeProperties(node, (k, v) -> {
            // Ignore null values, they aren't allowed in the TinkerGraph
            if (v != null) {
                keyValueList.add(k);
                keyValueList.add(v);
            }
        });
        Object[] keyValues = keyValueList.toArray();

        return single(graph().vertices(ElementHelper.getIdValue(keyValues).get()),
                v -> {
                    if (v.label().equals(DEFAULT_LABEL)) {
                        // Detach the edges and recreate them on a replacement vertex
                        List<DetachedEdge> edges = IteratorUtils.list(IteratorUtils.map(v.edges(Direction.IN), e -> DetachedFactory.detach(e, true)));
                        v.remove();
                        Vertex replacementVertex = addVertex(keyValues);
                        edges.forEach(e -> e.attach(Attachable.Method.create(graph())));
                        return replacementVertex;
                    } else {
                        // Attach the additional properties
                        // TODO Lookup the cardinality
                        ElementHelper.attachProperties(v, single, keyValues);
                        return v;
                    }
                },
                () -> addVertex(keyValues));
    }

    private Vertex addVertex(Object... keyValues) {
        // We need to use the traversal so any strategies that modify the properties are applied
        GraphTraversalSource g = traversal();
        GraphTraversal<Vertex, Vertex> t = g.addV(ElementHelper.getLabelValue(keyValues).get());
        for (int i = 0; i < keyValues.length; ++i) {
            t = t.property(keyValues[i], keyValues[++i]);
        }
        return t.next();
    }

    private void createEdges(Map<String, Object> node, Vertex vertex) {
        GraphTraversalSource g = traversal();
        getNodeEdges(node, (edgeLabel, inVertexId) -> {
            Vertex inVertex = single(graph().vertices(inVertexId), x -> x,
                    // If the in-vertex does not exist yet, add a place holder
                    () -> g.addV().property(T.id, inVertexId).next());
            g.V(vertex).addE(edgeLabel).to(inVertex).iterate();
        });
    }

    private Completable checkForMissingNodes() {
        return graph().traversal().V().hasLabel(DEFAULT_LABEL).inE().tryNext()
                .map(e -> new NodeDoesNotExistException(e.outVertex().id(), e.label(), e.inVertex().id()))
                .map(Completable::error)
                .orElseGet(Completable::complete);
    }

}
