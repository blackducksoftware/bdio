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
 * Term constants corresponding to the Resource Description Framework Schema properties.
 *
 * @author jgustie
 * @see <a href="http://www.w3.org/TR/rdf-schema/">RDF Schema</a>
 */
public enum RdfsTerm implements Term {

    RANGE("http://www.w3.org/2000/01/rdf-schema#range"),
    DOMAIN("http://www.w3.org/2000/01/rdf-schema#domain"),
    TYPE("http://www.w3.org/2000/01/rdf-schema#type"),
    SUB_CLASS_OF("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
    SUB_PROPERTY_OF("http://www.w3.org/2000/01/rdf-schema#subPropertyOf"),
    LABEL("http://www.w3.org/2000/01/rdf-schema#label"),
    COMMENT("http://www.w3.org/2000/01/rdf-schema#comment");

    private final URI uri;

    private RdfsTerm(String fullyQualifiedName) {
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
