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

import static com.google.common.truth.Truth.assertThat;

import java.net.URI;

import org.junit.Test;

public class LinkedDataContextTest {

    @Test
    public void testBaseUri() {
        String base = "http://example.com/test";
        LinkedDataContext context = new LinkedDataContext(base);
        assertThat(context.getBase()).isEqualTo(URI.create(base));
        assertThat(context.getBase().isAbsolute()).isTrue();
    }

    @Test
    public void testNullBaseUri() {
        assertThat(new LinkedDataContext().getBase()).isNull();
        assertThat(new LinkedDataContext(null).getBase()).isNull();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBaseUri() {
        new LinkedDataContext(":");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRelativeBaseUri() {
        new LinkedDataContext("/foo");
    }

}
