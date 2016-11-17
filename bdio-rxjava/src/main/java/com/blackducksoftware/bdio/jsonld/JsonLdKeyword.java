/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio.jsonld;

public enum JsonLdKeyword {

    context("@context"),
    id("@id"),
    type("@type"),
    graph("@graph"),
    vocab("@vocab"),
    container("@container"),
    set("@set"),
    list("@list"),
    ;

    private final String keyword;

    private JsonLdKeyword(String keyword) {
        this.keyword = keyword;
    }

    @Override
    public String toString() {
        return keyword;
    }

}
