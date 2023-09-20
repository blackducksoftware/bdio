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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioWriter;
import com.blackducksoftware.bdio2.EntrySizeViolationException;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.test.BdioTest;
import com.blackducksoftware.common.io.HeapOutputStream;
import com.blackducksoftware.common.value.ProductList;
import com.google.common.collect.MoreCollectors;

import io.reactivex.Flowable;
import io.reactivex.exceptions.CompositeException;

/**
 * Tests verifying we can generate BDIO documents from model objects.
 *
 * @author jgustie
 */
public class BdioDocumentFromModelTest {

    private final Logger logger = LoggerFactory.getLogger(BdioDocumentFromModelTest.class);

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

    @Test
    public void scanTypeTest() {
        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        metadata.scanType(Bdio.ScanType.PACKAGE_MANAGER);
        HeapOutputStream out = new HeapOutputStream();
        RxJavaBdioDocument doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

        Flowable.just(new File("http://example.com/files/1"))
                .buffer(1)
                .subscribe(doc.write(metadata, new BdioWriter.BdioFile(out)));

        // Validate how the doc reader can extract the scanType
        assertThat(doc.metadata(doc.read(out.getInputStream())).singleOrError().blockingGet().scanType()).isEqualTo(Bdio.ScanType.PACKAGE_MANAGER.getValue());

        // Validate all chunks have the ScanType information
        List<String> entries = BdioTest.zipEntries(out.getInputStream());
        assertThat(entries).hasSize(2);
        assertThatJson(entries.get(0)).at("/@type").isEqualTo(Bdio.ScanType.PACKAGE_MANAGER.getValue());
        assertThatJson(entries.get(1)).at("/@type").isEqualTo(Bdio.ScanType.PACKAGE_MANAGER.getValue());
    }

    @Test
    public void scanTypeIacZeroEntryTest() {
        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        metadata.scanType(Bdio.ScanType.INFRASTRUCTURE_AS_CODE);
        HeapOutputStream out = new HeapOutputStream();
        RxJavaBdioDocument doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

        // IaC scans just require the header document
        Flowable.empty().subscribe(doc.write(metadata, new BdioWriter.BdioFile(out)));

        // Validate how the doc reader can extract the scanType
        assertThat(doc.metadata(doc.read(out.getInputStream())).singleOrError().blockingGet().scanType()).isEqualTo(Bdio.ScanType.INFRASTRUCTURE_AS_CODE.getValue());

        // Validate all chunks have the ScanType information
        List<String> entries = BdioTest.zipEntries(out.getInputStream());
        assertThat(entries).hasSize(1);
        assertThatJson(entries.get(0)).at("/@type").isEqualTo(Bdio.ScanType.INFRASTRUCTURE_AS_CODE.getValue());
    }

