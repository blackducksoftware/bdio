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
    VULNERABILITY("http://blackducksoftware.com/rdf/terms#Vulnerability"),

    EXTERNAL_IDENTIFIER("http://blackducksoftware.com/rdf/terms#ExternalIdentifier"),
    MATCH_DETAIL("http://blackducksoftware.com/rdf/terms#MatchDetail");

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
