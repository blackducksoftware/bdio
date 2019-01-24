/*
 * Copyright 2019 Synopsys, Inc.
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

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoNormalizationSpi;

/**
 * BDIO normalization implementation optimized for the TinkerGraph.
 *
 * @author jgustie
 */
final class TinkerGraphBlackDuckIoNormalization extends BlackDuckIoNormalizationSpi {

    public TinkerGraphBlackDuckIoNormalization(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        super(traversal, options, frame);
    }

    @Override
    protected void createParentEdges() {
        GraphTraversalSource g = traversal();

        g.V().hasLabel(Bdio.Class.File.name()).has(options().fileParentKey().get()).as("child")
                .<String> values(options().fileParentKey().get())
                // The flat map here forces the use of the index on path
                .flatMap(t -> g.V().hasLabel(Bdio.Class.File.name()).has(Bdio.DataProperty.path.name(), t.get()))
                .addE(Bdio.ObjectProperty.parent.name())
                .from("child")
                .iterate();
    }

}
