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

/**
 * Enum defining bdio entry type.
 * <p>
 * NOTE: Order matters here, only append new types to the end.
 *
 * @author sharapov
 */
public enum BdioEntryType {
    HEADER, CHUNK
}
