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
package com.blackducksoftware.bom;

import java.net.URI;

/**
 * A representation of a type used to classify a node.
 *
 * @author jgustie
 */
public interface Type {

    /**
     * Returns a fully qualified IRI for this type.
     */
    @Override
    String toString();

    /**
     * Returns this term as URI.
     */
    URI toUri();

}
