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

public final class Project extends BdioObject {

    public Project(String id) {
        super(id, Bdio.Class.Project);
    }

    public Project subproject(@Nullable Object subproject) {
        putObject(Bdio.ObjectProperty.subproject, subproject);
        return this;
    }

    public Project previousVersion(@Nullable Object previousVersion) {
        putObject(Bdio.ObjectProperty.previousVersion, previousVersion);
        return this;
    }

    public Project base(@Nullable Object base) {
        putObject(Bdio.ObjectProperty.base, base);
        return this;
    }

    public Project name(@Nullable String name) {
        putData(Bdio.DataProperty.name, name);
        return this;
    }

    public Project version(@Nullable String version) {
        putData(Bdio.DataProperty.version, version);
        return this;
    }

    public Project homepage(@Nullable String homepage) {
        putData(Bdio.DataProperty.homepage, homepage);
        return this;
    }

}
