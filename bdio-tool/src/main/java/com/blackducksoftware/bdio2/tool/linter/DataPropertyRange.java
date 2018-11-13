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

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.Bdio.FileSystemType;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.tool.linter.Linter.RawNodeRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.blackducksoftware.bdio2.tool.linter.Linter.ViolationBuilder;
import com.blackducksoftware.common.value.HID;
import com.google.common.base.Enums;

public class DataPropertyRange implements RawNodeRule {

    @Override
    public Stream<Violation> validate(Map<String, Object> input) {
        ViolationBuilder result = new ViolationBuilder(this, input);

        BdioContext context = BdioContext.getActive();
        for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
            String key = dataProperty.toString();
            try {
                // Use the value object mapper to validate data properties
                Object rawValue = context.fromFieldValue(key, input.get(key));

                Collection<?> values;
                if (rawValue instanceof Collection<?>) {
                    values = (Collection<?>) rawValue;
                } else if (rawValue != null) {
                    values = Collections.singleton(rawValue);
                } else {
                    values = Collections.emptyList();
                }

                // TODO How do we detect invalid reference or embedded objects?

                // Check other fields which may have more restrictive rules
                for (Object value : values) {
                    if (key.equals(Bdio.DataProperty.fileSystemType.toString())) {
                        FileSystemType.from(value);
                    } else if (key.equals(Bdio.DataProperty.path.toString())
                            || key.equals(Bdio.DataProperty.linkPath.toString())) {
                        HID.from(value);
                    } else if (key.equals(Bdio.DataProperty.encoding.toString())) {
                        if (value instanceof String && !Charset.isSupported((String) value)) {
                            result.warning("UnsupportedCharset");
                        }
                    }
                }
            } catch (Exception e) {
                result.error("Invalid", e, key, input.get(key));
            }
        }

        // TODO Can we detect object properties used as data properties (e.g. "description")

        return result.build();
    }

}
