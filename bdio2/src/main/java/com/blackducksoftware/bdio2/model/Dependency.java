/*
 * Copyright 2017 Black Duck Software, Inc.
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

public final class Dependency extends BdioObject {

    public Dependency() {
        super(Bdio.Class.Dependency);
    }

    public Dependency dependsOn(@Nullable Object component) {
        putFieldValue(Bdio.ObjectProperty.dependsOn, component);
        return this;
    }

    public Dependency declaredBy(@Nullable File file) {
        putFieldValue(Bdio.ObjectProperty.declaredBy, file);
        return this;
    }

    public Dependency description(@Nullable Annotation description) {
        putFieldValue(Bdio.ObjectProperty.description, description);
        return this;
    }
    
    public Dependency dependencyType(@Nullable Object dependencyType) {
        putFieldValue(Bdio.DataProperty.dependencyType, dependencyType);
        return this;
    }
    
    public Dependency evidence(@Nullable File file) {
        putFieldValue(Bdio.ObjectProperty.evidence, file);
        return this;
    }

    public Dependency license(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.license, license);
        return this;
    }

    public Dependency licenseConjunctive(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.licenseConjunctive, license);
        return this;
    }

    public Dependency licenseDisjunctive(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.licenseDisjunctive, license);
        return this;
    }

    public Dependency licenseOrLater(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.licenseOrLater, license);
        return this;
    }

    public Dependency namespace(@Nullable String namespace) {
        putFieldValue(Bdio.DataProperty.namespace, namespace);
        return this;
    }

    public Dependency range(@Nullable String range) {
        putFieldValue(Bdio.DataProperty.range, range);
        return this;
    }

    public Dependency requestedVersion(@Nullable String requestedVersion) {
        putFieldValue(Bdio.DataProperty.requestedVersion, requestedVersion);
        return this;
    }

    public Dependency scope(@Nullable String scope) {
        putFieldValue(Bdio.DataProperty.scope, scope);
        return this;
    }

}
