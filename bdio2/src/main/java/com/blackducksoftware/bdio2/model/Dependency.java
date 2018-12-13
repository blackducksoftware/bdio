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
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioObject;

public final class Dependency extends BdioObject {

    public Dependency() {
        super(Bdio.Class.Dependency);
    }

    public Dependency dependsOn(@Nullable Object component) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.dependsOn, component);
        return this;
    }

    public Dependency declaredBy(@Nullable File file) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.declaredBy, file);
        return this;
    }

    public Dependency description(@Nullable Annotation description) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.description, description);
        return this;
    }

    public Dependency evidence(@Nullable File file) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.evidence, file);
        return this;
    }

    public Dependency license(@Nullable Object license) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.license, license);
        return this;
    }

    public Dependency licenseConjunctive(@Nullable Object license) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.licenseConjunctive, license);
        return this;
    }

    public Dependency licenseDisjunctive(@Nullable Object license) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.licenseDisjunctive, license);
        return this;
    }

    public Dependency licenseOrLater(@Nullable Object license) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.licenseOrLater, license);
        return this;
    }

    public Dependency namespace(@Nullable String namespace) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.namespace, namespace);
        return this;
    }

    public Dependency range(@Nullable String range) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.range, range);
        return this;
    }

    public Dependency requestedVersion(@Nullable String requestedVersion) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.requestedVersion, requestedVersion);
        return this;
    }

    public Dependency scope(@Nullable String scope) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.scope, scope);
        return this;
    }

}
