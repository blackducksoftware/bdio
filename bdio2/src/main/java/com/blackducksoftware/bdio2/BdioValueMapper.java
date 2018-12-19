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
package com.blackducksoftware.bdio2;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;

import javax.annotation.Nullable;

public interface BdioValueMapper {

    /**
     * Converts a JSON-LD field value into a typed representation.
     */
    Object fromFieldValue(Map<?, ?> fieldValue);

    /**
     * Converts a typed representation into something that can be handled by the JSON-LD serialization.
     * <p>
     * The output of this method is passed to a Jackson ObjectMapper created with the default configuration.
     */
    Object toFieldValue(@Nullable Object definedType, Object value, Predicate<Object> isEmbeddedType);

    // TODO We may need a `fromLiteralFieldValue(Object)` and `isLiteralFieldValue`

    /**
     * Returns the JSON-LD container specific collector used when multiple values are present.
     */
    Collector<? super Object, ?, ?> getCollector(@Nullable String container);

    /**
     * Splits the supplied value, this is the inverse of what container specific collector did.
     */
    Stream<?> split(Object value);

}
