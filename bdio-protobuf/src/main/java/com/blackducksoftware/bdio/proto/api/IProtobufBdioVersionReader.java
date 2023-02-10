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
import java.io.InputStream;

import com.blackducksoftware.bdio.proto.domain.ProtoChunk;
import com.blackducksoftware.bdio.proto.domain.ProtoScanHeader;

/**
 * Api for reading bdio data
 *
 * @author sharapov
 *
 */
public interface IProtobufBdioVersionReader {

    /**
     * Reads header chunk represented by input stream
     *
     * @param in
     *            input stream
     * @return ProtoScanHeader
     * @throws IOException
     */
    ProtoScanHeader readHeaderChunk(InputStream in) throws IOException;

    /**
     * Reads the bdio entry (chunk) represented by input stream.
     *
     * @param in
     *            input stream
     * @return ProtoChunk deserialized bdio entry (chunk)
     * @throws IOException
     */
    ProtoChunk readProtoChunk(InputStream in) throws IOException;

}
