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

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategies;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.junit.Test;

/**
 * Tests for {@code GraphIoWrapper}.
 *
 * @author jgustie
 */
public class GraphIoWrapperTest {

    /**
     * We rely on third party code to perform a defensive copy for us, if that assumption changes this test will start
     * failing and we will need to code the defensive copy in ourselves.
     */
    @Test
    public void constructor_sortStrategiesReturnsCopy() {
        // Check an empty list
        List<TraversalStrategy<?>> emptyStrategies = new ArrayList<>();
        assertThat(TraversalStrategies.sortStrategies(emptyStrategies)).isNotSameAs(emptyStrategies);

        // Check a non-empty list with a mutation on the original
        List<TraversalStrategy<?>> singleStrategies = new ArrayList<>();
        singleStrategies.add(PartitionStrategy.build().partitionKey("test").create());
        List<TraversalStrategy<?>> singleSortedStrategies = TraversalStrategies.sortStrategies(singleStrategies);
        assertThat(singleSortedStrategies).isNotSameAs(singleStrategies);
        singleStrategies.clear();
        assertThat(singleSortedStrategies).isNotEmpty();
    }

}
