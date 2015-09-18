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
package com.blackducksoftware.bom.io;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxType;

public class LinkedDataContextTest {

    @Test
    public void testVocabTypeCompactValue() {
        assertThat(new LinkedDataContext().compactValue(JsonLdTerm.TYPE, BlackDuckType.FILE))
                .isEqualTo("File");
    }

    @Test
    public void testPrefixTypeCompactValue() {
        assertThat(new LinkedDataContext().compactValue(JsonLdTerm.TYPE, SpdxType.FILE))
                .isEqualTo("spdx:File");
    }

    @Test
    public void testVocabTypeExpandValue() {
        assertThat(new LinkedDataContext().expandValue(JsonLdTerm.TYPE, "File"))
                .isEqualTo(BlackDuckType.FILE.toString());
    }

    @Test
    public void testPrefixTypeExpandValue() {
        assertThat(new LinkedDataContext().expandValue(JsonLdTerm.TYPE, "spdx:File"))
                .isEqualTo(SpdxType.FILE.toString());
    }

    @Test
    public void testVocabCompactTerm() {
        assertThat(new LinkedDataContext().compactTerm(BlackDuckTerm.CONTENT_TYPE))
                .isEqualTo("contentType");
    }

    @Test
    public void testPrefixCompactTerm() {
        assertThat(new LinkedDataContext().compactTerm(SpdxTerm.AGENT))
                .isEqualTo("spdx:agent");
    }

    @Test
    public void testVocabExpandTerm() {
        assertThat(new LinkedDataContext().expandTerm("contentType"))
                .isEqualTo(BlackDuckTerm.CONTENT_TYPE.toString());
    }

    @Test
    public void testPrefixExpandTerm() {
        assertThat(new LinkedDataContext().expandTerm("spdx:agent"))
                .isEqualTo(SpdxTerm.AGENT.toString());
    }

}
