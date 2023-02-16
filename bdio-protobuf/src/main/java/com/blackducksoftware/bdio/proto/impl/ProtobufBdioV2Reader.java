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

import java.util.List;

import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.domain.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.domain.ProtoBdbaFileNode;
import com.blackducksoftware.bdio.proto.domain.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerLayerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;

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
    public List<Class<? extends Message>> getClassesList() {
        return ImmutableList.of(
                ProtoDependencyNode.class,
                ProtoComponentNode.class,
                ProtoFileNode.class,
                ProtoAnnotationNode.class,
                ProtoContainerNode.class,
                ProtoContainerLayerNode.class,
                ProtoBdbaFileNode.class);
    }
}
