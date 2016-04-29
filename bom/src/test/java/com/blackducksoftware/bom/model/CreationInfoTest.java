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
package com.blackducksoftware.bom.model;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * Tests for {@code CreationInfo}.
 *
 * @author jgustie
 */
public class CreationInfoTest {

    @Test
    public void currentToolHasMainMethod() throws ReflectiveOperationException {
        // No boom.
        CreationInfo.currentToolClass().getMethod("main", String[].class);
    }

    @Test
    public void introspectCurrentTool() {
        final CreationInfo currentTool = CreationInfo.currentTool();
        assertThat(currentTool).isNotNull();
        assertThat(currentTool.getCreated()).isNotNull();
        assertThat(currentTool.getCreator()).hasSize(1);
        assertThat(getOnlyElement(currentTool.getCreator())).startsWith("Tool: ");
    }

}
