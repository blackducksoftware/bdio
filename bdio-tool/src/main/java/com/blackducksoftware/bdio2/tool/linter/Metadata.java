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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.tool.linter.Linter.RawEntryRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.blackducksoftware.bdio2.tool.linter.Linter.ViolationBuilder;
import com.blackducksoftware.common.value.ProductList;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableMap;

public class Metadata implements RawEntryRule {

    /**
     * Track the identifier used across metadata validations.
     */
    private Object id;

    @Override
    public Stream<Violation> validate(Object input) {
        ViolationBuilder result = new ViolationBuilder(this, input);

        // Input is not required to be a map, it can be a list or even a simple string
        if (input instanceof Map<?, ?> && ((Map<?, ?>) input).containsKey(JsonLdConsts.GRAPH)) {
            Map<?, ?> bdioEntry = (Map<?, ?>) input;

            // The identifier should always be present
            if (!bdioEntry.containsKey(JsonLdConsts.ID) || Objects.equals(bdioEntry.get(JsonLdConsts.ID), JsonLdConsts.DEFAULT)) {
                result.warning("DefaultNamedGraphIdentififer");
            } else {
                Object id = bdioEntry.get(JsonLdConsts.ID);
                if (this.id == null) {
                    result.compose(new ValidIdentifier(), ImmutableMap.of(JsonLdConsts.ID, bdioEntry.get(JsonLdConsts.ID)));
                    this.id = id;
                } else if (!this.id.equals(id)) {
                    result.error("MismatchedGraphLabel");
                }
            }

            // TODO Is it even possible to use object properties on metadata?
            for (Bdio.ObjectProperty objectProperty : Bdio.ObjectProperty.values()) {
                if (bdioEntry.containsKey(objectProperty.toString())) {
                    if (!Enums.getField(objectProperty).getAnnotation(Bdio.Domain.class).metadata()) {
                        result.error("PropertyNotAllowed", objectProperty.name());
                    }
                }
            }

            BdioContext context = BdioContext.getActive();
            for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
                String key = dataProperty.toString();
                if (bdioEntry.containsKey(key)) {
                    if (Enums.getField(dataProperty).getAnnotation(Bdio.Domain.class).metadata()) {
                        // TODO This repeats the logic from DataPropertyRange validation, we should make it more generic
                        try {
                            Object rawValue = context.fromFieldValue(key, bdioEntry.get(key));

                            Collection<?> values;
                            if (rawValue instanceof Collection<?>) {
                                values = (Collection<?>) rawValue;
                            } else if (rawValue != null) {
                                values = Collections.singleton(rawValue);
                            } else {
                                values = Collections.emptyList();
                            }

                            for (Object value : values) {
                                if (key.equals(Bdio.DataProperty.publisher.toString())) {
                                    ProductList productList = ProductList.from(value);
                                    if (productList.primary().version() == null) {
                                        result.warning("MissingPrimaryPublisherVersion", productList);
                                    }
                                    productList.tryFind(p -> p.name().equals("LegacyBdio1xEmitter") || p.name().equals("LegacyScanContainerEmitter"))
                                            .ifPresent(p -> result.error("ReservedLegacyPublisher", p.name()));
                                }
                            }
                        } catch (Exception e) {
                            result.error("Invalid", e, key, bdioEntry.get(key));
                        }
                    } else {
                        result.error("PropertyNotAllowed", dataProperty.name());
                    }
                }
            }

        } else if (input instanceof List<?>) {
            // A list is valid input, however, it makes it impossible to label the named graph
            result.warning("DefaultNamedGraphIdentififer");
        }

        return result.build();
    }

}
