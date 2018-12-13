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

public final class License extends BdioObject {

    public License(String id) {
        super(id, Bdio.Class.License);
    }

    public License canonical(@Nullable License canonical) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.canonical, canonical);
        return this;
    }

    public License description(@Nullable Annotation description) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.description, description);
        return this;
    }

    public License licenseException(@Nullable License license) {
        BdioContext.getActive().putFieldValue(this, Bdio.ObjectProperty.licenseException, license);
        return this;
    }

    public License name(@Nullable String name) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.name, name);
        return this;
    }

    public License namespace(@Nullable String namespace) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.namespace, namespace);
        return this;
    }

    public License identifier(@Nullable String locator) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.identifier, locator);
        return this;
    }

    public License context(@Nullable String context) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.context, context);
        return this;
    }

    public License resolver(@Nullable ProductList resolver) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.resolver, resolver);
        return this;
    }

    public License homepage(@Nullable String homepage) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.homepage, homepage);
        return this;
    }

}
