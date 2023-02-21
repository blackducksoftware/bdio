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

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * This class holds data deserialized from bdio entry in protobuf format
 *
 * @author sharapov
 *
 */
public class BdioChunk {

    private final Set<BdioFileNode> fileNodes;

    private final Set<BdioDependencyNode> dependencyNodes;

    private final Set<BdioComponentNode> componentNodes;

    private final Set<BdioAnnotationNode> annotationNodes;

    private final Set<BdioContainerNode> containerNodes;

    private final Set<BdioContainerLayerNode> containerLayerNodes;

    private final Set<BdioBdbaFileNode> bdbaFileNodes;

    public BdioChunk(Set<BdioFileNode> fileNodes) {
        this.fileNodes = ImmutableSet.copyOf(Objects.requireNonNull(fileNodes));
        this.dependencyNodes = Collections.emptySet();
        this.componentNodes = Collections.emptySet();
        this.annotationNodes = Collections.emptySet();
        this.containerNodes = Collections.emptySet();
        this.containerLayerNodes = Collections.emptySet();
        this.bdbaFileNodes = Collections.emptySet();
    }

    public BdioChunk(Set<BdioFileNode> fileNodes, Set<BdioDependencyNode> dependencyNodes,
            Set<BdioComponentNode> componentNodes, Set<BdioAnnotationNode> annotationNodes,
            Set<BdioContainerNode> containerNodes, Set<BdioContainerLayerNode> containerLayerNodes,
            Set<BdioBdbaFileNode> bdbaFileNodes) {
        this.fileNodes = ImmutableSet.copyOf(Objects.requireNonNull(fileNodes));
        this.dependencyNodes = ImmutableSet.copyOf(Objects.requireNonNull(dependencyNodes));
        this.componentNodes = ImmutableSet.copyOf(Objects.requireNonNull(componentNodes));
        this.annotationNodes = ImmutableSet.copyOf(Objects.requireNonNull(annotationNodes));
        this.containerNodes = ImmutableSet.copyOf(Objects.requireNonNull(containerNodes));
        this.containerLayerNodes = ImmutableSet.copyOf(Objects.requireNonNull(containerLayerNodes));
        this.bdbaFileNodes = ImmutableSet.copyOf(Objects.requireNonNull(bdbaFileNodes));
    }

    public Set<BdioFileNode> getFileNodes() {
        return fileNodes;
    }

    public Set<BdioDependencyNode> getDependencyNodes() {
        return dependencyNodes;
    }

    public Set<BdioComponentNode> getComponentNodes() {
        return componentNodes;
    }

    public Set<BdioAnnotationNode> getAnnotationNodes() {
        return annotationNodes;
    }

    public Set<BdioContainerNode> getContainerNodes() {
        return containerNodes;
    }

    public Set<BdioContainerLayerNode> getContainerLayerNodes() {
        return containerLayerNodes;
    }

    public Set<BdioBdbaFileNode> getBdbaFileNodes() {
        return bdbaFileNodes;
    }

}
