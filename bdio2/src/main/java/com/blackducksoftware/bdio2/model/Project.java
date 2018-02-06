/*
 * Copyright 2016 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2.model;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.common.value.ProductList;

public final class Project extends BdioObject {

    public Project(String id) {
        super(id, Bdio.Class.Project);
    }

    public Project description(@Nullable Annotation description) {
        putObject(Bdio.ObjectProperty.description, description);
        return this;
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

    public Project license(@Nullable Object license) {
        putObject(Bdio.ObjectProperty.license, license);
        return this;
    }

    public Project licenseConjunctive(@Nullable Object license) {
        putObject(Bdio.ObjectProperty.licenseConjunctive, license);
        return this;
    }

    public Project licenseDisjunctive(@Nullable Object license) {
        putObject(Bdio.ObjectProperty.licenseDisjunctive, license);
        return this;
    }

    public Project licenseOrLater(@Nullable Object license) {
        putObject(Bdio.ObjectProperty.licenseOrLater, license);
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

    public Project platform(@Nullable ProductList platform) {
        putData(Bdio.DataProperty.platform, platform);
        return this;
    }

    public Project homepage(@Nullable String homepage) {
        putData(Bdio.DataProperty.homepage, homepage);
        return this;
    }

}
