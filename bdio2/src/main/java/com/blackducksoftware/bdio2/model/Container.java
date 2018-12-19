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
import com.blackducksoftware.common.value.ProductList;

public final class Container extends BdioObject {

    public Container(String id) {
        super(id, Bdio.Class.Container);
    }

    public Container base(@Nullable File base) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.base, base);
        return this;
    }

    public Container dependency(Dependency dependency) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.dependency, dependency);
        return this;
    }

    public Container description(@Nullable Annotation description) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.description, description);
        return this;
    }

    public Container license(@Nullable Object license) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.license, license);
        return this;
    }

    public Container licenseConjunctive(@Nullable Object license) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.licenseConjunctive, license);
        return this;
    }

    public Container licenseDisjunctive(@Nullable Object license) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.licenseDisjunctive, license);
        return this;
    }

    public Container licenseOrLater(@Nullable Object license) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.licenseOrLater, license);
        return this;
    }

    public Container namespace(@Nullable String namespace) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.namespace, namespace);
        return this;
    }

    public Container platform(@Nullable ProductList platform) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.platform, platform);
        return this;
    }

}
