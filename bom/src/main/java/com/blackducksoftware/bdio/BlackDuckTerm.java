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
package com.blackducksoftware.bdio;

import java.net.URI;

/**
 * Term constants corresponding to Bill of Materials properties.
 *
 * @author jgustie
 */
public enum BlackDuckTerm implements Term {

    SPEC_VERSION("http://blackducksoftware.com/rdf/terms#specVersion"),

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
