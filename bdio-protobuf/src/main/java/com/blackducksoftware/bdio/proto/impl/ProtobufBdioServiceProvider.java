package com.blackducksoftware.bdio.proto.impl;

import java.util.HashMap;
import java.util.Map;

import com.blackducksoftware.bdio.proto.VersionConstants;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioVersionReader;

/**
 * Provides service for specific version
 * 
 * @author sharapov
 *
 */
public class ProtobufBdioServiceProvider {

	private final Map<Short, IProtobufBdioVersionReader> protobufReaders = new HashMap<>();
	private final Map<Short, IProtobufBdioValidator> protobufValidators = new HashMap<>();

	public ProtobufBdioServiceProvider() {

		ProtobubBdioV1Validator v1Validator = new ProtobubBdioV1Validator();
		ProtobufBdioV1Reader v1Reader = new ProtobufBdioV1Reader(v1Validator);
		protobufValidators.put(VersionConstants.VERSION_1, v1Validator);
		protobufReaders.put(VersionConstants.VERSION_1, v1Reader);

		ProtobubBdioV2Validator v2Validator = new ProtobubBdioV2Validator();
		ProtobufBdioV2Reader v2Reader = new ProtobufBdioV2Reader(v2Validator);
		protobufValidators.put(VersionConstants.VERSION_2, v2Validator);
		protobufReaders.put(VersionConstants.VERSION_2, v2Reader);
	}

	public IProtobufBdioVersionReader getProtobufBdioReader(short version) {

		IProtobufBdioVersionReader reader = protobufReaders.get(version);

		if (reader == null) {
			throw new RuntimeException("Unknow version is detected: " + version);
		}

		return reader;
	}

	public IProtobufBdioValidator getProtobufBdioValidator(short version) {

		IProtobufBdioValidator validator = protobufValidators.get(version);

		if (validator == null) {
			throw new RuntimeException("Unknow version is detected: " + version);
		}

		return validator;
	}

}
