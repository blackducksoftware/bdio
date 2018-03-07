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

import static com.google.common.truth.Truth8.assertThat;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategies;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.junit.Test;

/**
 * Tests for {@code BlackDuckIoCore}.
 *
 * @author jgustie
 */
public class BlackDuckIoCoreTest extends BaseTest {

    public BlackDuckIoCoreTest(Configuration configuration) {
        super(configuration);
    }

    @Test
    public void multiplePartitions() {
        BlackDuckIoCore bdio = new BlackDuckIoCore(graph);
        bdio = bdio.withStrategies(
                PartitionStrategy.build().partitionKey("a").create(),
                PartitionStrategy.build().partitionKey("b").create());

        TraversalStrategies strategies = bdio.traversal().getStrategies();
        assertThat(strategies.getStrategy(PartitionStrategy.class).map(PartitionStrategy::getPartitionKey)).hasValue("a");
        assertThat(strategies.toList().stream().filter(s -> s instanceof PartitionStrategy)).hasSize(2);
        assertThat(bdio.readerWrapper().strategies().filter(s -> s instanceof PartitionStrategy)).hasSize(2);
    }

    @Test
    public void overwritePartitions() {
        BlackDuckIoCore bdio = new BlackDuckIoCore(graph);
        bdio = bdio.withStrategies(PartitionStrategy.build().partitionKey("a").create());
        bdio = bdio.withStrategies(PartitionStrategy.build().partitionKey("b").create());

        TraversalStrategies strategies = bdio.traversal().getStrategies();
        assertThat(strategies.getStrategy(PartitionStrategy.class).map(PartitionStrategy::getPartitionKey)).hasValue("b");
        assertThat(strategies.toList().stream().filter(s -> s instanceof PartitionStrategy)).hasSize(1);
        assertThat(bdio.readerWrapper().strategies().filter(s -> s instanceof PartitionStrategy)).hasSize(1);
    }

}
