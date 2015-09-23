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
 * Term constants corresponding to the Software Package Data Exchange properties.
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
