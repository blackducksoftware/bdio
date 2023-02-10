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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;

/**
 *
 * @author sharapov
 *
 */
public class BdioFileNode implements IBdioNode {

    /**
     * Node id
     */
    private final long id;

    /**
     * Parent node id. A value of -1 signifies no parent.
     */
    private final long parentId;

    /**
     * File or directory name
     */
    private final String name;

    /**
     * File system type
     */
    private final String fileSystemType;

    /**
     * Number of directories that are direct children of this node
     */
    private final Long shallowDirectoryCount;

    /**
     * Number of directories under this specific node
     */
    private final Long deepDirectoryCount;

    /**
     * Number of files under this specific node
     */
    private final Long deepFileCount;

    /**
     * Distance from the source root
     */
    private final Long distanceFromRoot;

    /**
     * Distance from the inner root mostly used for archives
     */
    private final Long distanceFromInnerRoot;

    /**
     * Path for the file or directory
     */
    private final String path;

    /**
     * It is kind of the path but used to represent the path for the archives
     */
    private final String archiveContext;

    /**
     * URI for the file or directory
     */
    private final String uri;

    /**
     * Size of the file or directory
     */
    private final long size;

    /**
     * Map containing different signatures
     */
    private final Map<String, String> signatures;

    public BdioFileNode(long id, long parentId, String name, String fileSystemType, Long shallowDirectoryCount,
            Long deepDirectoryCount, Long deepFileCount, Long distanceFromRoot, Long distanceFromInnerRoot, String path,
            String archiveContext, String uri, Long size, Map<String, String> signatures) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.fileSystemType = fileSystemType;
        this.shallowDirectoryCount = shallowDirectoryCount;
        this.deepDirectoryCount = deepDirectoryCount;
        this.deepFileCount = deepFileCount;
        this.distanceFromRoot = distanceFromRoot;
        this.distanceFromInnerRoot = distanceFromInnerRoot;
        this.path = path;
        this.archiveContext = archiveContext;
        this.uri = uri;
        this.size = size;
        this.signatures = signatures;
    }

    public long getId() {
        return id;
    }

    /**
     * Returns the parentId or -1 if there is no parent.
     *
     */
    public long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public String getFileSystemType() {
        return fileSystemType;
    }

    public Optional<Long> getShallowDirectoryCount() {
        return Optional.ofNullable(shallowDirectoryCount);
    }

    public Optional<Long> getDeepDirectoryCount() {
        return Optional.ofNullable(deepDirectoryCount);
    }

    public Optional<Long> getDeepFileCount() {
        return Optional.ofNullable(deepFileCount);
    }

    public Optional<Long> getDistanceFromRoot() {
        return Optional.ofNullable(distanceFromRoot);
    }

    public Optional<Long> getDistanceFromInnerRoot() {
        return Optional.ofNullable(distanceFromInnerRoot);
    }

    public String getPath() {
        return path;
    }

    public Optional<String> getArchiveContext() {
        return Optional.ofNullable(archiveContext);
    }

    public String getUri() {
        return uri;
    }

    public long getSize() {
        return size;
    }

    public Map<String, String> getSignatures() {
        return signatures;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BdioFileNode fileNode = (BdioFileNode) o;
        return Objects.equals(id, fileNode.id) && Objects.equals(parentId, fileNode.parentId)
                && Objects.equals(name, fileNode.name) && Objects.equals(fileSystemType, fileNode.fileSystemType)
                && Objects.equals(shallowDirectoryCount, fileNode.shallowDirectoryCount)
                && Objects.equals(deepDirectoryCount, fileNode.deepDirectoryCount)
                && Objects.equals(deepFileCount, fileNode.deepFileCount)
                && Objects.equals(distanceFromRoot, fileNode.distanceFromRoot)
                && Objects.equals(distanceFromInnerRoot, fileNode.distanceFromInnerRoot)
                && Objects.equals(path, fileNode.path) && Objects.equals(archiveContext, fileNode.archiveContext)
                && Objects.equals(uri, fileNode.uri) && Objects.equals(size, fileNode.size)
                && Objects.equals(signatures, fileNode.signatures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, parentId, name, fileSystemType, shallowDirectoryCount, shallowDirectoryCount,
                deepDirectoryCount, deepFileCount, distanceFromRoot, distanceFromInnerRoot, path, archiveContext, uri,
                size, signatures);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).omitNullValues().add("id", id).add("parentId", parentId)
                .add("name", name).add("fileSystemType", fileSystemType)
                .add("shallowDirectoryCount", shallowDirectoryCount).add("deepDirectoryCount", deepDirectoryCount)
                .add("deepFileCount", deepFileCount).add("distanceFromRoot", distanceFromRoot)
                .add("distanceFromInnerRoot", distanceFromInnerRoot).add("path", path)
                .add("archiveContext", archiveContext).add("uri", uri).add("size", size).add("signatures", signatures)
                .toString();
    }

}
