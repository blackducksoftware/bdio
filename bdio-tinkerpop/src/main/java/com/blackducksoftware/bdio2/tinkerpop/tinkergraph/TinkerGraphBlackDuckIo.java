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

import java.util.Optional;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoReaderSpi;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoSpi;

public class TinkerGraphBlackDuckIo extends BlackDuckIoSpi {

    private static final TinkerGraphBlackDuckIo INSTANCE = new TinkerGraphBlackDuckIo();

    public static TinkerGraphBlackDuckIo getInstance() {
        return INSTANCE;
    }

    private TinkerGraphBlackDuckIo() {
    }

    @Override
    protected Optional<BlackDuckIoReaderSpi> providerReader(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame, int batchSize) {
        return Optional.of(new TinkerGraphBlackDuckIoReader(traversal, options, frame));
    }

}
