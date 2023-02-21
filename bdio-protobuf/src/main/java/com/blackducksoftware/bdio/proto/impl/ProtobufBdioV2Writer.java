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
import java.util.zip.ZipOutputStream;

import com.blackducksoftware.bdio.proto.BdioConstants;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.google.protobuf.Any;
import com.google.protobuf.Message;

/**
 * Implements methods to write version 2 bdio data
 *
 * @author sharapov
 *
 */
public class ProtobufBdioV2Writer extends AbstractProtobufBdioVersionWriter {

    public ProtobufBdioV2Writer(IProtobufBdioValidator validator) {
        super(validator);
    }

    @Override
    protected short getVersion() {
        return BdioConstants.VERSION_2;
    }

    @Override
    protected void writeHeaderNode(ZipOutputStream bdioArchive, Message header) throws IOException {
        Any any = Any.pack(header);
        any.writeTo(bdioArchive);
    }

    @Override
    public void writeDataNode(ZipOutputStream bdioArchive, Message node) throws IOException {
        Any any = Any.pack(node);
        any.writeDelimitedTo(bdioArchive);
    }
}
