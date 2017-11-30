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
package com.blackducksoftware.bdio2.tool.linter;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.blackducksoftware.bdio2.tool.linter.Linter.CompletedGraphRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.LT;
import com.blackducksoftware.bdio2.tool.linter.Linter.Severity;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;

public class SingleRoot implements CompletedGraphRule {

    @Override
    public Severity severity() {
        return Severity.error;
    }

    @Override
    public Stream<Violation> validate(GraphTraversalSource g) {
        Stream.Builder<Violation> result = Stream.builder();

        List<Vertex> metadata = g.V().hasLabel(LT._Metadata.name()).toList();
        if (metadata.isEmpty()) {
            result.add(new Violation(this, Collections.emptyMap(), "Missing metadata"));
        } else if (metadata.size() > 1) {
            result.add(new Violation(this, metadata.get(1), "Multiple metadata instances"));
        } else {
            Iterator<Edge> roots = metadata.get(0).edges(Direction.OUT, LT._root.name());
            if (roots.hasNext()) {
                roots.next();
                if (roots.hasNext()) {
                    result.add(new Violation(this, roots.next().inVertex(), "Multiple roots"));
                }
            } else {
                result.add(new Violation(this, metadata.get(0), "Missing root"));
            }
        }

        return result.build();
    }

}
