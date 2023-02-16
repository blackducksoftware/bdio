/*
 * Copyright (C) 2023 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio.proto.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;

/**
 *
 * @author sharapov
 *
 */
public class BdioBdbaFileNode implements IBdioNode {

    private final String id;

    private final String uri;

    private final long size;

    private final Instant lastModifiedDateTime;

    private final String fileSystemType;

    private final Map<String, String> signatures;

    public BdioBdbaFileNode(String id, String uri, long size, Instant lastModifiedDateTime, String fileSystemType,
            Map<String, String> signatures) {
        this.id = id;
        this.uri = uri;
        this.size = size;
        this.lastModifiedDateTime = lastModifiedDateTime;
        this.fileSystemType = fileSystemType;
        this.signatures = (signatures == null) ? ImmutableMap.of() : ImmutableMap.copyOf(signatures);
    }

    public String getId() {
        return id;
    }

    public String getUri() {
        return uri;
    }

    public long getSize() {
        return size;
    }

    public Instant getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public Optional<String> getFileSystemType() {
        return Optional.ofNullable(fileSystemType);
    }

    public Map<String, String> getSignatures() {
        return signatures;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getUri(), getSize(), getLastModifiedDateTime(),
                getFileSystemType(), getSignatures());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        } else if (other instanceof BdioBdbaFileNode) {
            BdioBdbaFileNode bdbaFileNode = (BdioBdbaFileNode) other;
            return Objects.equals(getId(), bdbaFileNode.getId())
                    && Objects.equals(getUri(), bdbaFileNode.getUri())
                    && Objects.equals(getSize(), bdbaFileNode.getSize())
                    && Objects.equals(getLastModifiedDateTime(), bdbaFileNode.getLastModifiedDateTime())
                    && Objects.equals(getFileSystemType(), bdbaFileNode.getFileSystemType())
                    && Objects.equals(getSignatures(), bdbaFileNode.getSignatures());
        }

        return false;
    }

}
