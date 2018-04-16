/*
 * Copyright 2018 Black Duck Software, Inc.
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

import static com.blackducksoftware.common.base.ExtraStreams.stream;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import java.util.stream.Stream;

import org.junit.Test;

import com.google.common.base.Enums;

/**
 * Try keeping the code clean by keeping things sorted alphabetically.
 *
 * @author jgustie
 */
public class BdioDataModelSortOrderTest {

    @Test
    public void bdioClassOrdering() {
        assertThat(stream(Bdio.Class.class).map(Bdio.Class::name).collect(toList())).isOrdered();
    }

    @Test
    public void bdioObjectPropertyOrdering() {
        assertThat(stream(Bdio.ObjectProperty.class).map(Bdio.ObjectProperty::name).collect(toList())).isOrdered();
    }

    @Test
    public void bdioDataPropertyOrdering() {
        assertThat(stream(Bdio.DataProperty.class).map(Bdio.DataProperty::name).collect(toList())).isOrdered();
    }

    @Test
    public void bdioDatatypeOrdering() {
        assertThat(stream(Bdio.Datatype.class).map(Bdio.Datatype::name).collect(toList())).isOrdered();
    }

    @Test
    public void bdioObjectPropertyAllowOnOrdering() {
        for (Bdio.ObjectProperty objectProperty : Bdio.ObjectProperty.values()) {
            assertThat(Stream.of(Enums.getField(objectProperty).getAnnotation(Bdio.Domain.class).value()).map(Bdio.Class::name).collect(toList()))
                    .named(objectProperty.name()).isOrdered();
        }
    }

    @Test
    public void bdioDataPropertyAllowOnOrdering() {
        for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
            assertThat(Stream.of(Enums.getField(dataProperty).getAnnotation(Bdio.Domain.class).value()).map(Bdio.Class::name).collect(toList()))
                    .named(dataProperty.name()).isOrdered();
        }
    }

}
