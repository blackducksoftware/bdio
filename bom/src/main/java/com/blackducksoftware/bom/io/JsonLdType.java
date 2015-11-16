/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 * 
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
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
