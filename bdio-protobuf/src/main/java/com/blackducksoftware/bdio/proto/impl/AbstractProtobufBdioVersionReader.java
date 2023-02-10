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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioVersionReader;
import com.blackducksoftware.bdio.proto.domain.ProtoChunk;
import com.blackducksoftware.bdio.proto.domain.ProtoScanHeader;
import com.google.protobuf.Any;
import com.google.protobuf.Message;

/**
 * Abstract protobuf bdio reader
 *
 * @author sharapov
 *
 */
public abstract class AbstractProtobufBdioVersionReader implements IProtobufBdioVersionReader {

    protected final IProtobufBdioValidator validator;

    public AbstractProtobufBdioVersionReader(IProtobufBdioValidator validator) {
        this.validator = Objects.requireNonNull(validator);
    }

    /**
     * Get list of message type classes representing data model for specific version
     *
     * @return List<Class<? extends Message>>
     */
    abstract List<Class<? extends Message>> getClassesList();

    @Override
    public ProtoScanHeader readHeaderChunk(InputStream in) throws IOException {
        Any any = Any.parseFrom(in);
        if (any.is(ProtoScanHeader.class)) {
            return any.unpack(ProtoScanHeader.class);
        }

        throw new RuntimeException("Unknown type for header data: " + any.getTypeUrl());
    }

    @Override
    public ProtoChunk readProtoChunk(InputStream in) throws IOException {
        ProtoChunkBuilder protoChunkBuilder = new ProtoChunkBuilder();

        while (true) {
            Any any = Any.parseDelimitedFrom(in);

            if (any == null) {
                // the end of the steam
                break;
            }

            Optional<Class<? extends Message>> clz = getClassesList().stream()
                    .filter(cl -> any.is(cl)).findFirst();

            if (clz.isEmpty()) {
                throw new RuntimeException("Object of unknown class is found: " + any.getTypeUrl());
            }

            Message message = any.unpack(clz.get());
            validator.validate(message);
            protoChunkBuilder.add(message);
        }

        return protoChunkBuilder.build();
    }

}
