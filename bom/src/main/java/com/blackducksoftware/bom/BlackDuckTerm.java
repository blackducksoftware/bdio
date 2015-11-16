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
 * Term constants corresponding to Bill of Materials properties.
 *
 * @author jgustie
 */
public enum BlackDuckTerm implements Term {

    SIZE("http://blackducksoftware.com/rdf/terms#size"),

    EXTERNAL_IDENTIFIER("http://blackducksoftware.com/rdf/terms#externalIdentifier"),
    EXTERNAL_SYSTEM_TYPE_ID("http://blackducksoftware.com/rdf/terms#externalSystemTypeId"),
    EXTERNAL_ID("http://blackducksoftware.com/rdf/terms#externalId"),
    EXTERNAL_REPOSITORY_LOCATION("http://blackducksoftware.com/rdf/terms#externalRepositoryLocation"),

    MATCH_DETAIL("http://blackducksoftware.com/rdf/terms#matchDetail"),
    MATCH_TYPE("http://blackducksoftware.com/rdf/terms#matchType"),
    CONTENT("http://blackducksoftware.com/rdf/terms#content");

    private final URI uri;

    private BlackDuckTerm(String fullyQualifiedName) {
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
