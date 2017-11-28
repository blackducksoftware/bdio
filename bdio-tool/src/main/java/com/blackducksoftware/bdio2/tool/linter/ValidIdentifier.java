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
import java.net.URISyntaxException;
import java.util.Map;
import java.util.stream.Stream;

import com.blackducksoftware.bdio2.tool.linter.Linter.RawNodeRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Severity;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Ascii;

public class ValidIdentifier implements RawNodeRule {

    @Override
    public Severity severity() {
        return Severity.warning;
    }

    @Override
    public Stream<Violation> validate(Map<String, Object> input) {
        Stream.Builder<Violation> result = Stream.builder();

        Object id = input.get(JsonLdConsts.ID);
        if (id instanceof String) {
            try {
                URI uri = new URI((String) id);
                if (!uri.isAbsolute()) {
                    // Identifier should have a scheme
                    result.add(new Violation(this, input, "Node identifiers should be absolute"));
                } else if (Ascii.equalsIgnoreCase(uri.getScheme(), "data")
                        || Ascii.equalsIgnoreCase(uri.getScheme(), "about")) {
                    // These schemes are not good identifiers to use in the graph
                    result.add(new Violation(this, input, "Node identifier scheme is questionable"));
                }
            } catch (URISyntaxException e) {
                // Identifier should be a valid URI
                result.add(new Violation(this, input, "Node identifier is not a valid URI"));
            }
        } else {
            // Identifier should be a string
            result.add(new Violation(this, input, "Node identifier should be a string"));
        }

        return result.build();
    }

}
