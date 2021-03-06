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
import com.blackducksoftware.common.value.ProductList;

public final class Container extends BdioObject {

    public Container(String id) {
        super(id, Bdio.Class.Container);
    }

    public Container base(@Nullable File base) {
        putFieldValue(Bdio.ObjectProperty.base, base);
        return this;
    }

    public Container dependency(Dependency dependency) {
        putFieldValue(Bdio.ObjectProperty.dependency, dependency);
        return this;
    }

    public Container description(@Nullable Annotation description) {
        putFieldValue(Bdio.ObjectProperty.description, description);
        return this;
    }

    public Container license(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.license, license);
        return this;
    }

    public Container licenseConjunctive(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.licenseConjunctive, license);
        return this;
    }

    public Container licenseDisjunctive(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.licenseDisjunctive, license);
        return this;
    }

    public Container licenseOrLater(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.licenseOrLater, license);
        return this;
    }

    public Container namespace(@Nullable String namespace) {
        putFieldValue(Bdio.DataProperty.namespace, namespace);
        return this;
    }

    public Container platform(@Nullable ProductList platform) {
        putFieldValue(Bdio.DataProperty.platform, platform);
        return this;
    }

}
