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
import com.blackducksoftware.bdio2.Bdio.FileSystemType;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.tool.linter.Linter.RawNodeRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.blackducksoftware.bdio2.tool.linter.Linter.ViolationBuilder;
import com.blackducksoftware.common.base.ExtraEnums;
import com.blackducksoftware.common.value.HID;

public class DataPropertyRange implements RawNodeRule {

    @Override
    public Stream<Violation> validate(Map<String, Object> input) {
        ValueObjectMapper valueObjectMapper = ValueObjectMapper.getContextValueObjectMapper();
        return ExtraEnums.stream(Bdio.DataProperty.class)
                .map(Object::toString)
                .flatMap(key -> {
                    try {
                        // Use the value object mapper to validate data properties
                        Object value = valueObjectMapper.fromFieldValue(key, input.get(key));

                        // Check other fields which may have more restrictive rules
                        if (value != null) {
                            if (key.equals(Bdio.DataProperty.fileSystemType.toString())) {
                                FileSystemType.from(value);
                            } else if (key.equals(Bdio.DataProperty.path.toString())
                                    || key.equals(Bdio.DataProperty.linkPath.toString())) {
                                HID.from(value);
                            }
                            // TODO Check encoding (maybe warn if the encoding is valid but isn't supported)
                        }

                        return Stream.empty();
                    } catch (Exception e) {
                        return new ViolationBuilder(this, input).error("Invalid", e, key).build();
                    }
                });
    }

}
