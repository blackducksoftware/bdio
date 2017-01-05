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
import com.blackducksoftware.bdio2.BdioObject;

public class Project extends BdioObject {

    public Project(String id) {
        this(id, Bdio.Class.Project);
    }

    // Pass through constructor for subclasses
    protected Project(String id, Bdio.Class bdioClass) {
        super(id, bdioClass);
    }

    public Project name(@Nullable String name) {
        putData(Bdio.DataProperty.name, name);
        return this;
    }

    public Project currentVersion(@Nullable Object currentVersion) {
        putObject(Bdio.ObjectProperty.currentVersion, currentVersion);
        return this;
    }

    public Project base(@Nullable Object base) {
        putObject(Bdio.ObjectProperty.base, base);
        return this;
    }
}
