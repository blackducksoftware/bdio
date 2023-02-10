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
package com.blackducksoftware.bdio.proto.domain;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * Holds data deserialized from bdio archive entry
 *
 * @author sharapov
 *
 */
public class ProtoChunk {

    private final Set<ProtoFileNode> fileNodes;

    private final Set<ProtoDependencyNode> dependencyNodes;

    private final Set<ProtoComponentNode> componentNodes;

    private final Set<ProtoAnnotationNode> annotationNodes;

    private final Set<ProtoContainerNode> containerNodes;

    private final Set<ProtoContainerLayerNode> containerLayerNodes;

    public ProtoChunk(Set<ProtoFileNode> fileNodes) {
        this.fileNodes = ImmutableSet.copyOf(Objects.requireNonNull(fileNodes));
        this.dependencyNodes = Collections.emptySet();
        this.componentNodes = Collections.emptySet();
        this.annotationNodes = Collections.emptySet();
        this.containerNodes = Collections.emptySet();
        this.containerLayerNodes = Collections.emptySet();
    }

    public ProtoChunk(Set<ProtoFileNode> fileNodes, Set<ProtoDependencyNode> dependencyNodes,
            Set<ProtoComponentNode> componentNodes, Set<ProtoAnnotationNode> annotationNodes,
            Set<ProtoContainerNode> imageNodes, Set<ProtoContainerLayerNode> imageLayerNodes) {
        this.fileNodes = ImmutableSet.copyOf(Objects.requireNonNull(fileNodes));
        this.dependencyNodes = ImmutableSet.copyOf(Objects.requireNonNull(dependencyNodes));
        this.componentNodes = ImmutableSet.copyOf(Objects.requireNonNull(componentNodes));
        this.annotationNodes = ImmutableSet.copyOf(Objects.requireNonNull(annotationNodes));
        this.containerNodes = ImmutableSet.copyOf(Objects.requireNonNull(imageNodes));
        this.containerLayerNodes = ImmutableSet.copyOf(Objects.requireNonNull(imageLayerNodes));
    }

    public Set<ProtoFileNode> getFileNodes() {
        return fileNodes;
    }

    public Set<ProtoDependencyNode> getDependencyNodes() {
        return dependencyNodes;
    }

    public Set<ProtoComponentNode> getComponentNodes() {
        return componentNodes;
    }

    public Set<ProtoAnnotationNode> getAnnotationNodes() {
        return annotationNodes;
    }

    public Set<ProtoContainerNode> getContainerNodes() {
        return containerNodes;
    }

    public Set<ProtoContainerLayerNode> getContainerLayerNodes() {
        return containerLayerNodes;
    }
}
