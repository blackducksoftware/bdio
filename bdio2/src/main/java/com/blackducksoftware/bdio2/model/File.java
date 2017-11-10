/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio2.model;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.common.value.ContentType;
import com.blackducksoftware.common.value.Digest;

public final class File extends BdioObject {

    public File(String id) {
        super(id, Bdio.Class.File);
    }

    public File parent(File parent) {
        putObject(Bdio.ObjectProperty.parent, parent);
        return this;
    }

    public File note(Note note) {
        putObject(Bdio.ObjectProperty.note, note);
        return this;
    }

    public File byteCount(@Nullable Long size) {
        putData(Bdio.DataProperty.byteCount, size);
        return this;
    }

    public File fingerprint(@Nullable Digest fingerprint) {
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

    public File encoding(@Nullable String encoding) {
        putData(Bdio.DataProperty.encoding, encoding);
        return this;
    }

    public File path(@Nullable String path) {
        putData(Bdio.DataProperty.path, path);
        return this;
    }

    public File linkPath(@Nullable String linkPath) {
        putData(Bdio.DataProperty.linkPath, linkPath);
        return this;
    }

}
