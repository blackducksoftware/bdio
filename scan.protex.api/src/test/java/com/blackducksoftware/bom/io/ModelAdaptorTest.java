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

import org.junit.Test;

import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.ImmutableNode;
import com.blackducksoftware.bom.Node;
import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.model.File;

/**
 * Tests for the model adaptors.
 *
 * @author jgustie
 */
public class ModelAdaptorTest {

    /**
     * A node for use in the {@code basic*} tests.
     */
    private static final Node BASIC_NODE = ImmutableNode.builder()
            .id("foo")
            .addType(BlackDuckType.FILE)
            .put(SpdxTerm.FILE_NAME, "./foo")
            .build();

    /**
     * We only need one of these unless we try to run in multiple threads.
     */
    private final ModelAdaptor adaptor = ModelAdaptor.create();

    @Test
    public void basicAdaptorLookup() {
        // Create a model and set an arbitrary property to verify it is recognized
        File model = new File();
        model.setPath("./foo");
        assertThat(adaptor.update("foo", model)).isEqualTo(BASIC_NODE);
    }

    @Test
    public void basicAdaptorStore() throws Exception {
        // Create a model using the adaptor an verify an arbitrary property is transfered
        File model = adaptor.createModel(File.class);
        adaptor.update(BASIC_NODE);
        assertThat(model.getPath()).isEqualTo("./foo");
    }

}
