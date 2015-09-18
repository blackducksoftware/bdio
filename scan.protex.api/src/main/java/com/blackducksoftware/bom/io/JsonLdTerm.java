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
package com.blackducksoftware.bom.io;

import static com.google.common.base.Preconditions.checkArgument;

import com.blackducksoftware.bom.Term;

/**
 * Terms specific to JSON-LD that we really only need while writing. Technically these are "keywords", but it is useful
 * to express them as terms.
 *
 * @author jgustie
 */
// TODO Rename to JsonLdKeyword
public enum JsonLdTerm implements Term {

    // TODO Doc
    CONTEXT("@context"),

    /**
     * A keyword for the identifier of a node. The value must be a valid IRI.
     */
    ID("@id"),

    // TODO Doc
    VALUE("@value"),

    // TODO Doc
    LANGUAGE("@language"),

    /**
     * A keyword for the types of a node. The value must be one or more valid IRI(s).
     */
    TYPE("@type"),

    // TODO Other keywords: container,list,set,reverse,index,base

    // TODO Doc
    VOCAB("@vocab"),

    // TODO Doc
    GRAPH("@graph");

    private final String keyword;

    private JsonLdTerm(String keyword) {
        checkArgument(keyword.charAt(0) == '@', "keyword must start with @: %s", keyword);
        this.keyword = keyword;
    }

    /**
     * Note that this implementation returns keywords, not IRIs.
     */
    @Override
    public String toString() {
        return keyword;
    }
}
