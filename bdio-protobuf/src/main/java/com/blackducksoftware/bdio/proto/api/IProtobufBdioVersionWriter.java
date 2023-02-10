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

import java.io.IOException;
import java.util.zip.ZipOutputStream;

import com.google.protobuf.Message;

/**
 * Api for writing bdio data
 *
 * @author sharapov
 *
 */
public interface IProtobufBdioVersionWriter {

    /**
     * Write header data to bdio header in provided zip archive stream
     *
     * @param bdioArchive
     * @param header
     * @throws IOException
     */
    void writeToHeader(ZipOutputStream bdioArchive, Message header) throws IOException;

    /**
     * Write node to bdio entry in provided zip archive stream
     *
     * @param bdioArchive
     * @param node
     * @throws IOException
     */
    void writeToEntry(ZipOutputStream bdioArchive, Message node) throws IOException;

}
