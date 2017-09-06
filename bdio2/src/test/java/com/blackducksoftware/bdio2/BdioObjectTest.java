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

import static com.google.common.truth.Truth.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.Test;

import com.blackducksoftware.bdio2.datatype.Fingerprint;
import com.google.common.collect.ImmutableMap;

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
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());

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
        new BdioObject(ImmutableMap.of()).put(null, "foo");
    }

    /**
     * Putting a {@code null} value into the map removes the entry.
     */
    @Test
    public void putNullValueIsRemove() {
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
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
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
        assertThat(bdioObject.id()).isNull();
        bdioObject.put("@id", "123");
        assertThat(bdioObject.id()).isEqualTo("123");
    }

    /**
     * If the JSON-LD identifier is mapped to a non-string value, it fails later on.
     */
    @Test(expected = IllegalStateException.class)
    public void replaceIdentifierWithNonString() {
        new BdioObject(ImmutableMap.of("@id", 123)).id();
    }

    /**
     * A default typed data property will be mapped directly.
     */
    @Test
    public void putDefaultTypeDataProperty() {
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
        bdioObject.putData(Bdio.DataProperty.name, "foobar");
        assertThat(bdioObject).containsEntry(Bdio.DataProperty.name.toString(), "foobar");
    }

    /**
     * A single valued data property will replace previous mappings.
     */
    @Test
    public void putSingleDataProperty() {
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
        bdioObject.putData(Bdio.DataProperty.name, "foo");
        assertThat(bdioObject.putData(Bdio.DataProperty.name, "bar")).isEqualTo("bar");
        assertThat(bdioObject).containsEntry(Bdio.DataProperty.name.toString(), "bar");
    }

    /**
     * Putting a {@code null} single valued data property removes previous mapping.
     */
    @Test
    public void putSingleDataPropertyNullValue() {
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
        bdioObject.putData(Bdio.DataProperty.name, "foo");
        assertThat(bdioObject.putData(Bdio.DataProperty.name, null)).isNull();
        assertThat(bdioObject).doesNotContainKey(Bdio.DataProperty.name.toString());
    }

    /**
     * A multivalued valued data property will append to previous mappings.
     */
    @Test
    public void putMultivaluedDataProperty() {
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
        bdioObject.putData(Bdio.DataProperty.fingerprint, Fingerprint.create("test", "123"));
        bdioObject.putData(Bdio.DataProperty.fingerprint, Fingerprint.create("test", "456"));
        bdioObject.putData(Bdio.DataProperty.fingerprint, Fingerprint.create("test", "789"));
        assertThat(bdioObject.get(Bdio.DataProperty.fingerprint.toString())).isInstanceOf(List.class);
        assertThat((List<?>) bdioObject.get(Bdio.DataProperty.fingerprint.toString())).containsExactly(
                ImmutableMap.of("@type", Bdio.Datatype.Fingerprint.toString(), "@value", "test:123"),
                ImmutableMap.of("@type", Bdio.Datatype.Fingerprint.toString(), "@value", "test:456"),
                ImmutableMap.of("@type", Bdio.Datatype.Fingerprint.toString(), "@value", "test:789"));
    }

    /**
     * A {@code null} multivalued valued data property will not effect previous mappings.
     */
    @Test
    public void putMultivaluedDataPropertyNullValue() {
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
        bdioObject.putData(Bdio.DataProperty.fingerprint, Fingerprint.create("test", "123"));
        bdioObject.putData(Bdio.DataProperty.fingerprint, null);
        assertThat(bdioObject.get(Bdio.DataProperty.fingerprint.toString()))
                .isEqualTo(ImmutableMap.of("@type", Bdio.Datatype.Fingerprint.toString(), "@value", "test:123"));

        bdioObject.putData(Bdio.DataProperty.fingerprint, Fingerprint.create("test", "456"));
        bdioObject.putData(Bdio.DataProperty.fingerprint, null);
        assertThat(bdioObject.get(Bdio.DataProperty.fingerprint.toString())).isInstanceOf(List.class);
        assertThat((List<?>) bdioObject.get(Bdio.DataProperty.fingerprint.toString())).containsExactly(
                ImmutableMap.of("@type", Bdio.Datatype.Fingerprint.toString(), "@value", "test:123"),
                ImmutableMap.of("@type", Bdio.Datatype.Fingerprint.toString(), "@value", "test:456"));
    }

    /**
     * A date-time value is serialized as string in a value object.
     */
    @Test
    public void putDateTimeTypeDataProperty() {
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
        ZonedDateTime now = ZonedDateTime.now();
        bdioObject.putData(Bdio.DataProperty.creationDateTime, now);
        assertThat(bdioObject).containsEntry(Bdio.DataProperty.creationDateTime.toString(),
                ImmutableMap.of("@type", Bdio.Datatype.DateTime.toString(), "@value", now.toString()));
    }

    /**
     * A long value is serialized directly in a value object.
     */
    @Test
    public void putLongTypeDataProperty() {
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
        bdioObject.putData(Bdio.DataProperty.byteCount, 1L);
        assertThat(bdioObject).containsEntry(Bdio.DataProperty.byteCount.toString(), 1L);
    }

    /**
     * An object property produces a value object with a type of "@id".
     */
    @Test
    public void putSingleObjectProperty() {
        String currentVersionId = BdioObject.randomId();
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
        bdioObject.putObject(Bdio.ObjectProperty.parent, currentVersionId);
        assertThat(bdioObject).containsEntry(Bdio.ObjectProperty.parent.toString(),
                ImmutableMap.of("@value", currentVersionId));
    }

    /**
     * Putting a {@code null} single valued object property removes previous mapping.
     */
    @Test
    public void putSingleObjectPropertyNullValue() {
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
        bdioObject.putObject(Bdio.ObjectProperty.parent, BdioObject.randomId());
        assertThat(bdioObject.putObject(Bdio.ObjectProperty.parent, null)).isNull();
        assertThat(bdioObject).doesNotContainKey(Bdio.ObjectProperty.parent.toString());
    }

    /**
     * The value returned from the {@link BdioObject#id()} method should not include a fragment.
     */
    @Test
    public void idIgnoresFragment() {
        BdioObject bdioObject = new BdioObject(ImmutableMap.of());
        bdioObject.put("@id", "http://example.com/test#ignored");
        assertThat(bdioObject.id()).isEqualTo("http://example.com/test");
        assertThat(bdioObject.get("@id")).isEqualTo("http://example.com/test#ignored");
    }

}