    @Test
    public void scanTypeFromPublisherTest() {
        // Verify mapping of an older version Package Manager Scan
        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        metadata.publisher(ProductList.from("Detect/5.5.1 IntegrationBdio/18.0.0 LegacyBdio1xEmitter/3.0.0-beta.44 (bdio 1.1.0)"));
        HeapOutputStream out = new HeapOutputStream();
        RxJavaBdioDocument doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

        Flowable.just(new File("http://example.com/files/1"))
                .buffer(1)
                .subscribe(doc.write(metadata, new BdioWriter.BdioFile(out)));

        // Validate that the extracted scanType is correct
        assertThat(doc.metadata(doc.read(out.getInputStream())).singleOrError().blockingGet().scanType()).isEqualTo(Bdio.ScanType.PACKAGE_MANAGER.getValue());

        // Verify mapping of a newer Package Manager Scan
        metadata = BdioMetadata.createRandomUUID();
        metadata.publisher(ProductList.from("Java/11.0.10  MacOSX/10.15.7 (x86_64)"));
        out = new HeapOutputStream();
        doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

        Flowable.just(new File("http://example.com/files/1"))
                .buffer(1)
                .subscribe(doc.write(metadata, new BdioWriter.BdioFile(out)));

        // Validate that the extracted scanType is correct
        assertThat(doc.metadata(doc.read(out.getInputStream())).singleOrError().blockingGet().scanType()).isEqualTo(Bdio.ScanType.PACKAGE_MANAGER.getValue());

        // Verify mapping of a Signature Scan
        metadata = BdioMetadata.createRandomUUID();
        metadata.publisher(ProductList.from("ScanClient/5.5.1 IntegrationBdio/18.0.0 LegacyBdio1xEmitter/3.0.0-beta.44 (bdio 1.1.0)"));
        out = new HeapOutputStream();
        doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

        Flowable.just(new File("http://example.com/files/1"))
                .buffer(1)
                .subscribe(doc.write(metadata, new BdioWriter.BdioFile(out)));

        // Validate that the extracted scanType is correct
        assertThat(doc.metadata(doc.read(out.getInputStream())).singleOrError().blockingGet().scanType()).isEqualTo(Bdio.ScanType.SIGNATURE.getValue());

        // Verify mapping of a Binary Scan
        metadata = BdioMetadata.createRandomUUID();
        metadata.publisher(ProductList.from("Protecode-SC"));
        out = new HeapOutputStream();
        doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

        Flowable.just(new File("http://example.com/files/1"))
                .buffer(1)
                .subscribe(doc.write(metadata, new BdioWriter.BdioFile(out)));

        // Validate that the extracted scanType is correct
        assertThat(doc.metadata(doc.read(out.getInputStream())).singleOrError().blockingGet().scanType()).isEqualTo(Bdio.ScanType.BINARY.getValue());

        // Verify mapping of a Scan without product information
        metadata = BdioMetadata.createRandomUUID();
        metadata.publisher(null);
        out = new HeapOutputStream();
        doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

        Flowable.just(new File("http://example.com/files/1"))
                .buffer(1)
                .subscribe(doc.write(metadata, new BdioWriter.BdioFile(out)));

        // Validate that the extracted scanType is correct
        assertThat(doc.metadata(doc.read(out.getInputStream())).singleOrError().blockingGet().scanType()).isEqualTo(Bdio.ScanType.PACKAGE_MANAGER.getValue());
    }
    
    @Test
    public void testProjectRelatedMetadata() {
    	 String expectedProject = "project1";
    	 String expectedProjectVersion = "projectVersion1";
    	 String expectedProjectGroup = "projectGroup1";
    	 
    	 BdioMetadata metadata = BdioMetadata.createRandomUUID();
         metadata.project(expectedProject);
         metadata.projectVersion(expectedProjectVersion);
         metadata.projectGroup(expectedProjectGroup);
         
         HeapOutputStream out = new HeapOutputStream();
         RxJavaBdioDocument doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

         // write bdio document (including metadata) to in memory output stream
         Flowable.just(new File("http://example.com/files/1"))
                 .buffer(1)
                 .subscribe(doc.write(metadata, new BdioWriter.BdioFile(out)));

         // read bdio document and extract metadata fields
         BdioContext context = new BdioContext.Builder().expandContext(Bdio.Context.DEFAULT).build();
         BdioMetadata actualMetadata = doc.metadata(doc.read(out.getInputStream())).singleOrError().blockingGet();
         
         String actualProject = (String) context.getFieldValue(Bdio.DataProperty.project, actualMetadata).collect(MoreCollectors.toOptional()).get();
         String actualProjectVersion = (String) context.getFieldValue(Bdio.DataProperty.projectVersion, actualMetadata).collect(MoreCollectors.toOptional()).get();
         String actualProjectGroup = (String) context.getFieldValue(Bdio.DataProperty.projectGroup, actualMetadata).collect(MoreCollectors.toOptional()).get();

         assertThat(actualProject).isEqualTo(expectedProject);
         assertThat(actualProjectVersion).isEqualTo(expectedProjectVersion);
         assertThat(actualProjectGroup).isEqualTo(expectedProjectGroup);
    }
    
