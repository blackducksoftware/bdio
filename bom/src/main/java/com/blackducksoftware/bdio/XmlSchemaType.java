/*
 * Copyright 2015 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio;

import java.net.URI;

/**
 * Type constants corresponding to the XML Schema types.
 *
 * @author jgustie
 * @see <a href="http://www.w3.org/TR/xmlschema-2/">XML Schema Part 2: Datatypes Second Edition</a>
 */
public enum XmlSchemaType implements Type {

    // TODO Include more primitive types
    // http://www.w3.org/TR/xmlschema-2/#built-in-primitive-datatypes

    LONG("http://www.w3.org/2001/XMLSchema#long");

    private final URI uri;

    private XmlSchemaType(String fullyQualifiedName) {
        uri = URI.create(fullyQualifiedName);
    }

    @Override
    public String toString() {
        return uri.toString();
    }

    @Override
    public URI toUri() {
        return uri;
    }

}
