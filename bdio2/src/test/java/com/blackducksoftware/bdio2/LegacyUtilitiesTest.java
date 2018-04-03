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
package com.blackducksoftware.bdio2;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * Tests for {@code LegacyUtilities}.
 *
 * @author jgustie
 */
public class LegacyUtilitiesTest {

    @Test
    public void guessScheme() {
        // It would be nice to have parameterized tests :)
        String[][] data = new String[][] {
                { "Simple ZIP", "foobar.zip", "zip" },
                { "Inner ZIP extension", "foobar.zip.foo", "zip" },
                { "Case insensitive ZIP", "foobar.ZIP", "zip" },
                { "Compressed TAR, separate extension", "foobar.tar.gz", "tar" },
                { "Compressed TAR, combined extension", "foobar.tgz", "tar" },
                { "Dot-dot", "foobar.zip..foo", "zip" },
                { "Ignore file name", "zip.foo", "unknown" },
                { "Ignore path", "foo.zip/bar.foo", "unknown" },
        };
        for (String[] testCase : data) {
            assertThat(LegacyUtilities.guessScheme(testCase[1])).named(testCase[0]).isEqualTo(testCase[2]);
        }
    }

}
