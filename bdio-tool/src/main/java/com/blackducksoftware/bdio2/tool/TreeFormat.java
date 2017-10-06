/*
 * Copyright 2017 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2.tool;

import java.util.function.IntPredicate;

/**
 * Utilities for helping format trees.
 *
 * @author jgustie
 */
final class TreeFormat {

    /**
     * Appends an ASCII indentation prefix to the supplied line.
     *
     * @param buffer
     *            the buffer to append to
     * @param depth
     *            the depth (distance from the root) of the node
     * @param childrenAtDepths
     *            a set indicating which ancestors have outstanding lineage
     * @param hasMoreSiblings
     *            flag indicating if this is the last sibling
     */
    public static void appendAsciiIndent(StringBuilder buffer, int depth, IntPredicate childrenAtDepths, boolean hasMoreSiblings) {
        if (depth > 0) {
            for (int i = 0; i < depth - 1; ++i) {
                if (childrenAtDepths.test(i)) {
                    buffer.append('|');
                } else {
                    buffer.append(' ');
                }
                buffer.append("   ");
            }
            buffer.append(hasMoreSiblings ? '|' : '`').append("-- ");
        }
    }

    private TreeFormat() {
        assert false;
    }
}
