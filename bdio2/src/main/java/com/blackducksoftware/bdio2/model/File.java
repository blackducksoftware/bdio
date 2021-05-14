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

    public File archiveContext(@Nullable String archiveContext) {
    	 putFieldValue(Bdio.DataProperty.archiveContext, archiveContext);
         return this;
    }
    
    public File description(@Nullable Annotation description) {
        putFieldValue(Bdio.ObjectProperty.description, description);
        return this;
    }

    public File parent(File parent) {
        putFieldValue(Bdio.ObjectProperty.parent, parent);
        return this;
    }

    public File note(Note note) {
        putFieldValue(Bdio.ObjectProperty.note, note);
        return this;
    }

    public File buildOptions(List<String> buildOptions) {
        putFieldValue(Bdio.DataProperty.buildOptions, buildOptions);
        return this;
    }

    public File byteCount(@Nullable Long size) {
        putFieldValue(Bdio.DataProperty.byteCount, size);
        return this;
    }

    public File fingerprint(@Nullable Collection<Digest> fingerprint) {
        putFieldValue(Bdio.DataProperty.fingerprint, fingerprint);
        return this;
    }

    public File fileSystemType(@Nullable String fileSystemType) {
        putFieldValue(Bdio.DataProperty.fileSystemType, fileSystemType);
        return this;
    }

    public File contentType(@Nullable ContentType contentType) {
        putFieldValue(Bdio.DataProperty.contentType, contentType);
        return this;
    }

    public File creationDateTime(@Nullable ZonedDateTime creationDateTime) {
        putFieldValue(Bdio.DataProperty.creationDateTime, creationDateTime);
        return this;
    }

    public File encoding(@Nullable String encoding) {
        putFieldValue(Bdio.DataProperty.encoding, encoding);
        return this;
    }
    
    public File nodeName(String nodeName) {
   	    putFieldValue(Bdio.DataProperty.nodeName, nodeName);
        return this;
    }

    public File path(@Nullable String path) {
        putFieldValue(Bdio.DataProperty.path, path);
        return this;
    }

    public File platform(@Nullable ProductList platform) {
        putFieldValue(Bdio.DataProperty.platform, platform);
        return this;
    }

    public File lastModifiedDateTime(ZonedDateTime lastModifiedDateTime) {
        putFieldValue(Bdio.DataProperty.lastModifiedDateTime, lastModifiedDateTime);
        return this;
    }

    public File linkPath(@Nullable String linkPath) {
        putFieldValue(Bdio.DataProperty.linkPath, linkPath);
        return this;
    }
    
    public File deepDirectoryCount(@Nullable Long count) {
        putFieldValue(Bdio.DataProperty.deepDirectoryCount, count);
        return this;
    }
    
    public File deepFileCount(@Nullable Long count) {
        putFieldValue(Bdio.DataProperty.deepFileCount, count);
        return this;
    }
    
    public File distanceFromRoot(@Nullable Long count) {
        putFieldValue(Bdio.DataProperty.distanceFromRoot, count);
        return this;
    }
    
    public File shallowDirectoryCount(@Nullable Long count) {
        putFieldValue(Bdio.DataProperty.shallowDirectoryCount, count);
        return this;
    }
    
    public File parentId(@Nullable Long count) {
        putFieldValue(Bdio.DataProperty.parentId, count);
        return this;
    }
    
    public File uri(String uri) {
   	    putFieldValue(Bdio.DataProperty.uri, uri);
        return this;
    }
    
}
