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
package com.blackducksoftware.bdio.proto;

public class BdioConstants {

    /**
     * version of data model, currently (till 2023.1.0 inclusive) used by signature
     * scans
     */
    public static final short VERSION_1 = 1;

    /**
     * version of data model in bdio entry, that will be used for SIGNATURE, BINARY and CONTAINER scan types
     */
    public static final short VERSION_2 = 2;

    /**
     * current version of data model
     */
    public static final short CURRENT_VERSION = VERSION_2;

    /**
     * name of header entry in bdio archive
     */
    public static final String HEADER_FILE_NAME = "bdio-header.pb";

    /**
     * name of entry in bdio archive
     */
    public static final String ENTRY_FILE_NAME_TEMPLATE = "bdio-entry-%02d.pb";

    /**
     * maximum size of bdio archive entry
     */
    public static final long MAX_CHUNK_SIZE = Math.multiplyExact(16, 1024 * 1024); // 16 Mb

}
