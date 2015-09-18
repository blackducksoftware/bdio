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

    private final String fullyQualifiedName;

    private DoapType(String fullyQualifiedName) {
        this.fullyQualifiedName = checkNotNull(fullyQualifiedName);
    }

    @Override
    public String toString() {
        return fullyQualifiedName;
    }
}
