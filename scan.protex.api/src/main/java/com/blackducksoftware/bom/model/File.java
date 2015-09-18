/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bom.model;

import javax.annotation.Nullable;

import com.google.common.hash.HashCode;
import com.google.common.net.MediaType;

/**
 * A file in a Bill of Materials.
 *
 * @author jgustie
 */
public class File {

    @Nullable
    private String path;

    @Nullable
    private MediaType type;

    @Nullable
    private Long size;

    @Nullable
    private HashCode sha1;

    @Nullable
    private HashCode md5;

    @Nullable
    private String component;

    @Nullable
    private String license;

    @Nullable
    public String getPath() {
        return path;
    }

    public void setPath(@Nullable String path) {
        this.path = path;
    }

    @Nullable
    public MediaType getType() {
        return type;
    }

    public void setType(@Nullable MediaType type) {
        this.type = type;
    }

    @Nullable
    public Long getSize() {
        return size;
    }

    public void setSize(@Nullable Long size) {
        this.size = size;
    }

    @Nullable
    public HashCode getSha1() {
        return sha1;
    }

    public void setSha1(@Nullable HashCode sha1) {
        this.sha1 = sha1;
    }

    @Nullable
    public HashCode getMd5() {
        return md5;
    }

    public void setMd5(@Nullable HashCode md5) {
        this.md5 = md5;
    }

    @Nullable
    public String getComponent() {
        return component;
    }

    public void setComponent(@Nullable String component) {
        this.component = component;
    }

    @Nullable
    public String getLicense() {
        return license;
    }

    public void setLicense(@Nullable String license) {
        this.license = license;
    }

}
