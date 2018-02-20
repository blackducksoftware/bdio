/*
 * Copyright 2018 Synopsys, Inc.
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
package com.blackducksoftware.bdio2.tinkerpop;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;

/**
 * Available versions of BDIO. Note that the version is can only be used to control reading into the graph, the default
 * version is always used for writing.
 *
 * @author jgustie
 */
public enum BlackDuckIoVersion {
    V1_0,
    V1_1,
    V1_1_1,
    V2_0,
    ;

    static BlackDuckIoVersion defaultVersion() {
        return V2_0;
    }

    @Nullable
    Object expandContext(@Nullable Object expandContext) {
        switch (this) {
        case V1_0:
            return Bdio.Context.VERSION_1_0;
        case V1_1:
            return Bdio.Context.VERSION_1_1;
        case V1_1_1:
            return Bdio.Context.VERSION_1_1_1;
        case V2_0:
            return expandContext;
        default:
            throw new IllegalStateException("unknown version: " + this);
        }
    }

}
