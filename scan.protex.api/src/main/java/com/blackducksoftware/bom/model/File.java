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

import static com.google.common.base.Objects.firstNonNull;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.BlackDuckValue;
import com.blackducksoftware.bom.SpdxTerm;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

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
     * The file types of this file. Corresponds to the SPDX types plus "DIRECTORY".
     */
    @Nullable
    private Set<String> fileType;

    private static final ModelField<File> FILE_TYPE = new ModelField<File>(SpdxTerm.FILE_TYPE) {
        @Override
        protected Object get(File file) {
            return file.getFileType();
        }

        @Override
        protected void set(File file, Object value) {
            file.setFileType(emptyToNull(valueToStrings(value).toSet()));
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
     * The list of checksums for this file.
     */
    @Nullable
    private List<Checksum> checksum;

    private static final ModelField<File> CHECKSUM = new ModelField<File>(SpdxTerm.CHECKSUM) {
        @Override
        protected Object get(File file) {
            return file.getChecksum();
        }

        @Override
        protected void set(File file, Object value) {
            file.setChecksum(emptyToNull(valueToNodes(value).transformAndConcat(toModel(Checksum.class)).toList()));
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
                PATH, FILE_TYPE, SIZE, CHECKSUM, COMPONENT, LICENSE);
    }

    @Nullable
    public String getPath() {
        return path;
    }

    public void setPath(@Nullable String path) {
        this.path = path;
    }

    @Nullable
    public Set<String> getFileType() {
        return fileType;
    }

    public void setFileType(@Nullable Set<String> fileType) {
        this.fileType = fileType;
    }

    public boolean isFileTypeDirectory() {
        Set<String> fileType = getFileType();
        return fileType != null && fileType.contains(BlackDuckValue.FILE_TYPE_DIRECTORY.id());
    }

    @Nullable
    public Long getSize() {
        return size;
    }

    public void setSize(@Nullable Long size) {
        this.size = size;
    }

    @Nullable
    public List<Checksum> getChecksum() {
        return checksum;
    }

    public void setChecksum(@Nullable List<Checksum> checksum) {
        this.checksum = checksum;
    }

    public File addChecksum(Checksum checksum) {
        if (checksum != null) {
            List<Checksum> checksums = getChecksum();
            if (checksums != null) {
                checksums.add(checksum);
            } else {
                setChecksum(Lists.newArrayList(checksum));
            }
        }
        return this;
    }

    public FluentIterable<Checksum> checksums() {
        return FluentIterable.from(firstNonNull(getChecksum(), ImmutableList.<Checksum> of()));
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
