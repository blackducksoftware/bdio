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
 * Type constants corresponding to the Description of a Project classes.
 *
 * @author jgustie
 * @see <a href="https://github.com/edumbill/doap/wiki">Description of a Project</a>
 */
public enum DoapType implements Type {

    PROJECT("http://usefulinc.com/ns/doap#Project"),
    VERSION("http://usefulinc.com/ns/doap#Version"),
    SPECIFICATION("http://usefulinc.com/ns/doap#Specification"),
    REPOSITORY("http://usefulinc.com/ns/doap#Repository"),
    SVN_REPOSITORY("http://usefulinc.com/ns/doap#SVNRepository"),
    BK_REPOSITORY("http://usefulinc.com/ns/doap#BKRepository"),
    CVS_REPOSITORY("http://usefulinc.com/ns/doap#CVSRepository"),
    ARCH_REPOSITORY("http://usefulinc.com/ns/doap#ArchRepository"),
    BAZAAR_BRANCH("http://usefulinc.com/ns/doap#BazaarBranch"),
    GIT_REPOSITORY("http://usefulinc.com/ns/doap#GitRepository"),
    HG_REPOSITORY("http://usefulinc.com/ns/doap#HgRepository"),
    DARCS_REPOSITORY("http://usefulinc.com/ns/doap#DarcsRepository");

    private final URI uri;

    private DoapType(String fullyQualifiedName) {
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
