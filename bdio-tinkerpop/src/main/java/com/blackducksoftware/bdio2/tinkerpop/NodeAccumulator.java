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

import java.util.Map;
import java.util.Objects;

import org.apache.tinkerpop.gremlin.structure.Graph;

import com.blackducksoftware.bdio2.NodeDoesNotExistException;

abstract class NodeAccumulator {

    /**
     * The wrapper used to access the graph.
     */
    private final GraphReaderWrapper wrapper;

    protected NodeAccumulator(GraphReaderWrapper wrapper) {
        this.wrapper = Objects.requireNonNull(wrapper);
    }

    /**
     * Returns the current graph wrapper.
     */
    protected GraphReaderWrapper wrapper() {
        return wrapper;
    }

    /**
     * Convenience method to acccess the graph.
     */
    protected Graph graph() {
        return wrapper.graph();
    }

    /**
     * Adds a JSON-LD node to this accumulator. In general each node <em>should</em> have an identifier and type, both
     * with {@code String} values, however (and especially with the type) this is not a requirement and implementations
     * should handle both non-string and missing identifier and type information.
     */
    public abstract NodeAccumulator addNode(Map<String, Object> node);

    public abstract void finish() throws NodeDoesNotExistException;

}
