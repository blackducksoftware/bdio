/*
 * Copyright 2016 Black Duck Software, Inc.
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
