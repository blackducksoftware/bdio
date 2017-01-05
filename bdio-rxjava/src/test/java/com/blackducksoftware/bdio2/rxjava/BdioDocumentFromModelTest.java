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
package com.blackducksoftware.bdio2.rxjava;

import static com.blackducksoftware.common.test.JsonSubject.assertThatJson;
import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import org.junit.Test;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.test.BdioTest;
import com.blackducksoftware.common.io.HeapOutputStream;

import io.reactivex.Flowable;

/**
 * Tests verifying we can generate BDIO documents from model objects.
 *
 * @author jgustie
 */
public class BdioDocumentFromModelTest {

    @Test
    public void singleNode() {
        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        HeapOutputStream out = new HeapOutputStream();
        BdioDocument doc = new BdioDocument.Builder().build(RxJavaBdioDocument.class).writeToFile(metadata, out);

        Flowable.just(new File("http://example.com/files/1")).subscribe(doc.asNodeSubscriber(metadata));

        List<String> entries = BdioTest.zipEntries(out.getInputStream());
        assertThat(entries).hasSize(2);
        assertThatJson(entries.get(1)).at("/@graph/0/@id").isEqualTo("http://example.com/files/1");
    }

}
