/*
 * Copyright 2015 Black Duck Software, Inc.
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
package com.blackducksoftware.bom.model;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.BlackDuckValue;
import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxValue;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

/**
 * A file in a Bill of Materials.
 *
 * @author jgustie
 */
public class File extends AbstractTopLevelModel<File> {
    private static final ModelField<File, String> PATH = new ModelField<File, String>(SpdxTerm.FILE_NAME) {
        @Override
        protected String get(File file) {
            return file.getPath();
        }

        @Override
        protected void set(File file, Object value) {
            file.setPath(valueToString(value));
        }
    };

    private static final ModelField<File, Set<String>> FILE_TYPES = new ModelField<File, Set<String>>(SpdxTerm.FILE_TYPE) {
        @Override
        protected Set<String> get(File file) {
            return file.getFileTypes();
        }

        @Override
        protected void set(File file, Object value) {
            file.setFileTypes(emptyToNull(valueToStrings(value).toSet()));
        }
    };

    private static final ModelField<File, Long> SIZE = new ModelField<File, Long>(BlackDuckTerm.SIZE) {
        @Override
        protected Long get(File file) {
            return file.getSize();
        }

        @Override
        protected void set(File file, Object value) {
            file.setSize(valueToLong(value));
        }
    };

    private static final ModelField<File, List<Checksum>> CHECKSUMS = new ModelField<File, List<Checksum>>(SpdxTerm.CHECKSUM) {
        @Override
        protected List<Checksum> get(File file) {
            return file.getChecksums();
        }

        @Override
        protected void set(File file, Object value) {
            file.setChecksums(emptyToNull(valueToNodes(value).transformAndConcat(toModel(Checksum.class)).toList()));
        }
    };

    private static final ModelField<File, String> COMPONENT = new ModelField<File, String>(SpdxTerm.ARTIFACT_OF) {
        @Override
        protected String get(File file) {
            return file.getComponent();
        }

        @Override
        protected void set(File file, Object value) {
            file.setComponent(valueToString(value));
        }
    };

    private static final ModelField<File, String> LICENSE = new ModelField<File, String>(SpdxTerm.LICENSE_CONCLUDED) {
        @Override
        protected String get(File file) {
            return file.getLicense();
        }

        @Override
        protected void set(File file, Object value) {
            file.setLicense(valueToString(value));
        }
    };

    private static final ModelField<File, List<MatchDetail>> MATCH_DETAILS = new ModelField<File, List<MatchDetail>>(BlackDuckTerm.MATCH_DETAIL) {
        @Override
        protected List<MatchDetail> get(File file) {
            return file.getMatchDetails();
        }

        @Override
        protected void set(File file, Object value) {
            file.setMatchDetails(valueToNodes(value).transformAndConcat(toModel(MatchDetail.class)).toList());
        }
    };

    /**
     * The path of the file. Should always start with "./" relative to some base path.
     */
    @Nullable
    private String path;

    /**
     * The file types of this file. Corresponds to the SPDX types plus "DIRECTORY".
     */
    @Nullable
    private Set<String> fileTypes;

    /**
     * The size of this file in bytes.
     */
    @Nullable
    private Long size;

    /**
     * The list of checksums for this file.
     */
    @Nullable
    private List<Checksum> checksums;

    /**
     * The component this file belongs to.
     */
    @Nullable
    private String component;

    /**
     * The concluded license of this file. May be the same or different from the component license.
     */
    @Nullable
    private String license;

    @Nullable
    private List<MatchDetail> matchDetails;

    public File() {
        super(BlackDuckType.FILE,
                ImmutableSet.<ModelField<File, ?>> builder()
                        .add(PATH).add(FILE_TYPES).add(SIZE).add(CHECKSUMS).add(COMPONENT).add(LICENSE).add(MATCH_DETAILS)
                        .build());
    }

    @Nullable
    public String getPath() {
        return path;
    }

    public void setPath(@Nullable String path) {
        this.path = path;
    }

    @Nullable
    public Set<String> getFileTypes() {
        return fileTypes;
    }

    public void setFileTypes(@Nullable Set<String> fileTypes) {
        this.fileTypes = fileTypes;
    }

    public boolean isFileTypeDirectory() {
        Set<String> fileType = getFileTypes();
        return fileType != null && fileType.contains(BlackDuckValue.FILE_TYPE_DIRECTORY.id());
    }

    public boolean isFileTypeArchive() {
        Set<String> fileType = getFileTypes();
        return fileType != null && fileType.contains(SpdxValue.FILE_TYPE_ARCHIVE.id());
    }

    @Nullable
    public Long getSize() {
        return size;
    }

    public void setSize(@Nullable Long size) {
        this.size = size;
    }

    @Nullable
    public List<Checksum> getChecksums() {
        return checksums;
    }

    public void setChecksums(@Nullable List<Checksum> checksums) {
        this.checksums = checksums;
    }

    public File addChecksum(Checksum checksum) {
        return safeAddArrayList(CHECKSUMS, checksum);
    }

    public FluentIterable<Checksum> checksums() {
        return safeGet(CHECKSUMS);
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

    @Nullable
    public List<MatchDetail> getMatchDetails() {
        return matchDetails;
    }

    public void setMatchDetails(@Nullable List<MatchDetail> matchDetails) {
        this.matchDetails = matchDetails;
    }

    public File addMatchDetail(MatchDetail matchDetail) {
        return safeAddArrayList(MATCH_DETAILS, matchDetail);
    }

    public FluentIterable<MatchDetail> matchDetails() {
        return safeGet(MATCH_DETAILS);
    }

}
