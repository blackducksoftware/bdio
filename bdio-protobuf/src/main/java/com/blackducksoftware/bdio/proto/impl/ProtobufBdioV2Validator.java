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

import com.blackducksoftware.bdio.proto.domain.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.domain.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerLayerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;
import com.google.protobuf.Message;

/**
 * Validate version 2 bdio data
 *
 * @author sharapov
 *
 */
public class ProtobufBdioV2Validator extends AbstractProtobufBdioValidator {

    @Override
    public void validate(Message message) {

        if (message instanceof ProtoDependencyNode) {
            validate((ProtoDependencyNode) message);
        } else if (message instanceof ProtoComponentNode) {
            validate((ProtoComponentNode) message);
        } else if (message instanceof ProtoFileNode) {
            validate((ProtoFileNode) message);
        } else if (message instanceof ProtoAnnotationNode) {
            validate((ProtoAnnotationNode) message);
        } else if (message instanceof ProtoContainerNode) {
            validate((ProtoContainerNode) message);
        } else if (message instanceof ProtoContainerLayerNode) {
            validate((ProtoContainerLayerNode) message);
        } else {
            throw new RuntimeException("Unknown type: " + message.getClass().getName());
        }
    }

}
