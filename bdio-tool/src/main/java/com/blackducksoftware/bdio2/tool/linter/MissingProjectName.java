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

import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tool.linter.Linter.LoadedGraphRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Severity;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;

public class MissingProjectName implements LoadedGraphRule {

    @Override
    public Severity severity() {
        return Severity.error;
    }

    @Override
    public Stream<Violation> validate(Vertex input) {
        Stream.Builder<Violation> result = Stream.builder();

        if (input.label().equals(Bdio.Class.Project.name())
                && !input.property(Bdio.DataProperty.name.name()).isPresent()) {
            if (input.property(Bdio.DataProperty.version.name()).isPresent()) {
                result.add(new Violation(this, input, "Project has version but no name"));
            } else {
                result.add(new Violation(this, input, "Project with no name should be a FileCollection"));
            }
        }

        return result.build();
    }

}
