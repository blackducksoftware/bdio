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
package com.blackducksoftware.bdio.io;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.URI;

import com.blackducksoftware.bdio.Term;

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

    @Override
    public URI toUri() {
        throw new UnsupportedOperationException();
    }
}
