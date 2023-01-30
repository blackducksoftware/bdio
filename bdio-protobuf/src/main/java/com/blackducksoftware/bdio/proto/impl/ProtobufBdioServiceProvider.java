package com.blackducksoftware.bdio.proto.impl;

import java.util.HashMap;
import java.util.Map;

import com.blackducksoftware.bdio.proto.api.IProtobufBdioVersionReader;

/**
 * Provides protobuf bdio reader for specific version
 * 
 * @author sharapov
 *
 */
public class ProtobufBdioReaderProvider {

	private final Map<Integer, AbstractProtobufBdioVersionReader> protoBufReadersMap = new HashMap<>();

	public ProtobufBdioReaderProvider() {
		protoBufReadersMap.put(1, new ProtobufBdioV1Reader());
		protoBufReadersMap.put(2, new ProtobufBdioV2Reader());
	}

	public IProtobufBdioVersionReader getProtobufBdioReader(int version) {

		IProtobufBdioVersionReader reader = protoBufReadersMap.get(version);

		if (reader == null) {
			throw new RuntimeException("Unknow version is detected: " + version);
		}

		return reader;
	}

}
