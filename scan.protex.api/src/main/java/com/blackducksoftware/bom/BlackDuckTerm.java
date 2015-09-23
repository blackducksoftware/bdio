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

    MD5("http://blackducksoftware.com/rdf/terms#md5"),
    SHA1("http://blackducksoftware.com/rdf/terms#sha1"),
    SIZE("http://blackducksoftware.com/rdf/terms#size"),
    CONTENT_TYPE("http://blackducksoftware.com/rdf/terms#contentType"),
    LEGACY_ID("http://blackducksoftware.com/rdf/terms#legacyId"),
    KNOWLEDGE_BASE_ID("http://blackducksoftware.com/rdf/terms#knowledgeBaseId");

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
