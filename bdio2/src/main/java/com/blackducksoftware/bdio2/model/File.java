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
import com.blackducksoftware.common.value.Digest;

public final class File extends BdioObject {

    public File(String id) {
        super(id, Bdio.Class.File);
    }

    public File byteCount(@Nullable Long size) {
        putData(Bdio.DataProperty.byteCount, size);
        return this;
    }

    public File fingerprint(@Nullable Digest fingerprint) {
        putData(Bdio.DataProperty.fingerprint, fingerprint);
        return this;
    }

    public File contentType(@Nullable String contentType) {
        putData(Bdio.DataProperty.contentType, contentType);
        return this;
    }

    public File path(@Nullable String path) {
        putData(Bdio.DataProperty.path, path);
        return this;
    }

}
