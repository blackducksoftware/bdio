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
import java.util.stream.Stream;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tool.linter.Linter.RawNodeRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Severity;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;

public class AllowedOn implements RawNodeRule {

    private final ImmutableSet<String> knownProperties;

    private final ImmutableMultimap<String, String> allowedProperties;

    public AllowedOn() {
        // Build a map of type strings to property keys
        ImmutableSet.Builder<String> known = ImmutableSet.builder();
        ImmutableSetMultimap.Builder<String, String> allowed = ImmutableSetMultimap.builder();
        for (Bdio.ObjectProperty objectProperty : Bdio.ObjectProperty.values()) {
            known.add(objectProperty.toString());
            Bdio.AllowedOn allowedOn = Enums.getField(objectProperty).getAnnotation(Bdio.AllowedOn.class);
            if (allowedOn != null) {
                for (Bdio.Class allowedClass : allowedOn.value()) {
                    allowed.put(allowedClass.toString(), objectProperty.toString());
                }
            }
        }
        for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
            known.add(dataProperty.toString());
            Bdio.AllowedOn allowedOn = Enums.getField(dataProperty).getAnnotation(Bdio.AllowedOn.class);
            if (allowedOn != null) {
                for (Bdio.Class allowedClass : allowedOn.value()) {
                    allowed.put(allowedClass.toString(), dataProperty.toString());
                }
            }
        }
        knownProperties = known.build();
        allowedProperties = allowed.build();
    }

    @Override
    public Severity severity() {
        return Severity.error;
    }

    @Override
    public Stream<Violation> validate(Map<String, Object> input) {
        Stream.Builder<Violation> result = Stream.builder();

        Object type = input.get(JsonLdConsts.TYPE);
        if (type != null) {
            for (String key : input.keySet()) {
                if (knownProperties.contains(key) && !allowedProperties.containsEntry(type, key)) {
                    result.add(new Violation(this, input, "The property %s is not allowed on the type %s", key, type));
                }
            }
        }

        return result.build();
    }

}
