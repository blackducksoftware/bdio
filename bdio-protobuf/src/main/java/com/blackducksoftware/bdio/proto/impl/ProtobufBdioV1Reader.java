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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.domain.ProtoChunk;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;
import com.blackducksoftware.bdio.proto.domain.ProtoScanHeader;
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

    @Override
    public List<Class<? extends com.google.protobuf.Message>> getClassesList() {
        return ImmutableList.of(ProtoFileNode.class);
    }

    @Override
    public ProtoChunk readProtoChunk(InputStream in) throws IOException {
        Set<ProtoFileNode> fileNodes = new HashSet<>();

        ProtoFileNode node;

        // in version 1 only file nodes may be present
        while ((node = ProtoFileNode.parseDelimitedFrom(in)) != null) {
            validator.validate(node);
            fileNodes.add(node);
        }

        return new ProtoChunk(fileNodes);
    }

}
