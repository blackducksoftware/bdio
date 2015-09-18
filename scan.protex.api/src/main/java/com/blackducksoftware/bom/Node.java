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

import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Primary unit of output in a serialized Bill of Materials.
 *
 * @author jgustie
 */
public interface Node {

    /**
     * The unique node identifier. Must be a valid IRI.
     */
    @Nonnull
    String id();

    /**
     * The set of types this node conforms to. May be empty.
     */
    @Nonnull
    Set<Type> types();

    /**
     * The data payload of this node, expressed as a mapping of terms to unspecified object values. May be empty.
     */
    @Nonnull
    Map<Term, Object> data();

}
