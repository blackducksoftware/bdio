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

import java.util.Set;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.SpdxTerm;
import com.google.common.hash.HashCode;
import com.google.common.net.MediaType;

/**
 * A file in a Bill of Materials.
 *
 * @author jgustie
 */
public class File extends AbstractModel<File> {

    /**
     * The path of the file. Should always start with "./" relative to some base path.
     */
    @Nullable
    private String path;

    private static final ModelField<File> PATH = new ModelField<File>(SpdxTerm.FILE_NAME) {
        @Override
        protected Object get(File file) {
            return file.getPath();
        }

        @Override
        protected void set(File file, Object value) {
            file.setPath(valueToString(value));
        }
    };

    /**
     * The media type of the file.
     */
    @Nullable
    private MediaType type;

    private static final ModelField<File> TYPE = new ModelField<File>(BlackDuckTerm.CONTENT_TYPE) {
        @Override
        protected Object get(File file) {
            return file.getType();
        }

        @Override
        protected void set(File file, Object value) {
            file.setType(value != null ? MediaType.parse(valueToString(value)) : null);
        }
    };

    /**
     * The file types of this file. Corresponds to the SPDX types plus "DIRECTORY".
     */
    @Nullable
    private Set<String> fileTypes;

    private static final ModelField<File> FILE_TYPES = new ModelField<File>(SpdxTerm.FILE_TYPE) {
        @Override
        protected Object get(File file) {
            return file.getFileTypes();
        }

        @Override
        protected void set(File file, Object value) {
            file.setFileTypes(valueToStrings(value).toSet());
        }
    };

    /**
     * The size of this file in bytes.
     */
    @Nullable
    private Long size;

    private static final ModelField<File> SIZE = new ModelField<File>(BlackDuckTerm.SIZE) {
        @Override
        protected Object get(File file) {
            return file.getSize();
        }

        @Override
        protected void set(File file, Object value) {
            file.setSize(valueToLong(value));
        }
    };

    /**
     * The SHA-1 hash of this file's contents.
     */
    @Nullable
    private HashCode sha1;

    private static final ModelField<File> SHA1 = new ModelField<File>(BlackDuckTerm.SHA1) {
        @Override
        protected Object get(File file) {
            return file.getSha1();
        }

        @Override
        protected void set(File file, Object value) {
            file.setSha1(value != null ? HashCode.fromString(valueToString(value)) : null);
        }
    };

    /**
     * The MD5 hash of this file's contents.
     */
    @Nullable
    private HashCode md5;

    private static final ModelField<File> MD5 = new ModelField<File>(BlackDuckTerm.MD5) {
        @Override
        protected Object get(File file) {
            return file.getMd5();
        }

        @Override
        protected void set(File file, Object value) {
            file.setMd5(value != null ? HashCode.fromString(valueToString(value)) : null);
        }
    };

    /**
     * The component this file belongs to.
     */
    @Nullable
    private String component;

    private static final ModelField<File> COMPONENT = new ModelField<File>(SpdxTerm.ARTIFACT_OF) {
        @Override
        protected Object get(File file) {
            return file.getComponent();
        }

        @Override
        protected void set(File file, Object value) {
            file.setComponent(valueToString(value));
        }
    };

    /**
     * The concluded license of this file. May be the same or different from the component license.
     */
    @Nullable
    private String license;

    private static final ModelField<File> LICENSE = new ModelField<File>(SpdxTerm.LICENSE_CONCLUDED) {
        @Override
        protected Object get(File file) {
            return file.getLicense();
        }

        @Override
        protected void set(File file, Object value) {
            file.setLicense(valueToString(value));
        }
    };

    public File() {
        super(BlackDuckType.FILE,
                PATH, TYPE, FILE_TYPES, SIZE, SHA1, MD5, COMPONENT, LICENSE);
    }

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
    public Set<String> getFileTypes() {
        return fileTypes;
    }

    public void setFileTypes(@Nullable Set<String> fileTypes) {
        this.fileTypes = fileTypes;
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
