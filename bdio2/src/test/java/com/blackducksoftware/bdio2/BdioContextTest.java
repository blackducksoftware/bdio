/*
 * Copyright 2018 Synopsys, Inc.
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
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collector;

import org.junit.Test;

import com.blackducksoftware.common.value.Digest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class BdioContextTest {

    private static final BdioContext DEFAULT_CONTEXT = new BdioContext.Builder().expandContext(Bdio.Context.DEFAULT).build();

    /**
     * A default typed data property will be mapped directly.
     */
    @Test
    public void putDefaultTypeDataProperty() {
        BdioContext context = DEFAULT_CONTEXT;
        Map<String, Object> map = new LinkedHashMap<>();

        context.putFieldValue(map, Bdio.DataProperty.name, "foobar");
        assertThat(map).containsEntry(Bdio.DataProperty.name.toString(), "foobar");
    }

    /**
     * A single valued data property will replace previous mappings.
     */
    @Test
    public void putSingleDataProperty() {
        BdioContext context = DEFAULT_CONTEXT;
        Map<String, Object> map = new LinkedHashMap<>();

        context.putFieldValue(map, Bdio.DataProperty.name, "foo");
        assertThat(context.putFieldValue(map, Bdio.DataProperty.name, "bar")).isEqualTo("bar");
        assertThat(map).containsEntry(Bdio.DataProperty.name.toString(), "bar");
    }

    /**
     * Putting a {@code null} single valued data property removes previous mapping.
     */
    @Test
    public void putSingleDataPropertyNullValue() {
        BdioContext context = DEFAULT_CONTEXT;
        Map<String, Object> map = new LinkedHashMap<>();

        context.putFieldValue(map, Bdio.DataProperty.name, "foo");
        assertThat(context.putFieldValue(map, Bdio.DataProperty.name, null)).isNull();
        assertThat(map).doesNotContainKey(Bdio.DataProperty.name.toString());
    }

    /**
     * A multivalued valued data property will append to previous mappings.
     */
    @Test
    public void putMultivaluedDataProperty() {
        BdioContext context = DEFAULT_CONTEXT;
        Map<String, Object> map = new LinkedHashMap<>();

        context.putFieldValue(map, Bdio.DataProperty.fingerprint, new Digest.Builder().algorithm("test").value("123").build());
        context.putFieldValue(map, Bdio.DataProperty.fingerprint, new Digest.Builder().algorithm("test").value("456").build());
        context.putFieldValue(map, Bdio.DataProperty.fingerprint, new Digest.Builder().algorithm("test").value("789").build());
        assertThat(map.get(Bdio.DataProperty.fingerprint.toString())).isInstanceOf(List.class);
        assertThat((List<?>) map.get(Bdio.DataProperty.fingerprint.toString())).containsExactly(
                ImmutableMap.of("@type", Bdio.Datatype.Digest.toString(), "@value", "test:123"),
                ImmutableMap.of("@type", Bdio.Datatype.Digest.toString(), "@value", "test:456"),
                ImmutableMap.of("@type", Bdio.Datatype.Digest.toString(), "@value", "test:789"));
    }

    /**
     * A {@code null} multivalued valued data property will not effect previous mappings.
     */
    @Test
    public void putMultivaluedDataPropertyNullValue() {
        BdioContext context = DEFAULT_CONTEXT;
        Map<String, Object> map = new LinkedHashMap<>();

        context.putFieldValue(map, Bdio.DataProperty.fingerprint, new Digest.Builder().algorithm("test").value("123").build());
        context.putFieldValue(map, Bdio.DataProperty.fingerprint, null);
        assertThat((List<?>) map.get(Bdio.DataProperty.fingerprint.toString())).containsExactly(
                ImmutableMap.of("@type", Bdio.Datatype.Digest.toString(), "@value", "test:123"));

        context.putFieldValue(map, Bdio.DataProperty.fingerprint, new Digest.Builder().algorithm("test").value("456").build());
        context.putFieldValue(map, Bdio.DataProperty.fingerprint, null);
        assertThat((List<?>) map.get(Bdio.DataProperty.fingerprint.toString())).containsExactly(
                ImmutableMap.of("@type", Bdio.Datatype.Digest.toString(), "@value", "test:123"),
                ImmutableMap.of("@type", Bdio.Datatype.Digest.toString(), "@value", "test:456"));
    }

    /**
     * A date-time value is serialized as string in a value object.
     */
    @Test
    public void putDateTimeTypeDataProperty() {
        BdioContext context = DEFAULT_CONTEXT;
        Map<String, Object> map = new LinkedHashMap<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        context.putFieldValue(map, Bdio.DataProperty.creationDateTime, now);
        assertThat(map).containsEntry(Bdio.DataProperty.creationDateTime.toString(),
                ImmutableMap.of("@type", Bdio.Datatype.DateTime.toString(), "@value", now.toString()));
    }

    /**
     * A long value is serialized directly in a value object.
     */
    @Test
    public void putLongTypeDataProperty() {
        BdioContext context = DEFAULT_CONTEXT;
        Map<String, Object> map = new LinkedHashMap<>();

        context.putFieldValue(map, Bdio.DataProperty.byteCount, 1L);
        assertThat(map).containsEntry(Bdio.DataProperty.byteCount.toString(),
                ImmutableMap.of("@type", Bdio.Datatype.Long.toString(), "@value", 1L));
    }

    /**
     * An object property produces a value object with a type of "@id".
     */
    @Test
    public void putSingleObjectProperty() {
        BdioContext context = DEFAULT_CONTEXT;
        String currentVersionId = BdioObject.randomId();
        Map<String, Object> map = new LinkedHashMap<>();

        context.putFieldValue(map, Bdio.ObjectProperty.parent, currentVersionId);
        assertThat(map).containsEntry(Bdio.ObjectProperty.parent.toString(),
                ImmutableMap.of("@id", currentVersionId));
    }

    /**
     * Putting a {@code null} single valued object property removes previous mapping.
     */
    @Test
    public void putSingleObjectPropertyNullValue() {
        BdioContext context = DEFAULT_CONTEXT;
        Map<String, Object> map = new LinkedHashMap<>();

        context.putFieldValue(map, Bdio.ObjectProperty.parent, BdioObject.randomId());
        assertThat(context.putFieldValue(map, Bdio.ObjectProperty.parent, null)).isNull();
        assertThat(map).doesNotContainKey(Bdio.ObjectProperty.parent.toString());
    }

    @Test
    public void fromFieldValue_identifier() {
        BdioContext context = DEFAULT_CONTEXT;

        // If the only value in the map is the identifier, it is safe to extract
        assertThat(context.fromFieldValue("test", ImmutableMap.of("@id", "test"))).isEqualTo("test");

        // This is important because if there are multiple values in the map, we can no longer just take the identifier
        assertThat(context.fromFieldValue("test", ImmutableMap.of("@id", "test", "foo", "bar"))).isEqualTo(ImmutableMap.of("@id", "test", "foo", "bar"));
    }

    @Test
    public void fromFieldValue_primitive() {
        BdioContext context = DEFAULT_CONTEXT;

        // Primitives flow through
        assertThat(context.fromFieldValue("test", ImmutableMap.of("@value", "test"))).isEqualTo("test");
        assertThat(context.fromFieldValue("test", ImmutableMap.of("@value", Boolean.TRUE))).isEqualTo(Boolean.TRUE);
        assertThat(context.fromFieldValue("test", ImmutableMap.of("@value", Integer.valueOf(1)))).isEqualTo(Integer.valueOf(1));
    }

    @Test
    public void fromFieldValue_dateTime() {
        BdioContext context = DEFAULT_CONTEXT;

        ZonedDateTime zoned = ZonedDateTime.now(ZoneId.systemDefault());
        assertThat(context.fromFieldValue("test", ImmutableMap.of("@type", Bdio.Datatype.DateTime.toString(), "@value", zoned.toString())))
                .named("zoned").isEqualTo(zoned);

        OffsetDateTime offset = OffsetDateTime.now(ZoneId.systemDefault());
        assertThat(context.fromFieldValue("test", ImmutableMap.of("@type", Bdio.Datatype.DateTime.toString(), "@value", offset.toString())))
                .named("offset").isEqualTo(offset.toZonedDateTime());

        Instant instant = Instant.now();
        assertThat(context.fromFieldValue("test", ImmutableMap.of("@type", Bdio.Datatype.DateTime.toString(), "@value", instant.toString())))
                .named("instant").isEqualTo(instant.atZone(ZoneOffset.UTC));

        Date date = new Date();
        assertThat(context.fromFieldValue("test", ImmutableMap.of("@type", Bdio.Datatype.DateTime.toString(), "@value", date)))
                .named("date").isEqualTo(date.toInstant().atZone(ZoneOffset.UTC));
    }

    @Test
    public void fromFieldValue_multiValueKey() {
        String exampleContext = "{\"@context\":{\"@vocab\":\"http://example.com/\",\"test2\":{\"@id\":\"http://example.com/test2\",\"@container\":\"@list\"}}}";
        BdioContext context = new BdioContext.Builder()
                .injectDocument("http://example.com/context", exampleContext)
                .expandContext("http://example.com/context")
                .build();

        assertThat(context.fromFieldValue("test", ImmutableList.of("foobar"))).isEqualTo("foobar");
        assertThat(context.fromFieldValue("test2", ImmutableList.of("foobar"))).isEqualTo(ImmutableList.of("foobar"));
        assertThat(context.fromFieldValue("test2", "foobar")).isEqualTo(ImmutableList.of("foobar"));
    }

    @Test
    public void fromFieldValue_multiValueCollector_stringArray() {
        String exampleContext = "{\"@context\":{\"@vocab\":\"http://example.com/\",\"test2\":{\"@id\":\"http://example.com/test2\",\"@container\":\"@list\"}}}";
        BdioContext context = new BdioContext.Builder()
                .injectDocument("http://example.com/context", exampleContext)
                .expandContext("http://example.com/context")
                .valueMapper(new StandardJavaValueMapper() {
                    @Override
                    public Collector<? super Object, ?, ?> getCollector(String container) {
                        // This is a collector that converts multiple values into a `String[]`
                        return collectingAndThen(mapping(o -> Objects.toString(o, null), toList()),
                                l -> l.stream().allMatch(Objects::isNull) ? null : l.toArray(new String[l.size()]));
                    }
                })
                .build();

        assertThat((String[]) context.fromFieldValue("test2", ImmutableList.of("foobar"))).isEqualTo(new String[] { "foobar" });
        assertThat((String[]) context.fromFieldValue("test2", Arrays.asList((String) null))).isNull();
    }

    @Test
    public void fromFieldValue_singleValueFromMultipleNull() {
        BdioContext context = new BdioContext.Builder().build();
        assertThat(context.fromFieldValue("test", Arrays.asList(null, null))).isNull();
    }

}
