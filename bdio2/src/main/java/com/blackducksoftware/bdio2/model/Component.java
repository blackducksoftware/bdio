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

public final class Component extends BdioObject {

    public Component(String id) {
        super(id, Bdio.Class.Component);
    }

    public Component name(@Nullable String name) {
        putData(Bdio.DataProperty.name, name);
        return this;
    }

    public Component version(@Nullable String version) {
        putData(Bdio.DataProperty.version, version);
        return this;
    }

    public Component requestedVersion(@Nullable String requestedVersion) {
        putData(Bdio.DataProperty.requestedVersion, requestedVersion);
        return this;
    }

    public Component locator(@Nullable String locator) {
        putData(Bdio.DataProperty.locator, locator);
        return this;
    }

    public Component namespace(@Nullable String namespace) {
        putData(Bdio.DataProperty.namespace, namespace);
        return this;
    }

    public Component context(@Nullable String context) {
        putData(Bdio.DataProperty.context, context);
        return this;
    }

    public Component homepage(@Nullable String homepage) {
        putData(Bdio.DataProperty.homepage, homepage);
        return this;
    }

}
