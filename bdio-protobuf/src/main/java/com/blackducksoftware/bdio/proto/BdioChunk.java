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
package com.blackducksoftware.bdio.proto;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.blackducksoftware.bdio.proto.v1.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.v1.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.v1.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.v1.ProtoFileNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageLayerNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageNode;

/**
 * This class holds data deserialized from bdio chunk in protobuf format
 * 
 * @author sharapov
 *
 */
public class BdioChunk {

    private final Set<ProtoFileNode> fileNodes;

    private final Set<ProtoDependencyNode> dependencyNodes;

    private final Set<ProtoComponentNode> componentNodes;

    private final Set<ProtoAnnotationNode> annotationNodes;

    private final Set<ProtoImageNode> imageNodes;

    private final Set<ProtoImageLayerNode> imageLayerNodes;

    public BdioChunk(Set<ProtoFileNode> fileNodes) {
        this.fileNodes = Objects.requireNonNull(fileNodes);
        this.dependencyNodes = Collections.emptySet();
        this.componentNodes = Collections.emptySet();
        this.annotationNodes = Collections.emptySet();
        this.imageNodes = Collections.emptySet();
        this.imageLayerNodes = Collections.emptySet();
    }

    public BdioChunk(Set<ProtoFileNode> fileNodes, Set<ProtoDependencyNode> dependencyNodes, Set<ProtoComponentNode> componentNodes,
            Set<ProtoAnnotationNode> annotationNodes, Set<ProtoImageNode> imageNodes, Set<ProtoImageLayerNode> imageLayerNodes) {
        this.fileNodes = Objects.requireNonNull(fileNodes);
        this.dependencyNodes = Objects.requireNonNull(dependencyNodes);
        this.componentNodes = Objects.requireNonNull(componentNodes);
        this.annotationNodes = Objects.requireNonNull(annotationNodes);
        this.imageNodes = Objects.requireNonNull(imageNodes);
        this.imageLayerNodes = Objects.requireNonNull(imageLayerNodes);
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

    public Set<ProtoImageNode> getImageNodes() {
        return imageNodes;
    }

    public Set<ProtoImageLayerNode> getImageLayerNodes() {
        return imageLayerNodes;
    }
}
