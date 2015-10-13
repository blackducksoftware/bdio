/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
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
