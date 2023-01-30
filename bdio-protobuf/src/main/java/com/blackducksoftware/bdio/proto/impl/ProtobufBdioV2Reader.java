package com.blackducksoftware.bdio.proto.impl;

import java.util.List;

import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.v1.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.v1.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.v1.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.v1.ProtoFileNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageLayerNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageNode;
import com.google.common.collect.ImmutableList;

/**
 * Reads the protobuf bdio data of version 2
 * 
 * @author sharapov
 *
 */
public class ProtobufBdioV2Reader extends AbstractProtobufBdioVersionReader {

	public ProtobufBdioV2Reader(IProtobufBdioValidator validator) {
		super(validator);
	}

	@Override
	public List<Class<? extends com.google.protobuf.Message>> getClassesList() {
		return ImmutableList.of(ProtoDependencyNode.class, ProtoComponentNode.class, ProtoFileNode.class,
				ProtoAnnotationNode.class, ProtoImageNode.class, ProtoImageLayerNode.class);
	}
}
