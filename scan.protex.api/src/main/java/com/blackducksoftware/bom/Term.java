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

/**
 * A representation of a term use to express the name of a property in a node.
 *
 * @author jgustie
 */
public interface Term {

    /**
     * Returns a fully qualified IRI for this term.
     */
    @Override
    String toString();

    // TODO With interface defaults, we should add `URI toUri()`

}
