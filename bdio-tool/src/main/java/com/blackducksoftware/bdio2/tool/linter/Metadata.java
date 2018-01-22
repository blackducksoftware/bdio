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

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tool.linter.Linter.RawEntryRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.blackducksoftware.bdio2.tool.linter.Linter.ViolationBuilder;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Enums;

public class Metadata implements RawEntryRule {

    @Override
    public Stream<Violation> validate(Object input) {
        ViolationBuilder result = new ViolationBuilder(this, input);

        // Input is not required to be a map, it can be a list or even a simple string
        if (input instanceof Map<?, ?> && ((Map<?, ?>) input).containsKey(JsonLdConsts.GRAPH)) {
            Map<?, ?> bdioEntry = (Map<?, ?>) input;

            // TODO The bdioEntry map should run through the DataPropertyRange validation

            // The identifier should always be present
            if (!bdioEntry.containsKey(JsonLdConsts.ID) || Objects.equals(bdioEntry.get(JsonLdConsts.ID), JsonLdConsts.DEFAULT)) {
                result.error("DefaultNamedGraphIdentififer");
            }

            for (Bdio.ObjectProperty objectProperty : Bdio.ObjectProperty.values()) {
                if (bdioEntry.containsKey(objectProperty.toString())) {
                    if (!Enums.getField(objectProperty).getAnnotation(Bdio.Domain.class).metadata()) {
                        result.error("PropertyNotAllowed", objectProperty.name());
                    }
                }
            }

            for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
                if (bdioEntry.containsKey(dataProperty.toString())) {
                    if (!Enums.getField(dataProperty).getAnnotation(Bdio.Domain.class).metadata()) {
                        result.error("PropertyNotAllowed", dataProperty.name());
                    }
                }
            }
        }

        return result.build();
    }

}
