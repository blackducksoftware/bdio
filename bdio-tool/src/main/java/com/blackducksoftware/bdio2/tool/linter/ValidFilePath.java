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
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.tool.linter.Linter.RawNodeRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.blackducksoftware.bdio2.tool.linter.Linter.ViolationBuilder;
import com.blackducksoftware.common.value.HID;

public class ValidFilePath implements RawNodeRule {

    @Override
    public Stream<Violation> validate(Map<String, Object> input) {
        ViolationBuilder result = new ViolationBuilder(this, input);

        if (RawNodeRule.types(input).anyMatch(Predicate.isEqual(Bdio.Class.File.toString()))) {
            BdioContext context = BdioContext.getDefault();
            Object path = context.fromFieldValue(Bdio.DataProperty.path.toString(), input.get(Bdio.DataProperty.path.toString()));
            if (path instanceof String) {
                HID hid = HID.from(path);

                // Make sure the path is in normal form
                if (!path.equals(hid.toUriString())) {
                    // TODO Should we force normalize everything and make this a warning?
                    result.error("PathNotNormalized", hid.toUriString());
                }

                // Sanity check the schemes
                HID container = hid;
                while (container.hasContainer()) {
                    if (UriSchemes.isBaseScheme(container.getScheme())) {
                        result.error("NestedBaseScheme", container.getScheme());
                    }
                    container = container.getContainer();
                }
                if (!UriSchemes.isBaseScheme(container.getScheme())) {
                    result.warning("UnknownBaseScheme", container.getScheme());
                }
            } else if (path != null) {
                result.error("String");
            }
        }

        return result.build();
    }

}
