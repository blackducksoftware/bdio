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
package com.blackducksoftware.bom;

import java.net.URI;

/**
 * Term constants corresponding to the Resource Description Framework syntax properties.
 *
 * @author jgustie
 * @see <a href="http://www.w3.org/TR/rdf11-concepts/">RDF 1.1 Concepts</a>
 */
public enum RdfTerm implements Term {

    TYPE("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
    SUBJECT("http://www.w3.org/1999/02/22-rdf-syntax-ns#subject"),
    PREDICATE("http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate"),
    OBJECT("http://www.w3.org/1999/02/22-rdf-syntax-ns#object"),
    VALUE("http://www.w3.org/1999/02/22-rdf-syntax-ns#value"),
    FIRST("http://www.w3.org/1999/02/22-rdf-syntax-ns#first"),
    REST("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest");

    private final URI uri;

    private RdfTerm(String fullyQualifiedName) {
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
