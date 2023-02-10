/*
 * Copyright (C) 2023 Synopsys Inc.
 * http://www.synopsys.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Synopsys ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Synopsys.
 */
package com.blackducksoftware.bdio.proto.api;

import com.google.protobuf.Message;

/**
 * Api for validation of protobuf message before serialization or after deserialization
 *
 * @author sharapov
 *
 */
public interface IProtobufBdioValidator {

    /**
     * Validate single protobuf data node
     *
     * @param message
     *            node to validate
     * @throws BdioValidationException
     */
    void validate(Message message);
}
