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

import java.util.Objects;
import java.util.function.BiConsumer;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;

import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.bdio2.tinkerpop.strategy.PropertyConstantStrategy;

public abstract class AbstractBlackDuckIoSpi {

    private final GraphTraversalSource traversal;

    private final BlackDuckIoOptions options;

    private final BdioFrame frame;

    protected AbstractBlackDuckIoSpi(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        this.traversal = Objects.requireNonNull(traversal);
        this.options = Objects.requireNonNull(options);
        this.frame = Objects.requireNonNull(frame);
    }

    protected final GraphTraversalSource traversal() {
        return traversal;
    }

    protected final BlackDuckIoOptions options() {
        return options;
    }

    protected final BdioFrame frame() {
        return frame;
    }

    protected Graph graph() {
        return traversal.getGraph();
    }

    protected void getTraversalProperties(BiConsumer<Object, Object> properties, boolean includeConstants) {
        for (TraversalStrategy<?> strategy : traversal.getStrategies().toList()) {
            if (strategy instanceof PartitionStrategy && ((PartitionStrategy) strategy).getWritePartition() != null) {
                properties.accept(((PartitionStrategy) strategy).getPartitionKey(), ((PartitionStrategy) strategy).getWritePartition());
            } else if (strategy instanceof PropertyConstantStrategy) {
                ((PropertyConstantStrategy) strategy).getPropertyMap().forEach(properties);
            }
        }
    }

}
