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
 * Type constants corresponding to Bill Of Materials classes.
 *
 * @author jgustie
 */
public enum BlackDuckType implements Type {

    BILL_OF_MATERIALS("http://blackducksoftware.com/rdf/terms#BillOfMaterials"),
    COMPONENT("http://blackducksoftware.com/rdf/terms#Component"),
    FILE("http://blackducksoftware.com/rdf/terms#File"),
    LICENSE("http://blackducksoftware.com/rdf/terms#License"),
    PROJECT("http://blackducksoftware.com/rdf/terms#Project"),
    VERSION("http://blackducksoftware.com/rdf/terms#Version"),
    VULNERABILITY("http://blackducksoftware.com/rdf/terms#Vulnerability");

    private final URI uri;

    private BlackDuckType(String fullyQualifiedName) {
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
