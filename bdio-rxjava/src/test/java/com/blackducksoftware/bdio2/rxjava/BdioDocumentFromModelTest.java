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
package com.blackducksoftware.bdio2.rxjava;

import static com.blackducksoftware.common.test.JsonSubject.assertThatJson;
import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import org.junit.Test;

import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioWriter;
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
        RxJavaBdioDocument doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

        Flowable.just(new File("http://example.com/files/1"))
                .buffer(1)
                .subscribe(doc.write(metadata, new BdioWriter.BdioFile(out)));

        List<String> entries = BdioTest.zipEntries(out.getInputStream());
        assertThat(entries).hasSize(2);
        assertThatJson(entries.get(1)).at("/@graph/0/@id").isEqualTo("http://example.com/files/1");
    }

}
