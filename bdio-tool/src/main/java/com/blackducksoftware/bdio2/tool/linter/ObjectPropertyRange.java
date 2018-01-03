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

import static com.blackducksoftware.common.base.ExtraEnums.tryByName;
import static java.util.Collections.emptySet;

import java.util.Set;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tool.linter.Linter.CompletedGraphRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.blackducksoftware.bdio2.tool.linter.Linter.ViolationBuilder;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableSet;

public class ObjectPropertyRange implements CompletedGraphRule {

    @Override
    public Stream<Violation> validate(GraphTraversalSource g) {
        ViolationBuilder result = new ViolationBuilder(this);

        // Validate EVERY EDGE
        g.E().forEachRemaining(e -> {
            Set<Bdio.Class> range = tryByName(Bdio.ObjectProperty.class, e.label()).map(ObjectPropertyRange::range).orElse(emptySet());
            if (!range.isEmpty() && !tryByName(Bdio.Class.class, e.inVertex().label()).filter(range::contains).isPresent()) {
                result.target(e).error("InvalidRange");
            }
        });

        return result.build();
    }

    private static Set<Bdio.Class> range(Bdio.ObjectProperty objectProperty) {
        return ImmutableSet.copyOf(Enums.getField(objectProperty).getAnnotation(Bdio.ObjectPropertyRange.class).value());
    }

}
