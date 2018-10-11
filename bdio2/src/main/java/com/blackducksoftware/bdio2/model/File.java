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

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.common.value.ContentType;
import com.blackducksoftware.common.value.Digest;
import com.blackducksoftware.common.value.ProductList;

public final class File extends BdioObject {

    public File(String id) {
        super(id, Bdio.Class.File);
    }

    public File description(@Nullable Annotation description) {
        putObject(Bdio.ObjectProperty.description, description);
        return this;
    }

    public File parent(File parent) {
        putObject(Bdio.ObjectProperty.parent, parent);
        return this;
    }

    public File note(Note note) {
        putObject(Bdio.ObjectProperty.note, note);
        return this;
    }

    public File buildOptions(List<String> buildOptions) {
        putData(Bdio.DataProperty.buildOptions, buildOptions);
        return this;
    }

    public File byteCount(@Nullable Long size) {
        putData(Bdio.DataProperty.byteCount, size);
        return this;
    }

    public File fingerprint(@Nullable Collection<Digest> fingerprint) {
        putData(Bdio.DataProperty.fingerprint, fingerprint);
        return this;
    }

    public File fileSystemType(@Nullable String fileSystemType) {
        putData(Bdio.DataProperty.fileSystemType, fileSystemType);
        return this;
    }

    public File contentType(@Nullable ContentType contentType) {
        putData(Bdio.DataProperty.contentType, contentType);
        return this;
    }

    public File creationDateTime(@Nullable ZonedDateTime creationDateTime) {
        putData(Bdio.DataProperty.creationDateTime, creationDateTime);
        return this;
    }

    public File encoding(@Nullable String encoding) {
        putData(Bdio.DataProperty.encoding, encoding);
        return this;
    }

    public File path(@Nullable String path) {
        putData(Bdio.DataProperty.path, path);
        return this;
    }

    public File platform(@Nullable ProductList platform) {
        putData(Bdio.DataProperty.platform, platform);
        return this;
    }

    public File lastModifiedDateTime(ZonedDateTime lastModifiedDateTime) {
        putData(Bdio.DataProperty.lastModifiedDateTime, lastModifiedDateTime);
        return this;
    }

    public File linkPath(@Nullable String linkPath) {
        putData(Bdio.DataProperty.linkPath, linkPath);
        return this;
    }

}
