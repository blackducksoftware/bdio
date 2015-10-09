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

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Node constants corresponding to the Software Package Data Exchange individuals.
 *
 * @author jgustie
 * @see <a href="http://spdx.org/rdf/ontology/spdx-2-0/">SPDX RDF Terms</a>
 */
public enum BlackDuckValue implements Node {

    FILE_TYPE_DIRECTORY("http://blackducksoftware.com/rdf/terms#fileType_directory");

    private final String fullyQualifiedName;

    private BlackDuckValue(String fullyQualifiedName) {
        this.fullyQualifiedName = checkNotNull(fullyQualifiedName);
    }

    @Override
    public String id() {
        return fullyQualifiedName;
    }

    @Override
    public Set<Type> types() {
        return ImmutableSet.of();
    }

    @Override
    public Map<Term, Object> data() {
        return ImmutableMap.of();
    }
}
