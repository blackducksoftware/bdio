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

/**
 *
 * @author sharapov
 *
 */
public class BdioValidationException extends RuntimeException {

    private static final long serialVersionUID = 3528312351351639125L;

    public BdioValidationException(String message) {
        super(message);
    }

}
