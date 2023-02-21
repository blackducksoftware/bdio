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

import java.util.HashMap;
import java.util.Map;

import com.blackducksoftware.bdio.proto.BdioConstants;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioVersionReader;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioVersionWriter;

/**
 * Provides service for specific version
 *
 * @author sharapov
 *
 */
public class ProtobufBdioServiceProvider {

    private final Map<Short, IProtobufBdioValidator> protobufValidators = new HashMap<>();

    private final Map<Short, IProtobufBdioVersionReader> protobufReaders = new HashMap<>();

    private final Map<Short, IProtobufBdioVersionWriter> protobufWriters = new HashMap<>();

    public ProtobufBdioServiceProvider() {

        ProtobufBdioV1Validator v1Validator = new ProtobufBdioV1Validator();
        ProtobufBdioV1Reader v1Reader = new ProtobufBdioV1Reader(v1Validator);
        IProtobufBdioVersionWriter v1Writer = new ProtobufBdioV1Writer(v1Validator);

        protobufValidators.put(BdioConstants.VERSION_1, v1Validator);
        protobufReaders.put(BdioConstants.VERSION_1, v1Reader);
        protobufWriters.put(BdioConstants.VERSION_1, v1Writer);

        ProtobufBdioV2Validator v2Validator = new ProtobufBdioV2Validator();
        ProtobufBdioV2Reader v2Reader = new ProtobufBdioV2Reader(v2Validator);
        IProtobufBdioVersionWriter v2Writer = new ProtobufBdioV2Writer(v2Validator);

        protobufValidators.put(BdioConstants.VERSION_2, v2Validator);
        protobufReaders.put(BdioConstants.VERSION_2, v2Reader);
        protobufWriters.put(BdioConstants.VERSION_2, v2Writer);

    }

    public IProtobufBdioValidator getProtobufBdioValidator(short version) {

        IProtobufBdioValidator validator = protobufValidators.get(version);

        if (validator == null) {
            throw new RuntimeException("Unknow version is detected: " + version);
        }

        return validator;
    }

    public IProtobufBdioVersionReader getProtobufBdioReader(short version) {

        IProtobufBdioVersionReader reader = protobufReaders.get(version);

        if (reader == null) {
            throw new RuntimeException("Unknow version is detected: " + version);
        }

        return reader;
    }

    public IProtobufBdioVersionWriter getProtobufBdioWriter(short version) {

        IProtobufBdioVersionWriter writer = protobufWriters.get(version);

        if (writer == null) {
            throw new RuntimeException("Unknow version is detected: " + version);
        }

        return writer;
    }

}
