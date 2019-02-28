/*
 * Copyright 2016 Black Duck Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    /**
     * Creates a new exception for when an entry is too large.
     */
    public EntrySizeViolationException(@Nullable String name, long estimatedSize) {
        super("Entry length violation");
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
