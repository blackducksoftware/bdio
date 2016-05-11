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

import com.blackducksoftware.bom.BlackDuckValue;

/**
 * Tests for {@code ExternalIdentifierBuilder}.
 *
 * @author jgustie
 */
public class ExternalIdentifierBuilderTest {

    @Test
    public void testFromId() {
        assertThat(ExternalIdentifierBuilder.create().fromId(null).build()).isAbsent();
        assertThat(ExternalIdentifierBuilder.create().fromId("foobar").build()).isPresent();
        assertThat(ExternalIdentifierBuilder.create().fromId("foobar").build().get().getExternalId()).isEqualTo("foobar");
    }

    @Test(expected = NullPointerException.class)
    public void testFormatIdNullFormat() {
        ExternalIdentifierBuilder.create().formatId(null);
    }

    @Test
    public void testFormatId() {
        assertThat(ExternalIdentifierBuilder.create().formatId("foobar").build()).isPresent();
        assertThat(ExternalIdentifierBuilder.create().formatId("foobar").build().get().getExternalId()).isEqualTo("foobar");
        assertThat(ExternalIdentifierBuilder.create().formatId("%s", "foobar").build()).isPresent();
        assertThat(ExternalIdentifierBuilder.create().formatId("%s", "foobar").build().get().getExternalId()).isEqualTo("foobar");
        assertThat(ExternalIdentifierBuilder.create().formatId("%s", (Object) null).build()).isAbsent();
        assertThat(ExternalIdentifierBuilder.create().formatId("%s-%s", null, null).build()).isAbsent();
        assertThat(ExternalIdentifierBuilder.create().formatId("%s-%s", null, "foobar").build()).isAbsent();
        assertThat(ExternalIdentifierBuilder.create().formatId("%s-%s", "foobar", null).build()).isAbsent();
        assertThat(ExternalIdentifierBuilder.create().formatId("%s-%s", "foo", "bar").build().get().getExternalId()).isEqualTo("foo-bar");
    }

    @Test
    public void testBlackDuckSuite() {
        assertThat(ExternalIdentifierBuilder.create().blackDuckSuite(null).build()).isAbsent();

        ExternalIdentifier extIdTagless = ExternalIdentifierBuilder.create().blackDuckSuite("c_foobar_5000").build().get();
        assertThat(extIdTagless.getExternalSystemTypeId()).isEqualTo(BlackDuckValue.EXTERNAL_IDENTIFIER_BDSUITE.id());
        assertThat(extIdTagless.getExternalId()).isEqualTo("c_foobar_5000");
        assertThat(extIdTagless.getExternalRepositoryLocation()).isNull();

        assertThat(ExternalIdentifierBuilder.create().blackDuckSuite(null, "123").build()).isAbsent();

        ExternalIdentifier extIdWithNullTag = ExternalIdentifierBuilder.create().blackDuckSuite("c_foobar_5000", null).build().get();
        assertThat(extIdWithNullTag.getExternalSystemTypeId()).isEqualTo(BlackDuckValue.EXTERNAL_IDENTIFIER_BDSUITE.id());
        assertThat(extIdWithNullTag.getExternalId()).isEqualTo("c_foobar_5000");
        assertThat(extIdWithNullTag.getExternalRepositoryLocation()).isNull();

        ExternalIdentifier extIdWithTag = ExternalIdentifierBuilder.create().blackDuckSuite("c_foobar_5000", "123").build().get();
        assertThat(extIdWithTag.getExternalSystemTypeId()).isEqualTo(BlackDuckValue.EXTERNAL_IDENTIFIER_BDSUITE.id());
        assertThat(extIdWithTag.getExternalId()).isEqualTo("c_foobar_5000#123");
        assertThat(extIdWithTag.getExternalRepositoryLocation()).isNull();
    }

}
