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
package com.blackducksoftware.bdio.proto.impl;

import java.util.HashSet;
import java.util.Set;

import com.blackducksoftware.bdio.proto.domain.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.domain.ProtoChunk;
import com.blackducksoftware.bdio.proto.domain.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerLayerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;
import com.google.protobuf.Message;

/**
 * Builder for bdio chunk
 *
 * @author sharapov
 *
 */
public class ProtoChunkBuilder {

    private final Set<ProtoFileNode> fileNodes = new HashSet<>();

    private final Set<ProtoDependencyNode> dependencyNodes = new HashSet<>();

    private final Set<ProtoComponentNode> componentNodes = new HashSet<>();

    private final Set<ProtoAnnotationNode> annotationNodes = new HashSet<>();

    private final Set<ProtoContainerNode> containerNodes = new HashSet<>();

    private final Set<ProtoContainerLayerNode> containerLayerNodes = new HashSet<>();

    public ProtoChunkBuilder add(Message node) {
        if (node instanceof ProtoDependencyNode) {
            dependencyNodes.add((ProtoDependencyNode) node);
        } else if (node instanceof ProtoComponentNode) {
            componentNodes.add((ProtoComponentNode) node);
        } else if (node instanceof ProtoFileNode) {
            fileNodes.add((ProtoFileNode) node);
        } else if (node instanceof ProtoAnnotationNode) {
            annotationNodes.add((ProtoAnnotationNode) node);
        } else if (node instanceof ProtoContainerNode) {
            containerNodes.add((ProtoContainerNode) node);
        } else if (node instanceof ProtoContainerLayerNode) {
            containerLayerNodes.add((ProtoContainerLayerNode) node);
        } else {
            throw new RuntimeException("Unknown type: " + node.getClass().getName());
        }

        return this;
    }

    public ProtoChunk build() {
        return new ProtoChunk(
                fileNodes,
                dependencyNodes,
                componentNodes,
                annotationNodes,
                containerNodes,
                containerLayerNodes);
    }

}
