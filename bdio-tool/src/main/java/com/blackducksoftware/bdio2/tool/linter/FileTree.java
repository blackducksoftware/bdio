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
package com.blackducksoftware.bdio2.tool.linter;

import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.as;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.in;

import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tool.linter.Linter.CompletedGraphRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.blackducksoftware.bdio2.tool.linter.Linter.ViolationBuilder;

public class FileTree implements CompletedGraphRule {

    @Override
    public Stream<Violation> validate(GraphTraversalSource input) {
        ViolationBuilder result = new ViolationBuilder(this);

        input.V()
                .hasLabel(Bdio.Class.File.name())
                .has(Bdio.DataProperty.path.name())
                .match(as("file").outE(Bdio.ObjectProperty.parent.name()).count().is(0),
                        as("file").inE(Bdio.ObjectProperty.base.name()).count().is(0))
                .<Vertex> select("file")
                .emit().repeat(in(Bdio.ObjectProperty.parent.name()))
                .hasNot(Linter.LT._implicit.name())
                .forEachRemaining(file -> {
                    result.target(file).error("NonBaseTree", file.<String> value(Bdio.DataProperty.path.name()));
                });

        return result.build();
    }

}
