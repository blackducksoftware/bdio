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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.umlg.sqlg.structure.SqlgGraph;

/**
 * Specialization of the read graph context to use when the underlying graph is a Sqlg graph.
 *
 * @author jgustie
 */
class SqlgGraphReaderWrapper extends GraphReaderWrapper {

    /**
     * Flag indicating if the graph supports batch mode or not.
     */
    private final boolean supportsBatchMode;

    protected SqlgGraphReaderWrapper(SqlgGraph sqlgGraph, GraphMapper mapper, List<TraversalStrategy<?>> strategies, Optional<Object> expandContext,
            int batchSize) {
        super(sqlgGraph, mapper, strategies, expandContext, batchSize);
        this.supportsBatchMode = sqlgGraph.features().supportsBatchMode();
    }

    @Override
    public SqlgGraph graph() {
        return (SqlgGraph) super.graph();
    }

    @Override
    public void startBatchTx() {
        // (Re-)enable batch mode if it is supported
        super.startBatchTx();
        if (supportsBatchMode) {
            graph().tx().normalBatchModeOn();
        }
    }

    @Override
    public Object[] getNodeProperties(Map<String, Object> node, boolean includeSpecial) {
        Object[] result = super.getNodeProperties(node, includeSpecial);

        // Until issue #294 is resolved we cannot include ZonedDateTime instances when normal batch mode is enabled

        // The bug impacts batch mode when one vertex has the value and the other does not; e.g. one file has a last
        // modified time and the other does not. Since we cannot inspect the entire vertex cache to determine if we need
        // to strip ZonedDateTime instances (or really any property type with a postfix, we just don't use `Period` or
        // `Duration`), the best we can do is make a guess based on the property values we see.

        // If `includeSpecial == false` we are most likely processing metadata, there will only be a single vertex
        // created for that vertex label therefore the bug will not occur.

        // Otherwise only 'Annotation', 'File' and 'Vulnerability' are affected

        if (includeSpecial && graph().tx().isInNormalBatchMode() && hasZonedDateTime(result)) {
            return IntStream.range(0, result.length)
                    .filter(i -> i % 2 == 0)
                    .filter(i -> !(result[i + 1] instanceof ZonedDateTime))
                    .flatMap(i -> IntStream.of(i, i + 1))
                    .mapToObj(i -> result[i])
                    .toArray();
        }

        return result;
    }

    private static boolean hasZonedDateTime(Object[] keyValuePairs) {
        for (int i = 1; i < keyValuePairs.length; i += 2) {
            if (keyValuePairs[i] instanceof ZonedDateTime) {
                return true;
            }
        }
        return false;
    }

}
