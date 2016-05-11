/*
 * Copyright 2015 Black Duck Software, Inc.
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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import com.blackducksoftware.bom.model.TestModel.TestTerm;
import com.google.common.hash.HashCode;

/**
 * Tests for the abstract model.
 *
 * @author jgustie
 */
public class AbstractModelTest {

    @Test
    public void testDataInitiallyEmpty() {
        assertThat(new TestModel().data()).isEmpty();
    }

    @Test
    public void testDataPutString() {
        TestModel test = new TestModel();
        test.data().put(TestTerm.NAME, "foobar");
        assertThat(test.data()).hasSize(1);
        assertThat(test.data()).containsKey(TestTerm.NAME);
        assertThat(test.data().get(TestTerm.NAME)).isEqualTo("foobar");
        assertThat(test.getName()).isEqualTo("foobar");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDataPutNull() {
        new TestModel().data().put(TestTerm.NAME, null);
    }

    @Test
    public void testDataRemove() {
        TestModel test = new TestModel();
        test.setName("foobar");
        assertThat(test.data()).hasSize(1);
        assertThat(test.data()).containsKey(TestTerm.NAME);
        test.data().remove(TestTerm.NAME);
        assertThat(test.data()).isEmpty();
    }

    @Test
    public void testDataRemoveConvertedField() {
        TestModel test = new TestModel();
        test.setHashCode(HashCode.fromInt(0));
        test.data().remove(TestTerm.HASH_CODE);
        assertThat(test.getHashCode()).isNull();
    }

    @Test
    public void testDataPutBoolean() {
        TestModel test = new TestModel();
        test.data().put(TestTerm.FLAG, Boolean.TRUE);
        assertThat(test.data().get(TestTerm.FLAG)).isEqualTo(Boolean.TRUE);
        assertThat(test.getFlag()).isTrue();
        test.data().put(TestTerm.FLAG, "false");
        assertThat(test.data().get(TestTerm.FLAG)).isEqualTo(Boolean.FALSE);
        assertThat(test.getFlag()).isFalse();
    }

    @Test
    public void testExtraData() {
        TestModel test = new TestModel();
        test.data().put(TestTerm.UNMAPPED, "FooBar");
        assertThat(test.data()).containsKey(TestTerm.UNMAPPED);
        assertThat(test.data().get(TestTerm.UNMAPPED)).isEqualTo("FooBar");
        test.data().put(TestTerm.UNMAPPED, 1);
        assertThat(test.data().get(TestTerm.UNMAPPED)).isEqualTo(1);

        test.data().remove(TestTerm.UNMAPPED);
        assertThat(test.data()).doesNotContainKey(TestTerm.UNMAPPED);
    }

    @Test
    public void testExtraDataWithRealData() {
        TestModel test = new TestModel();
        test.setName("FooBar");
        test.data().put(TestTerm.UNMAPPED, "BarFoo");

        assertThat(test.data()).hasSize(2);
        assertThat(test.data()).containsEntry(TestTerm.NAME, "FooBar");
        assertThat(test.data()).containsEntry(TestTerm.UNMAPPED, "BarFoo");
    }

}
