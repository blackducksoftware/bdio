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

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tool.linter.Linter.RawNodeRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Severity;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.blackducksoftware.common.value.HID;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Ascii;

public class ValidFilePath implements RawNodeRule {

    @Override
    public Severity severity() {
        return Severity.error;
    }

    @Override
    public Stream<Violation> validate(Map<String, Object> input) {
        Stream.Builder<Violation> result = Stream.builder();

        if (Objects.equals(input.get(JsonLdConsts.TYPE), Bdio.Class.File.toString())) {
            Object path = input.get(Bdio.DataProperty.path.toString());
            if (path instanceof String) {
                HID hid = HID.from((String) path);
                if (!path.equals(hid.toUriString())) {
                    result.add(new Violation(this, input, "File path should be normalized"));
                }

                URI baseUri = hid.getBase().toUri();
                if (baseUri.isAbsolute()
                        && Ascii.equalsIgnoreCase(baseUri.getScheme(), "file")
                        && baseUri.getAuthority() == null) {
                    result.add(new Violation(this, input, "Base path 'file:' URI should include an authority"));
                }
            } else if (path != null) {
                result.add(new Violation(this, input, "Path should be a string"));
            }
        }

        return result.build();
    }

}
