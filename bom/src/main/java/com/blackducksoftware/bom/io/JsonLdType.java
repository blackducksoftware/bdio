/*
 * Copyright 2015 Black Duck Software, Inc.
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
package com.blackducksoftware.bom.io;

import java.net.URI;

import com.blackducksoftware.bom.Type;

/**
 * Types specific to JSON-LD that we really only need while writing.
 *
 * @author jgustie
 */
public enum JsonLdType implements Type {

    /**
     * Yes, "@id" is also a type.
     */
    ID("@id");

    private final String type;

    private JsonLdType(String type) {
        this.type = type;
    }

    /**
     * Note that this implementation returns keywords, not IRIs.
     */
    @Override
    public String toString() {
        return type;
    }

    @Override
    public URI toUri() {
        if (type.startsWith("@")) {
            throw new UnsupportedOperationException();
        } else {
            return URI.create(type);
        }
    }
}
