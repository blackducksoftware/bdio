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
package com.blackducksoftware.bdio.model;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import com.blackducksoftware.bdio.BlackDuckValue;

/**
 * Tests for {@code ExternalIdentifierBuilder}.
 *
 * @author jgustie
 */
public class ExternalIdentifierBuilderTest {

    @Test
    public void fromId() {
        assertThat(ExternalIdentifierBuilder.create().fromId(null).build()).isAbsent();
        assertThat(ExternalIdentifierBuilder.create().fromId("foobar").build()).isPresent();
        assertThat(ExternalIdentifierBuilder.create().fromId("foobar").build().get().getExternalId()).isEqualTo("foobar");
    }

    @Test(expected = NullPointerException.class)
    public void formatIdNullFormat() {
        ExternalIdentifierBuilder.create().formatId(null);
    }

    @Test
    public void formatId() {
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
    public void blackDuckSuite() {
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

    @Test
    public void maven_gav() {
        ExternalIdentifier extIdWithVersion = ExternalIdentifierBuilder.create().maven("com.example", "example", "1.0").build().get();
        assertThat(extIdWithVersion.getExternalSystemTypeId()).isEqualTo(BlackDuckValue.EXTERNAL_IDENTIFIER_MAVEN.id());
        assertThat(extIdWithVersion.getExternalId()).isEqualTo("com.example:example:1.0");
        assertThat(extIdWithVersion.getExternalRepositoryLocation()).isNull();

        ExternalIdentifier extIdNoVersion = ExternalIdentifierBuilder.create().maven("com.example", "example", null).build().get();
        assertThat(extIdNoVersion.getExternalSystemTypeId()).isEqualTo(BlackDuckValue.EXTERNAL_IDENTIFIER_MAVEN.id());
        assertThat(extIdNoVersion.getExternalId()).isEqualTo("com.example:example");
        assertThat(extIdNoVersion.getExternalRepositoryLocation()).isNull();
    }

    @Test
    public void maven_gav_absent() {
        assertThat(ExternalIdentifierBuilder.create().maven(null, "example", "1.0").build()).isAbsent();
        assertThat(ExternalIdentifierBuilder.create().maven("com.example", null, "1.0").build()).isAbsent();
        assertThat(ExternalIdentifierBuilder.create().maven("com.example", "example", null).build()).isPresent();
    }

    @Test
    public void maven_packaging_classifier() {
        // This is the same as the GAV test
        ExternalIdentifier extIdNoPackagingOrClassifier = ExternalIdentifierBuilder.create().maven("com.example", "example", null, null, "1.0").build().get();
        assertThat(extIdNoPackagingOrClassifier.getExternalId()).isEqualTo("com.example:example:1.0");

        // You need a packaging to get a classifier
        ExternalIdentifier extIdNoPackaging = ExternalIdentifierBuilder.create().maven("com.example", "example", null, "jdk5", "1.0").build().get();
        assertThat(extIdNoPackaging.getExternalId()).isEqualTo("com.example:example:1.0");

        // Just packaging
        ExternalIdentifier extIdPackaging = ExternalIdentifierBuilder.create().maven("com.example", "example", "jar", null, "1.0").build().get();
        assertThat(extIdPackaging.getExternalId()).isEqualTo("com.example:example:jar:1.0");

        // Packaging and classifier
        ExternalIdentifier extIdPackagingAndClassifier = ExternalIdentifierBuilder.create().maven("com.example", "example", "jar", "jdk5", "1.0").build().get();
        assertThat(extIdPackagingAndClassifier.getExternalId()).isEqualTo("com.example:example:jar:jdk5:1.0");
    }

}
