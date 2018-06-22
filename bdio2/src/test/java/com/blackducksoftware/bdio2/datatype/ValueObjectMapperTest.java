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
package com.blackducksoftware.bdio2.datatype;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import org.junit.Test;

import com.blackducksoftware.bdio2.Bdio;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Tests for {@link ValueObjectMapper}.
 *
 * @author jgustie
 */
public class ValueObjectMapperTest {

    @Test
    public void fromFieldValue_identifier() {
        ValueObjectMapper mapper = new ValueObjectMapper.Builder().build();

        // If the only value in the map is the identifier, it is safe to extract
        assertThat(mapper.fromFieldValue("test", ImmutableMap.of("@id", "test"))).isEqualTo("test");

        // This is important because if there are multiple values in the map, we can no longer just take the identifier
        assertThat(mapper.fromFieldValue("test", ImmutableMap.of("@id", "test", "foo", "bar"))).isEqualTo(ImmutableMap.of("@id", "test", "foo", "bar"));
    }

    @Test
    public void fromFieldValue_primitive() {
        ValueObjectMapper mapper = new ValueObjectMapper.Builder().build();

        // Primitives flow through
        assertThat(mapper.fromFieldValue("test", ImmutableMap.of("@value", "test"))).isEqualTo("test");
        assertThat(mapper.fromFieldValue("test", ImmutableMap.of("@value", Boolean.TRUE))).isEqualTo(Boolean.TRUE);
        assertThat(mapper.fromFieldValue("test", ImmutableMap.of("@value", Integer.valueOf(1)))).isEqualTo(Integer.valueOf(1));
    }

    @Test
    public void fromFieldValue_dateTime() {
        ValueObjectMapper mapper = new ValueObjectMapper.Builder().build();

        ZonedDateTime zoned = ZonedDateTime.now();
        assertThat(mapper.fromFieldValue("test", ImmutableMap.of("@type", Bdio.Datatype.DateTime.toString(), "@value", zoned.toString())))
                .named("zoned").isEqualTo(zoned);

        OffsetDateTime offset = OffsetDateTime.now();
        assertThat(mapper.fromFieldValue("test", ImmutableMap.of("@type", Bdio.Datatype.DateTime.toString(), "@value", offset.toString())))
                .named("offset").isEqualTo(offset.toZonedDateTime());

        Instant instant = Instant.now();
        assertThat(mapper.fromFieldValue("test", ImmutableMap.of("@type", Bdio.Datatype.DateTime.toString(), "@value", instant.toString())))
                .named("instant").isEqualTo(instant.atZone(ZoneOffset.UTC));

        Date date = new Date();
        assertThat(mapper.fromFieldValue("test", ImmutableMap.of("@type", Bdio.Datatype.DateTime.toString(), "@value", date)))
                .named("date").isEqualTo(date.toInstant().atZone(ZoneOffset.UTC));
    }

    @Test
    public void fromFieldValue_multiValueKey() {
        ValueObjectMapper mapper = new ValueObjectMapper.Builder().addMultiValueKey("test2").build();
        assertThat(mapper.fromFieldValue("test", ImmutableList.of("foobar"))).isEqualTo("foobar");
        assertThat(mapper.fromFieldValue("test2", ImmutableList.of("foobar"))).isEqualTo(ImmutableList.of("foobar"));
        assertThat(mapper.fromFieldValue("test2", "foobar")).isEqualTo(ImmutableList.of("foobar"));
    }

    @Test
    public void fromFieldValue_multiValueCollector_stringArray() {
        // This is a collector that converts multiple values into a `String[]`
        ValueObjectMapper mapper = new ValueObjectMapper.Builder()
                .addMultiValueKey("test2")
                .multiValueCollector(collectingAndThen(mapping(o -> Objects.toString(o, null), toList()),
                        l -> l.stream().allMatch(Objects::isNull) ? null : l.toArray(new String[l.size()])))
                .build();

        assertThat((String[]) mapper.fromFieldValue("test2", ImmutableList.of("foobar"))).isEqualTo(new String[] { "foobar" });
        assertThat((String[]) mapper.fromFieldValue("test2", Arrays.asList((String) null))).isNull();
    }

    @Test
    public void fromFieldValue_singleValueFromMultipleNull() {
        ValueObjectMapper mapper = new ValueObjectMapper.Builder().build();
        assertThat(mapper.fromFieldValue("test", Arrays.asList(null, null))).isNull();
    }

}
