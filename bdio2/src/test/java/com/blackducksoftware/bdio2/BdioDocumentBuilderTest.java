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

import java.net.URI;

import org.junit.Test;

/**
 * Tests for {@link BdioDocument.Builder}.
 *
 * @author jgustie
 */
public class BdioDocumentBuilderTest {

    @Test(expected = NullPointerException.class)
    public void builder_nullBaseString() {
        new BdioDocument.Builder().base((String) null);
    }

    @Test(expected = NullPointerException.class)
    public void builder_nullBaseUri() {
        new BdioDocument.Builder().base((URI) null);
    }

    @Test(expected = NullPointerException.class)
    public void builder_nullAllowRemoteLoading() {
        new BdioDocument.Builder().allowRemoteLoading(null);
    }

}
