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

import static org.apache.tinkerpop.gremlin.process.traversal.P.within;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.reactivestreams.Publisher;

import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;

import io.reactivex.rxjava3.core.Flowable;

final class DefaultBlackDuckIoWriter extends BlackDuckIoWriterSpi {

    public DefaultBlackDuckIoWriter(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        super(traversal, options, frame);
    }

    @Override
    public BdioMetadata retrieveMetadata() {
        // TODO Make sure we don't leak a transaction!
        return super.retrieveMetadata();
    }

    @Override
    public Publisher<Map<String, Object>> retrieveCompactedNodes() {
        // TODO Make sure we don't leak a transaction!
        GraphTraversalSource g = traversal();
        return Flowable.fromIterable(() -> g.V().hasLabel(within(includedLabels())).map(t -> convertVertexToNode(t.get())));
    }

    /**
     * Creates a JSON-LD node from a vertex (or multiple vertices in the case of embedded objects).
     */
    private Map<String, Object> convertVertexToNode(Vertex vertex) {
        Map<String, Object> result = new LinkedHashMap<>();
        getVertexFields(vertex, result::put);
        vertex.edges(Direction.OUT).forEachRemaining(e -> getEdgeFields(e, this::convertVertexToNode, result::put));
        return result;
    }

}
