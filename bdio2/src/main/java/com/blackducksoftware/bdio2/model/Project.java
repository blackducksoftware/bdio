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
import com.blackducksoftware.common.value.ProductList;

public final class Project extends BdioObject {

    public Project(String id) {
        super(id, Bdio.Class.Project);
    }

    public Project subproject(@Nullable Project subproject) {
        putObject(Bdio.ObjectProperty.subproject, subproject);
        return this;
    }

    public Project previousVersion(@Nullable Project previousVersion) {
        putObject(Bdio.ObjectProperty.previousVersion, previousVersion);
        return this;
    }

    public Project base(@Nullable File base) {
        putObject(Bdio.ObjectProperty.base, base);
        return this;
    }

    public Project dependency(Dependency dependency) {
        putObject(Bdio.ObjectProperty.dependency, dependency);
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

    public Project vendor(@Nullable String vendor) {
        putData(Bdio.DataProperty.vendor, vendor);
        return this;
    }

    public Project license(@Nullable String license) {
        putData(Bdio.DataProperty.license, license);
        return this;
    }

    public Project namespace(@Nullable String namespace) {
        putData(Bdio.DataProperty.namespace, namespace);
        return this;
    }

    public Project identifier(@Nullable String locator) {
        putData(Bdio.DataProperty.identifier, locator);
        return this;
    }

    public Project context(@Nullable String context) {
        putData(Bdio.DataProperty.context, context);
        return this;
    }

    public Project resolver(@Nullable ProductList resolver) {
        putData(Bdio.DataProperty.resolver, resolver);
        return this;
    }

    public Project homepage(@Nullable String homepage) {
        putData(Bdio.DataProperty.homepage, homepage);
        return this;
    }

}
