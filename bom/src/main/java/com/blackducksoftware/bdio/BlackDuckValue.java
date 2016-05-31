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

    FILE_TYPE_DIRECTORY("http://blackducksoftware.com/rdf/terms#fileType_directory"),

    EXTERNAL_IDENTIFIER_ANACONDA("http://blackducksoftware.com/rdf/terms#externalIdentifier_anaconda"),
    // EXTERNAL_IDENTIFIER_BIICODE("http://blackducksoftware.com/rdf/terms#externalIdentifier_biicode"),
    EXTERNAL_IDENTIFIER_BOWER("http://blackducksoftware.com/rdf/terms#externalIdentifier_bower"),
    // EXTERNAL_IDENTIFIER_CARGO("http://blackducksoftware.com/rdf/terms#externalIdentifier_cargo"),
    // EXTERNAL_IDENTIFIER_CENTOS("http://blackducksoftware.com/rdf/terms#externalIdentifier_centos"),
    // EXTERNAL_IDENTIFIER_COCOAPODS("http://blackducksoftware.com/rdf/terms#externalIdentifier_cocoapods"),
    // EXTERNAL_IDENTIFIER_CODEPLEX("http://blackducksoftware.com/rdf/terms#externalIdentifier_codeplex"),
    // EXTERNAL_IDENTIFIER_COMPOSER("http://blackducksoftware.com/rdf/terms#externalIdentifier_composer"),
    EXTERNAL_IDENTIFIER_CPAN("http://blackducksoftware.com/rdf/terms#externalIdentifier_cpan"),
    // EXTERNAL_IDENTIFIER_CRAN("http://blackducksoftware.com/rdf/terms#externalIdentifier_cran"),
    // EXTERNAL_IDENTIFIER_CTAN("http://blackducksoftware.com/rdf/terms#externalIdentifier_ctan"),
    // EXTERNAL_IDENTIFIER_DEBIAN("http://blackducksoftware.com/rdf/terms#externalIdentifier_debian"),
    // EXTERNAL_IDENTIFIER_DUB("http://blackducksoftware.com/rdf/terms#externalIdentifier_dub"),
    // EXTERNAL_IDENTIFIER_FEDORA("http://blackducksoftware.com/rdf/terms#externalIdentifier_fedora"),
    // EXTERNAL_IDENTIFIER_GITHUB("http://blackducksoftware.com/rdf/terms#externalIdentifier_github"),
    EXTERNAL_IDENTIFIER_GOGET("http://blackducksoftware.com/rdf/terms#externalIdentifier_goget"),
    // EXTERNAL_IDENTIFIER_GOOGLECODE("http://blackducksoftware.com/rdf/terms#externalIdentifier_googlecode"),
    // EXTERNAL_IDENTIFIER_HACKAGE("http://blackducksoftware.com/rdf/terms#externalIdentifier_hackage"),
    // EXTERNAL_IDENTIFIER_LUAROCKS("http://blackducksoftware.com/rdf/terms#externalIdentifier_luarocks"),
    EXTERNAL_IDENTIFIER_MAVEN("http://blackducksoftware.com/rdf/terms#externalIdentifier_maven"),
    EXTERNAL_IDENTIFIER_NPM("http://blackducksoftware.com/rdf/terms#externalIdentifier_npm"),
    EXTERNAL_IDENTIFIER_NUGET("http://blackducksoftware.com/rdf/terms#externalIdentifier_nuget"),
    // EXTERNAL_IDENTIFIER_OPAM("http://blackducksoftware.com/rdf/terms#externalIdentifier_opam"),
    // EXTERNAL_IDENTIFIER_PEAR("http://blackducksoftware.com/rdf/terms#externalIdentifier_pear"),
    // EXTERNAL_IDENTIFIER_PIP("http://blackducksoftware.com/rdf/terms#externalIdentifier_pip"),
    // EXTERNAL_IDENTIFIER_QUICKLISP("http://blackducksoftware.com/rdf/terms#externalIdentifier_quicklisp"),
    // EXTERNAL_IDENTIFIER_RHEL("http://blackducksoftware.com/rdf/terms#externalIdentifier_rhel"),
    EXTERNAL_IDENTIFIER_RUBYGEMS("http://blackducksoftware.com/rdf/terms#externalIdentifier_rubygems"),
    // EXTERNAL_IDENTIFIER_SBT("http://blackducksoftware.com/rdf/terms#externalIdentifier_sbt"),
    // EXTERNAL_IDENTIFIER_SOURCEFORGE("http://blackducksoftware.com/rdf/terms#externalIdentifier_sourceforge"),
    // EXTERNAL_IDENTIFIER_LEININGEN("http://blackducksoftware.com/rdf/terms#externalIdentifier_leiningen"),
    // EXTERNAL_IDENTIFIER_UBUNTU("http://blackducksoftware.com/rdf/terms#externalIdentifier_ubuntu"),
    // EXTERNAL_IDENTIFIER_CPE("http://blackducksoftware.com/rdf/terms#externalIdentifier_cpe"),
    // EXTERNAL_IDENTIFIER_SWID("http://blackducksoftware.com/rdf/terms#externalIdentifier_swid"),
    EXTERNAL_IDENTIFIER_BDSUITE("http://blackducksoftware.com/rdf/terms#externalIdentifier_bdsuite"),
    EXTERNAL_IDENTIFIER_BDHUB("http://blackducksoftware.com/rdf/terms#externalIdentifier_bdhub"),
    EXTERNAL_IDENTIFIER_OPENHUB("http://blackducksoftware.com/rdf/terms#externalIdentifier_openhub"),
    // EXTERNAL_IDENTIFIER_PALAMIDADB("http://blackducksoftware.com/rdf/terms#externalIdentifier_palamidadb"),
    // EXTERNAL_IDENTIFIER_NEXB("http://blackducksoftware.com/rdf/terms#externalIdentifier_nexb"),

    MATCH_TYPE_DEPENDENCY("http://blackducksoftware.com/rdf/terms#matchType_dependency"),
    MATCH_TYPE_PARTIAL("http://blackducksoftware.com/rdf/terms#matchType_partial");

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