    @Test
    public void testCorrelationId() {
    	 String expectedCorrelationId = UUID.randomUUID().toString();
    	 
    	 BdioMetadata metadata = BdioMetadata.createRandomUUID();
         metadata.correlationId(expectedCorrelationId);
          
         HeapOutputStream out = new HeapOutputStream();
         RxJavaBdioDocument doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

         // write bdio document (including metadata) to in memory output stream
         Flowable.just(new File("http://example.com/files/1"))
                 .buffer(1)
                 .subscribe(doc.write(metadata, new BdioWriter.BdioFile(out)));

         // read bdio document and extract metadata fields
         BdioContext context = new BdioContext.Builder().expandContext(Bdio.Context.DEFAULT).build();
         BdioMetadata actualMetadata = doc.metadata(doc.read(out.getInputStream())).singleOrError().blockingGet();
         
         String actualCorrelationId = (String) context.getFieldValue(Bdio.DataProperty.correlationId, actualMetadata).collect(MoreCollectors.toOptional()).get();
       
         assertThat(actualCorrelationId).isEqualTo(expectedCorrelationId);
    }

    /**
     * Given a bdio file with metadata longer the maximum allowed value,
     * When doc.metadata(doc.read...) is called for the file,
     * then it should bubble up the maximum size exception
     */
    @Test
    public void testThrowsExceptionWhenMetadataUnreadable() {
        String expectedCorrelationId = UUID.randomUUID().toString();

        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        metadata.correlationId(expectedCorrelationId);

        StringBuilder temp = new StringBuilder();
        for (int i = 0; i <= Bdio.MAX_ENTRY_READ_SIZE; i++) {
            temp.append("a");
        }
        // set the metadata creator to a string longer than the max size to throw an error
        metadata.creator(temp.toString());

        HeapOutputStream out = new HeapOutputStream();

        RxJavaBdioDocument doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

        BdioWriter.BdioFile file = new BdioWriter.BdioFile(out);

        // write bdio document (including metadata) to in memory output stream
        Flowable.just(new File("http://example.com/files/1"))
                .buffer(1)
                .subscribe(doc.write(metadata, file));

        try {
            // read bdio document and extract metadata fields
            BdioContext context = new BdioContext.Builder().expandContext(Bdio.Context.DEFAULT).build();
            BdioMetadata actualMetadata = doc.metadata(doc.read(out.getInputStream())).singleOrError().blockingGet();
            context.getFieldValue(Bdio.DataProperty.correlationId, actualMetadata)
                    .collect(MoreCollectors.toOptional()).get();
        } catch (CompositeException e) {
            Assert.assertTrue(e.getExceptions().stream().map(Throwable::getClass).collect(Collectors.toSet())
                    .contains(EntrySizeViolationException.class));
            return;
        } catch (Exception e) {
            Assert.fail("The above read should've thrown a CompositeException with an EntrySizeViolationException, failing test.");
        }
        Assert.fail("The above read should've thrown an exception, failing test.");
    }

    @Test
    public void testMatchConfidenceThreshold() {
         Long expectedMatchConfidenceThreshold = 15L;

         BdioMetadata metadata = BdioMetadata.createRandomUUID();
         metadata.matchConfidenceThreshold(expectedMatchConfidenceThreshold);

         HeapOutputStream out = new HeapOutputStream();
         RxJavaBdioDocument doc = new RxJavaBdioDocument(new BdioContext.Builder().build());

         // write bdio document (including metadata) to in memory output stream
         Flowable.just(new File("http://example.com/files/1"))
                 .buffer(1)
                 .subscribe(doc.write(metadata, new BdioWriter.BdioFile(out)));

         // read bdio document and extract metadata fields
         BdioContext context = new BdioContext.Builder().expandContext(Bdio.Context.DEFAULT).build();
         BdioMetadata actualMetadata = doc.metadata(doc.read(out.getInputStream())).singleOrError().blockingGet();

         Long actualMatchConfidenceThreshold = (Long) context.getFieldValue(Bdio.DataProperty.matchConfidenceThreshold, actualMetadata).collect(MoreCollectors.toOptional()).get();

         assertThat(actualMatchConfidenceThreshold).isEqualTo(expectedMatchConfidenceThreshold);
    }

}
