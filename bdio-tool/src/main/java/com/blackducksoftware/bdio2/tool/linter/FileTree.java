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

import java.util.Set;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tool.linter.Linter.CompletedGraphRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.blackducksoftware.bdio2.tool.linter.Linter.ViolationBuilder;
import com.blackducksoftware.common.value.HID;

public class FileTree implements CompletedGraphRule {

    @Override
    public Stream<Violation> validate(GraphTraversalSource input) {
        ViolationBuilder result = new ViolationBuilder(this);

        // Collect the base paths
        Set<HID> basePaths = input.E().hasLabel(Bdio.ObjectProperty.base.name())
                .inV()
                .values(Bdio.DataProperty.path.name())
                .map(t -> HID.from(t.get()))
                .toSet();

        // Validate every path against the base path
        input.V()
                .hasLabel(Bdio.Class.File.name())
                .has(Bdio.DataProperty.path.name())
                .forEachRemaining(file -> {
                    HID path = HID.from(file.value(Bdio.DataProperty.path.name()));
                    if (!basePaths.contains(path) && !basePaths.stream().anyMatch(base -> path.isAncestor(base))) {
                        result.target(file).error("NonBaseTree", path.toUriString());
                    }
                });

        // TODO Should we also check that the parents terminate at bases?

        return result.build();
    }

}
