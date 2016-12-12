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
package com.blackducksoftware.bdio2.model;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;

public class Version extends Project {

    public Version(String id) {
        this(id, Bdio.Class.Version);
    }

    // Pass through constructor for subclasses
    protected Version(String id, Bdio.Class bdioClass) {
        super(id, bdioClass);
    }

    public Version version(@Nullable String version) {
        putData(Bdio.DataProperty.version, version);
        return this;
    }

}
