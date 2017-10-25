/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio2;

import java.io.IOException;

import javax.annotation.Nullable;

/**
 * Thrown when a BDIO entry exceeds the maximum allowable size.
 *
 * @author jgustie
 */
public class EntrySizeViolationException extends IOException {

    private static final long serialVersionUID = 1627540107517253512L;

    /**
     * The name of the entry that exceeded the maximum length.
     */
    @Nullable
    private final String name;

    /**
     * The estimated total size of the entry in bytes or -1 if it is not known.
     */
    private final long estimatedSize;

    private static String message(@Nullable String name, long estimatedSize) {
        return "Entry length violation";
    }

    /**
     * Creates a new exception for when an entry is too large.
     */
    public EntrySizeViolationException(@Nullable String name, long estimatedSize) {
        super(message(name, estimatedSize));
        this.name = name;
        this.estimatedSize = estimatedSize;
    }

    /**
     * Returns the name of the entry that caused the violation. This might be {@code null} if the violation occurred
     * while reading a plain JSON file or while writing a BDIO file where the entry name has not yet been computed.
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Returns the estimated size of the entry that caused the violation (or -1 if the size is unknown). This should be
     * greater then the maximum entry size, however the Zip metadata may misrepresent the size of the entry.
     */
    public long getEstimatedSize() {
        return estimatedSize;
    }

}
