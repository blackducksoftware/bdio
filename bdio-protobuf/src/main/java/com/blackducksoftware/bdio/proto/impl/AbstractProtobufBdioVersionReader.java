package com.blackducksoftware.bdio.proto.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import com.blackducksoftware.bdio.proto.api.BdioChunk;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioVersionReader;
import com.blackducksoftware.bdio.proto.v1.ProtoScanHeader;
import com.google.protobuf.Any;

/**
 * Abstract protobuf bdio reader
 * 
 * @author sharapov
 *
 */
public abstract class AbstractProtobufBdioVersionReader implements IProtobufBdioVersionReader {

	@Override
	public ProtoScanHeader readHeaderChunk(InputStream in) throws IOException {
		Any any = Any.parseFrom(in);
		if (any.is(ProtoScanHeader.class)) {
			return any.unpack(ProtoScanHeader.class);
		}

		throw new RuntimeException("Unknown type for header data: " + any.getDescriptorForType().getFullName());
	}

	@Override
	public BdioChunk readBdioChunk(InputStream in) throws IOException {
		BdioChunkBuilder bdioChunkBuilder = new BdioChunkBuilder();

		while (true) {
			Any any = Any.parseDelimitedFrom(in);

			if (any == null) {
				// the end of the steam
				break;
			}

			Optional<Class<? extends com.google.protobuf.Message>> clz = getClassesList().stream()
					.filter(cl -> any.is(cl)).findFirst();

			if (clz.isEmpty()) {
				throw new RuntimeException("Object of unknown class is found: " + any.getTypeUrl());
			}

			bdioChunkBuilder.add(any.unpack(clz.get()));
		}

		return bdioChunkBuilder.build();
	}
}
