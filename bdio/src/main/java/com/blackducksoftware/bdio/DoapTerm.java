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
 * Term constants corresponding to the Software Package Data Exchange properties.
 *
 * @author jgustie
 * @see <a href="https://github.com/edumbill/doap/wiki">Description of a Project</a>
 */
public enum DoapTerm implements Term {

    NAME("http://usefulinc.com/ns/doap#name"),
    HOMEPAGE("http://usefulinc.com/ns/doap#homepage"),
    OLD_HOMEPAGE("http://usefulinc.com/ns/doap#old-homepage"),
    CREATED("http://usefulinc.com/ns/doap#created"),
    SHORTDESC("http://usefulinc.com/ns/doap#shortdesc"),
    DESCRIPTION("http://usefulinc.com/ns/doap#description"),
    RELEASE("http://usefulinc.com/ns/doap#release"),
    MAILING_LIST("http://usefulinc.com/ns/doap#mailing-list"),
    CATEGORY("http://usefulinc.com/ns/doap#category"),
    LICENSE("http://usefulinc.com/ns/doap#license"),
    REPOSITORY("http://usefulinc.com/ns/doap#repository"),
    ANON_ROOT("http://usefulinc.com/ns/doap#anon-root"),
    BROWSE("http://usefulinc.com/ns/doap#browse"),
    MODULE("http://usefulinc.com/ns/doap#module"),
    LOCATION("http://usefulinc.com/ns/doap#location"),
    DOWNLOAD_PAGE("http://usefulinc.com/ns/doap#download-page"),
    DOWNLOAD_MIRROR("http://usefulinc.com/ns/doap#download-mirror"),
    REVISION("http://usefulinc.com/ns/doap#revision"),
    FILE_RELEASE("http://usefulinc.com/ns/doap#file-release"),
    WIKI("http://usefulinc.com/ns/doap#wiki"),
    BUG_DATABASE("http://usefulinc.com/ns/doap#bug-database"),
    SCREENSHOTS("http://usefulinc.com/ns/doap#screenshots"),
    MAINTAINER("http://usefulinc.com/ns/doap#maintainer"),
    DEVELOPER("http://usefulinc.com/ns/doap#developer"),
    DOCUMENTER("http://usefulinc.com/ns/doap#documenter"),
    TRANSLATOR("http://usefulinc.com/ns/doap#translator"),
    TESTER("http://usefulinc.com/ns/doap#tester"),
    HELPER("http://usefulinc.com/ns/doap#helper"),
    PROGRAMMING_LANGUAGE("http://usefulinc.com/ns/doap#programming-language"),
    OS("http://usefulinc.com/ns/doap#os"),
    IMPLEMENTS("http://usefulinc.com/ns/doap#implements"),
    SERVICE_ENDPOINT("http://usefulinc.com/ns/doap#service-endpoint"),
    LANGUAGE("http://usefulinc.com/ns/doap#language"),
    VENDOR("http://usefulinc.com/ns/doap#vendor"),
    PLATFORM("http://usefulinc.com/ns/doap#platform"),
    AUDIENCE("http://usefulinc.com/ns/doap#audience"),
    BLOG("http://usefulinc.com/ns/doap#blog");

    private final URI uri;

    private DoapTerm(String fullyQualifiedName) {
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