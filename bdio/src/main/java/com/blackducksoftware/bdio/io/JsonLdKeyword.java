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
 * @see <a href="http://www.w3.org/TR/json-ld/#dfn-keyword">Syntax Tokens and Keywords</a>
 */
public enum JsonLdKeyword implements Term {

    /**
     * Used to define the short-hand names that are used throughout a JSON-LD document.
     */
    CONTEXT("@context"),

    /**
     * Used to uniquely identify things that are being described in the document with IRIs or blank node identifiers.
     */
    ID("@id"),

    /**
     * Used to specify the data that is associated with a particular property in the graph.
     */
    VALUE("@value"),

    /**
     * Used to specify the language for a particular string value or the default language of a JSON-LD document.
     */
    LANGUAGE("@language"),

    /**
     * Used to set the data type of a node or typed value.
     */
    TYPE("@type"),

    /**
     * Used to set the default container type for a term.
     */
    CONTAINER("@container"),

    /**
     * Used to express an ordered set of data.
     */
    LIST("@list"),

    /**
     * Used to express an unordered set of data and to ensure that values are always represented as arrays.
     */
    SET("@set"),

    /**
     * Used to express reverse properties.
     */
    REVERSE("@reverse"),

    /**
     * Used to specify that a container is used to index information and that processing should continue deeper into a
     * JSON data structure.
     */
    INDEX("@index"),

    /**
     * Used to set the base IRI against which relative IRIs are resolved.
     */
    BASE("@base"),

    /**
     * Used to expand properties and values in @type with a common prefix IRI.
     */
    VOCAB("@vocab"),

    /**
     * Used to express a graph.
     */
    GRAPH("@graph");

    private final String keyword;

    private JsonLdKeyword(String keyword) {
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
