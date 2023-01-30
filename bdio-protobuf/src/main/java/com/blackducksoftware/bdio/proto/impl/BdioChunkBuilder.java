package com.blackducksoftware.bdio.proto.impl;

import java.util.HashSet;
import java.util.Set;

import com.blackducksoftware.bdio.proto.api.BdioChunk;
import com.blackducksoftware.bdio.proto.v1.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.v1.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.v1.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.v1.ProtoFileNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageLayerNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageNode;

/**
 * Builder for bdio chunk
 * 
 * @author sharapov
 *
 */
public class BdioChunkBuilder {

	private final Set<ProtoFileNode> fileNodes = new HashSet<>();

	private final Set<ProtoDependencyNode> dependencyNodes = new HashSet<>();

	private final Set<ProtoComponentNode> componentNodes = new HashSet<>();

	private final Set<ProtoAnnotationNode> annotationNodes = new HashSet<>();

	private final Set<ProtoImageNode> imageNodes = new HashSet<>();

	private final Set<ProtoImageLayerNode> imageLayerNodes = new HashSet<>();

	public BdioChunkBuilder add(com.google.protobuf.Message node) {
		if (node instanceof ProtoDependencyNode) {
			dependencyNodes.add((ProtoDependencyNode) node);
		} else if (node instanceof ProtoComponentNode) {
			componentNodes.add((ProtoComponentNode) node);
		} else if (node instanceof ProtoFileNode) {
			fileNodes.add((ProtoFileNode) node);
		} else if (node instanceof ProtoAnnotationNode) {
			annotationNodes.add((ProtoAnnotationNode) node);
		} else if (node instanceof ProtoImageNode) {
			imageNodes.add((ProtoImageNode) node);
		} else if (node instanceof ProtoImageLayerNode) {
			imageLayerNodes.add((ProtoImageLayerNode) node);
		} else {
			throw new RuntimeException("Unknown type: " + node.getClass().getName());
		}

		return this;
	}

	public BdioChunk build() {
		return new BdioChunk(fileNodes, dependencyNodes, componentNodes, annotationNodes, imageNodes, imageLayerNodes);
	}

}
