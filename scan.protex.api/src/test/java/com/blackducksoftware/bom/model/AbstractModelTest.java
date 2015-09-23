/*
 * Copyright (C) 2015 Black Duck Software Inc.
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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import com.blackducksoftware.bom.model.TestModel.TestTerm;

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
    public void testDataPutBoolean() {
        TestModel test = new TestModel();
        test.data().put(TestTerm.FLAG, Boolean.TRUE);
        assertThat(test.data().get(TestTerm.FLAG)).isEqualTo(Boolean.TRUE);
        assertThat(test.getFlag()).isTrue();
        test.data().put(TestTerm.FLAG, "false");
        assertThat(test.data().get(TestTerm.FLAG)).isEqualTo(Boolean.FALSE);
        assertThat(test.getFlag()).isFalse();
    }

}
