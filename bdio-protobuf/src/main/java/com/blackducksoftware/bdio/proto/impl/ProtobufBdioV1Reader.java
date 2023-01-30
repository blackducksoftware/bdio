package com.blackducksoftware.bdio.proto.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.blackducksoftware.bdio.proto.api.BdioChunk;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.v1.ProtoFileNode;
import com.blackducksoftware.bdio.proto.v1.ProtoScanHeader;
import com.google.common.collect.ImmutableList;

/**
 * Reads the protobuf bdio data of version 1
 * 
 * @author sharapov
 *
 */
public class ProtobufBdioV1Reader extends AbstractProtobufBdioVersionReader {

	public ProtobufBdioV1Reader(IProtobufBdioValidator validator) {
		super(validator);
	}

	@Override
	public ProtoScanHeader readHeaderChunk(InputStream in) throws IOException {
		return ProtoScanHeader.parseFrom(in);
	}

	public List<Class<? extends com.google.protobuf.Message>> getClassesList() {
		return ImmutableList.of(ProtoFileNode.class);
	}

	@Override
	public BdioChunk readBdioChunk(InputStream in) throws IOException {
		Set<ProtoFileNode> fileNodes = new HashSet<>();

		ProtoFileNode node;

		// in version 1 only file nodes may be present
		while ((node = ProtoFileNode.parseDelimitedFrom(in)) != null) {
			validator.validate(node);
			fileNodes.add(node);
		}

		return new BdioChunk(fileNodes);
	}

}
