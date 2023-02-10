/*
 * Copyright (C) 2023 Synopsys Inc.
 * http://www.synopsys.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Synopsys ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Synopsys.
 */
package com.blackducksoftware.bdio.proto.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.common.base.MoreObjects;

/**
 * Holds bdio header data
 *
 * @author sharapov
 *
 */
public class BdioHeader implements IBdioNode {

    private short version;

    private final String id;

    private final String scanType;

    private final String codeLocationName;

    private final String projectName;

    private final String versionName;

    private final String publisherName;

    private final String publisherVersion;

    private final String publisherComment;

    private final String creator;

    private final Instant creationTime;

    private final String sourceRepository;

    private final String sourceBranch;

    private final String projectGroupName;

    private final UUID correlationId;

    private final Long matchConfidenceThreshold;

    private final String baseDir;

    private final boolean withStringSearch;

    private final boolean withSnippetMatching;

    private final Boolean retainUnmatchedFiles;

    private final Long fileSystemSizeInBytes;

    public BdioHeader(String id, String scanType, String codeLocationName, String projectName, String versionName,
            String publisherName, String publisherVersion, String publisherComment, String creator,
            Instant creationTime, String sourceRepository, String sourceBranch, String projectGroupName,
            UUID correlationId, Long matchConfidenceThreshold, String baseDir, boolean withStringSearch,
            boolean withSnippetMatching, Boolean retainUnmatchedFiles, Long fileSystemSizeInBytes) {
        this.id = id;
        this.scanType = scanType;
        this.codeLocationName = codeLocationName;
        this.projectName = projectName;
        this.versionName = versionName;
        this.publisherName = publisherName;
        this.publisherVersion = publisherVersion;
        this.publisherComment = publisherComment;
        this.creator = creator;
        this.creationTime = creationTime;
        this.sourceRepository = sourceRepository;
        this.sourceBranch = sourceBranch;
        this.projectGroupName = projectGroupName;
        this.correlationId = correlationId;
        this.matchConfidenceThreshold = matchConfidenceThreshold;
        this.baseDir = baseDir;
        this.withStringSearch = withStringSearch;
        this.withSnippetMatching = withSnippetMatching;
        this.retainUnmatchedFiles = retainUnmatchedFiles;
        this.fileSystemSizeInBytes = fileSystemSizeInBytes;
    }

    public void setVersion(short version) {
        this.version = version;
    }

    public short getVersion() {
        return this.version;
    }

    public String getId() {
        return id;
    }

    public String getScanType() {
        return scanType;
    }

    public String getCodeLocationName() {
        return codeLocationName;
    }

    public Optional<String> getProjectName() {
        return Optional.ofNullable(projectName);
    }

    public Optional<String> getVersionName() {
        return Optional.ofNullable(versionName);
    }

    public String getPublisherName() {
        return publisherName;
    }

    public String getPublisherVersion() {
        return publisherVersion;
    }

    public String getPublisherComment() {
        return publisherComment;
    }

    public String getCreator() {
        return creator;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public Optional<String> getSourceRepository() {
        return Optional.ofNullable(sourceRepository);
    }

    public Optional<String> getSourceBranch() {
        return Optional.ofNullable(sourceBranch);
    }

    public Optional<String> getProjectGroupName() {
        return Optional.ofNullable(projectGroupName);
    }

    public Optional<UUID> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    @Nullable
    public Optional<Long> getMatchConfidenceThreshold() {
        return Optional.ofNullable(matchConfidenceThreshold);
    }

    public String getBaseDir() {
        return baseDir;
    }

    public boolean isWithStringSearch() {
        return withStringSearch;
    }

    public boolean isWithSnippetMatching() {
        return withSnippetMatching;
    }

    public Optional<Boolean> isRetainUnmatchedFiles() {
        return Optional.ofNullable(retainUnmatchedFiles);
    }

    public Optional<Long> getFileSystemSizeInBytes() {
        return Optional.ofNullable(fileSystemSizeInBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BdioHeader that = (BdioHeader) o;
        return withStringSearch == that.withStringSearch && withSnippetMatching == that.withSnippetMatching
                && Objects.equals(id, that.id) && scanType == that.scanType
                && Objects.equals(codeLocationName, that.codeLocationName)
                && Objects.equals(projectName, that.projectName) && Objects.equals(versionName, that.versionName)
                && Objects.equals(publisherName, that.publisherName)
                && Objects.equals(publisherVersion, that.publisherVersion)
                && Objects.equals(publisherComment, that.publisherComment) && Objects.equals(creator, that.creator)
                && Objects.equals(creationTime, that.creationTime)
                && Objects.equals(sourceRepository, that.sourceRepository)
                && Objects.equals(sourceBranch, that.sourceBranch)
                && Objects.equals(projectGroupName, that.projectGroupName)
                && Objects.equals(correlationId, that.correlationId)
                && Objects.equals(matchConfidenceThreshold, that.matchConfidenceThreshold)
                && Objects.equals(baseDir, that.baseDir)
                && Objects.equals(retainUnmatchedFiles, that.retainUnmatchedFiles)
                && Objects.equals(fileSystemSizeInBytes, that.fileSystemSizeInBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, scanType, codeLocationName, projectName, versionName, publisherName, publisherVersion,
                publisherComment, creator, creationTime, sourceRepository, sourceBranch, projectGroupName,
                correlationId, matchConfidenceThreshold, baseDir, withStringSearch, withSnippetMatching,
                retainUnmatchedFiles, fileSystemSizeInBytes);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("scanType", scanType)
                .add("codeLocationName", codeLocationName)
                .add("projectName", projectName)
                .add("versionName", versionName)
                .add("publisherName", publisherName)
                .add("publisherVersion", publisherVersion)
                .add("publisherComment", publisherComment)
                .add("creator", creator)
                .add("creationTime", creationTime)
                .add("sourceRepository", sourceRepository)
                .add("sourceBranch", sourceBranch)
                .add("projectGroupName", projectGroupName)
                .add("correlationId", correlationId)
                .add("matchConfidenceThreshold", matchConfidenceThreshold)
                .add("baseDir", baseDir)
                .add("withStringSearch", withStringSearch)
                .add("withSnippetMatching", withSnippetMatching)
                .add("isRetainUnmatchedFiles", retainUnmatchedFiles)
                .add("fileSystemSizeInBytes", fileSystemSizeInBytes)
                .toString();
    }
}
