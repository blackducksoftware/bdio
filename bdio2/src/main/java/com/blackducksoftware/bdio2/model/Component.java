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

public final class Component extends BdioObject {

    public Component(String id) {
        super(id, Bdio.Class.Component);
    }

    public Component canonical(@Nullable Component canonical) {
        putFieldValue(Bdio.ObjectProperty.canonical, canonical);
        return this;
    }

    public Component dependency(@Nullable Dependency dependency) {
        putFieldValue(Bdio.ObjectProperty.dependency, dependency);
        return this;
    }

    public Component description(@Nullable Annotation description) {
        putFieldValue(Bdio.ObjectProperty.description, description);
        return this;
    }

    public Component license(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.license, license);
        return this;
    }

    public Component licenseConjunctive(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.licenseConjunctive, license);
        return this;
    }

    public Component licenseDisjunctive(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.licenseDisjunctive, license);
        return this;
    }

    public Component licenseOrLater(@Nullable Object license) {
        putFieldValue(Bdio.ObjectProperty.licenseOrLater, license);
        return this;
    }

    public Component name(@Nullable String name) {
        putFieldValue(Bdio.DataProperty.name, name);
        return this;
    }

    public Component version(@Nullable String version) {
        putFieldValue(Bdio.DataProperty.version, version);
        return this;
    }

    public Component vendor(@Nullable String vendor) {
        putFieldValue(Bdio.DataProperty.vendor, vendor);
        return this;
    }

    public Component namespace(@Nullable String namespace) {
        putFieldValue(Bdio.DataProperty.namespace, namespace);
        return this;
    }

    public Component identifier(@Nullable String identifier) {
        putFieldValue(Bdio.DataProperty.identifier, identifier);
        return this;
    }

    public Component context(@Nullable String context) {
        putFieldValue(Bdio.DataProperty.context, context);
        return this;
    }

    public Component resolver(@Nullable ProductList resolver) {
        putFieldValue(Bdio.DataProperty.resolver, resolver);
        return this;
    }

    public Component platform(@Nullable ProductList platform) {
        putFieldValue(Bdio.DataProperty.platform, platform);
        return this;
    }

    public Component homepage(@Nullable String homepage) {
        putFieldValue(Bdio.DataProperty.homepage, homepage);
        return this;
    }

}
