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

import com.blackducksoftware.bdio.proto.api.BdioValidationException;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;
import com.google.protobuf.Message;

/**
 * Validate version 1 bdio data
 *
 * @author sharapov
 *
 */
public class ProtobufBdioV1Validator extends AbstractProtobufBdioValidator {

    @Override
    public void validate(Message message) {
        if (message instanceof ProtoFileNode) {
            validate((ProtoFileNode) message);
        } else {
            throw new BdioValidationException("Unknown message type in version 1: " + message.getClass().getName());
        }
    }

}
