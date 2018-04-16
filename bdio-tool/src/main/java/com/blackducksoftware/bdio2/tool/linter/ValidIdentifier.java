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
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.blackducksoftware.bdio2.tool.linter.Linter.ViolationBuilder;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Ascii;

public class ValidIdentifier implements RawNodeRule {

    @Override
    public Stream<Violation> validate(Map<String, Object> input) {
        ViolationBuilder result = new ViolationBuilder(this, input);

        Object id = input.get(JsonLdConsts.ID);
        if (id instanceof String) {
            try {
                // TODO We want RFC 3986 parsing, not RFC 2396
                URI uri = new URI((String) id);
                if (!uri.isAbsolute()) {
                    // Identifier should have a scheme
                    result.warning("Absolute");
                } else if (Ascii.equalsIgnoreCase(uri.getScheme(), "about")
                        || Ascii.equalsIgnoreCase(uri.getScheme(), "chrome")
                        || Ascii.equalsIgnoreCase(uri.getScheme(), "data")
                        || Ascii.equalsIgnoreCase(uri.getScheme(), "mailto")) {
                    // These schemes are not good identifiers to use in the graph
                    result.warning("Scheme");
                } else if (Ascii.equalsIgnoreCase(uri.getScheme(), "file")
                        || Ascii.equalsIgnoreCase(uri.getScheme(), "ftp")
                        || Ascii.equalsIgnoreCase(uri.getScheme(), "http")
                        || Ascii.equalsIgnoreCase(uri.getScheme(), "https")) {
                    // These schemes should include an authority when used in the graph to avoid ambiguity
                    if (uri.getAuthority() == null) {
                        result.warning("MissingAuthority");
                    }
                } else if (Ascii.equalsIgnoreCase(uri.getScheme(), "uuid")) {
                    // "uuid:" isn't a scheme, it should be "urn:uuid:"
                    result.warning("UUID");
                }
                // TODO Use of "mvn:g/a/v" vs. "mvn:g:a:v" (first is "correct")
                // See `https://www.iana.org/assignments/uri-schemes/prov/mvn` for examples
                // TODO Package URL ("pkg") format
            } catch (URISyntaxException e) {
                // Identifier should be a valid URI
                result.error("Invalid", e);
            }
        } else {
            // Identifier should be a string
            result.error("String");
        }

        return result.build();
    }

}
