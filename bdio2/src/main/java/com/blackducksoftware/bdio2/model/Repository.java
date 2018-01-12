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

public final class Repository extends BdioObject {

    public Repository(String id) {
        super(id, Bdio.Class.Repository);
    }

    public Repository canonical(@Nullable Repository canonical) {
        putObject(Bdio.ObjectProperty.canonical, canonical);
        return this;
    }

    public Repository description(@Nullable Annotation description) {
        putObject(Bdio.ObjectProperty.description, description);
        return this;
    }

    public Repository name(@Nullable String name) {
        putData(Bdio.DataProperty.name, name);
        return this;
    }

    public Repository base(@Nullable File base) {
        putObject(Bdio.ObjectProperty.base, base);
        return this;
    }

    public Repository dependency(Dependency dependency) {
        putObject(Bdio.ObjectProperty.dependency, dependency);
        return this;
    }

    public Repository namespace(@Nullable String namespace) {
        putData(Bdio.DataProperty.namespace, namespace);
        return this;
    }

}
