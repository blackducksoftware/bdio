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
package com.blackducksoftware.bdio2;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * Tests for {@link BdioObject}.
 *
 * @author jgustie
 */
public class BdioObjectTest {

    /**
     * A BDIO object is just a regular map, we can put any garbage we want into it.
     */
    @Test
    public void regularMap() {
        BdioObject bdioObject = new BdioObject();

        // Simple string key name, value is replaced
        bdioObject.put("test1", "foo");
        assertThat(bdioObject).containsEntry("test1", "foo");
        bdioObject.put("test1", "bar");
        assertThat(bdioObject).containsEntry("test1", "bar");

        // Delimiter character in key name, Long value
        bdioObject.put("X:Y:Z", 1L);
        assertThat(bdioObject).containsEntry("X:Y:Z", 1L);
    }

    /**
     * The values supplied to the constructors are available through the map.
     */
    @Test
    public void constructorValues() {
        BdioObject typeOnly = new BdioObject(Bdio.Class.Project);
        assertThat(typeOnly).doesNotContainKey("@id");
        assertThat(typeOnly).containsEntry("@type", Bdio.Class.Project.toString());

        BdioObject typeAndId = new BdioObject("123", Bdio.Class.Project);
        assertThat(typeAndId).containsEntry("@id", "123");
        assertThat(typeAndId).containsEntry("@type", Bdio.Class.Project.toString());
    }

    /**
     * Using a {@code null} key fails.
     */
    @Test(expected = NullPointerException.class)
    public void putNullKeyFails() {
        new BdioObject().put(null, "foo");
    }

    /**
     * Putting a {@code null} value into the map removes the entry.
     */
    @Test
    public void putNullValueIsRemove() {
        BdioObject bdioObject = new BdioObject();
        bdioObject.put("test1", "foo");
        assertThat(bdioObject).containsKey("test1");
        bdioObject.put("test1", null);
        assertThat(bdioObject).doesNotContainKey("test1");
    }

    /**
     * The JSON-LD identifier can be replaced by putting a new value into the map if you know the key.
     */
    @Test
    public void replaceIdentifier() {
        BdioObject bdioObject = new BdioObject();
        assertThat(bdioObject.id()).isNull();
        bdioObject.put("@id", "123");
        assertThat(bdioObject.id()).isEqualTo("123");
    }

    /**
     * If the JSON-LD identifier is mapped to a non-string value, it fails later on.
     */
    @Test(expected = IllegalStateException.class)
    public void replaceIdentifierWithNonString() {
        BdioObject bdioObject = new BdioObject();
        bdioObject.put("@id", 123);
        bdioObject.id();
    }

    /**
     * The value returned from the {@link BdioObject#id()} method should not include a fragment.
     */
    @Test
    public void idIgnoresFragment() {
        BdioObject bdioObject = new BdioObject();
        bdioObject.put("@id", "http://example.com/test#ignored");
        assertThat(bdioObject.id()).isEqualTo("http://example.com/test");
        assertThat(bdioObject.get("@id")).isEqualTo("http://example.com/test#ignored");
    }

    /**
     * The JSON-LD scanType can be replaced by putting a new value into the map if you know the key.
     */
    @Test
    public void replaceScanType() {
        BdioObject bdioObject = new BdioObject();
        assertThat(bdioObject.scanType()).isNull();
        bdioObject.put("@type", Bdio.ScanType.PACKAGE_MANAGER.getValue());
        assertThat(bdioObject.scanType()).isEqualTo(Bdio.ScanType.PACKAGE_MANAGER.getValue());
    }
}
