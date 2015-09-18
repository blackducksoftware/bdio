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

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Term constants corresponding to Bill of Materials properties.
 *
 * @author jgustie
 */
public enum BlackDuckTerm implements Term {

    MD5("http://blackducksoftware.com/rdf/terms#md5"),
    SHA1("http://blackducksoftware.com/rdf/terms#sha1"),
    SIZE("http://blackducksoftware.com/rdf/terms#size"),
    CONTENT_TYPE("http://blackducksoftware.com/rdf/terms#contentType");

    private final String fullyQualifiedName;

    private BlackDuckTerm(String fullyQualifiedName) {
        this.fullyQualifiedName = checkNotNull(fullyQualifiedName);
    }

    @Override
    public String toString() {
        return fullyQualifiedName;
    }
}
