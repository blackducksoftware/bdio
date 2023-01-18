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

public class BdioChunk {

    private final Set<ProtoFileNode> fileNodes;

    private final Set<ProtoDependencyNode> dependencyNodes;

    private final Set<ProtoComponentNode> componentNodes;

    private final Set<ProtoAnnotationNode> annotationNodes;

    private final Set<ProtoImageNode> imageNodes;

    private final Set<ProtoImageLayerNode> layerNodes;

    public BdioChunk(Set<ProtoFileNode> fileData) {
        this.fileNodes = Objects.requireNonNull(fileData);
        this.dependencyNodes = Collections.emptySet();
        this.componentNodes = Collections.emptySet();
        this.annotationNodes = Collections.emptySet();
        this.imageNodes = Collections.emptySet();
        this.layerNodes = Collections.emptySet();
    }

    public BdioChunk(Set<ProtoFileNode> fileData, Set<ProtoDependencyNode> dependencyData, Set<ProtoComponentNode> componentData,
            Set<ProtoAnnotationNode> annotationData, Set<ProtoImageNode> imageData, Set<ProtoImageLayerNode> layerData) {
        this.fileNodes = Objects.requireNonNull(fileData);
        this.dependencyNodes = Objects.requireNonNull(dependencyData);
        this.componentNodes = Objects.requireNonNull(componentData);
        this.annotationNodes = Objects.requireNonNull(annotationData);
        this.imageNodes = Objects.requireNonNull(imageData);
        this.layerNodes = Objects.requireNonNull(layerData);
    }

    public Set<ProtoFileNode> getFileData() {
        return fileNodes;
    }

    public Set<ProtoDependencyNode> getDependencyData() {
        return dependencyNodes;
    }

    public Set<ProtoComponentNode> getComponentData() {
        return componentNodes;
    }

    public Set<ProtoAnnotationNode> getAnnotationData() {
        return annotationNodes;
    }

    public Set<ProtoImageNode> getImageData() {
        return imageNodes;
    }

    public Set<ProtoImageLayerNode> getLayerData() {
        return layerNodes;
    }
}
