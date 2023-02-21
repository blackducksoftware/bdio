/*
 * Copyright (C) 2023 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio.proto;

import static com.google.common.truth.Truth.assertThat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioVersionWriter;
import com.blackducksoftware.bdio.proto.domain.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.domain.ProtoBdbaFileNode;
import com.blackducksoftware.bdio.proto.domain.ProtoChunk;
import com.blackducksoftware.bdio.proto.domain.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerLayerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;
import com.blackducksoftware.bdio.proto.domain.ProtoScanHeader;
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioV2Reader;
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioV2Validator;
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioV2Writer;

public class ProtobufBdioV2WriterReaderTest {

    private static final String FILE_PATH = "src/test/resources/scan_data/bdioData.zip";

    private ProtobufBdioV2Validator v2Validator = new ProtobufBdioV2Validator();

    private ProtobufBdioV2Reader v2Reader = new ProtobufBdioV2Reader(v2Validator);

    private IProtobufBdioVersionWriter v2Writer = new ProtobufBdioV2Writer(v2Validator);

    private ProtoScanHeader protoHeader = ProtobufTestUtils.createProtoScanHeader();

    @Before
    public void setUp() throws IOException {
        Path filePath = Paths.get(FILE_PATH);
        Files.createFile(filePath);
    }

    @After
    public void deleteBdioArchive() throws IOException {
        Files.delete(Paths.get(FILE_PATH));
    }

    @Test
    public void testValidatorIsCalledWhenWriting() throws IOException {
        IProtobufBdioValidator mockedValidator = Mockito.mock(IProtobufBdioValidator.class);
        ProtobufBdioV2Writer writer = new ProtobufBdioV2Writer(mockedValidator);

        ProtoFileNode fileNode = ProtobufTestUtils.createProtoFileNode();
        try (ZipOutputStream bdioOutput = new ZipOutputStream(new FileOutputStream(FILE_PATH))) {
            writer.writeToHeader(bdioOutput, protoHeader);
            writer.writeToEntry(bdioOutput, fileNode);
        }

        Mockito.verify(mockedValidator, Mockito.times(1)).validate(Mockito.any());
    }

    @Test
    public void testValidatorIsCalledWhenReading() throws IOException {

        ProtoFileNode fileNode = ProtobufTestUtils.createProtoFileNode();
        try (ZipOutputStream bdioOutput = new ZipOutputStream(new FileOutputStream(FILE_PATH))) {
            v2Writer.writeToHeader(bdioOutput, protoHeader);
            v2Writer.writeToEntry(bdioOutput, fileNode);
        }

        IProtobufBdioValidator mockedValidator = Mockito.mock(IProtobufBdioValidator.class);
        ProtobufBdioV2Reader reader = new ProtobufBdioV2Reader(mockedValidator);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(FILE_PATH))) {
            skipToFirstNode(zis);
            reader.readProtoChunk(zis);
        }

        Mockito.verify(mockedValidator, Mockito.times(1)).validate(Mockito.any());
    }

    @Test
    public void testWriteAndReadSingleFileNode() throws IOException {
        ProtoFileNode fileNode = ProtobufTestUtils.createProtoFileNode();
        try (ZipOutputStream bdioOutput = new ZipOutputStream(new FileOutputStream(FILE_PATH))) {
            v2Writer.writeToHeader(bdioOutput, protoHeader);
            v2Writer.writeToEntry(bdioOutput, fileNode);
        }

        ProtoChunk protoChunk = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(FILE_PATH))) {
            skipToFirstNode(zis);
            protoChunk = v2Reader.readProtoChunk(zis);
        }
        assertThat(protoChunk.getFileNodes().size()).isEqualTo(1);

        ProtoFileNode protoFileNode = protoChunk.getFileNodes().iterator().next();
        assertThat(protoFileNode).isEqualTo(fileNode);
    }

    @Test
    public void testWriteAndReadSingleComponentNode() throws IOException {
        ProtoComponentNode componentNode = ProtobufTestUtils.createProtoComponentNode();
        try (ZipOutputStream bdioOutput = new ZipOutputStream(new FileOutputStream(FILE_PATH))) {
            v2Writer.writeToHeader(bdioOutput, protoHeader);
            v2Writer.writeToEntry(bdioOutput, componentNode);
        }

        ProtoChunk protoChunk = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(FILE_PATH))) {
            skipToFirstNode(zis);
            protoChunk = v2Reader.readProtoChunk(zis);
        }
        assertThat(protoChunk.getComponentNodes().size()).isEqualTo(1);

        ProtoComponentNode protoComponentNode = protoChunk.getComponentNodes().iterator().next();
        assertThat(protoComponentNode).isEqualTo(componentNode);
    }

    @Test
    public void testWriteAndReadSingleDependencyNode() throws IOException {
        ProtoDependencyNode dependencyNode = ProtobufTestUtils.createProtoDependencyNode();
        try (ZipOutputStream bdioOutput = new ZipOutputStream(new FileOutputStream(FILE_PATH))) {
            v2Writer.writeToHeader(bdioOutput, protoHeader);
            v2Writer.writeToEntry(bdioOutput, dependencyNode);
        }

        ProtoChunk protoChunk = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(FILE_PATH))) {
            skipToFirstNode(zis);
            protoChunk = v2Reader.readProtoChunk(zis);
        }
        assertThat(protoChunk.getDependencyNodes().size()).isEqualTo(1);

        ProtoDependencyNode protoDependencyNode = protoChunk.getDependencyNodes().iterator().next();
        assertThat(protoDependencyNode).isEqualTo(dependencyNode);
    }

    @Test
    public void testWriteAndReadSingleAnnotationNode() throws IOException {
        ProtoAnnotationNode annotationNode = ProtobufTestUtils.createProtoAnnotationNode();
        try (ZipOutputStream bdioOutput = new ZipOutputStream(new FileOutputStream(FILE_PATH))) {
            v2Writer.writeToHeader(bdioOutput, protoHeader);
            v2Writer.writeToEntry(bdioOutput, annotationNode);
        }

        ProtoChunk protoChunk = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(FILE_PATH))) {
            skipToFirstNode(zis);
            protoChunk = v2Reader.readProtoChunk(zis);
        }
        assertThat(protoChunk.getAnnotationNodes().size()).isEqualTo(1);

        ProtoAnnotationNode protoAnnotationNode = protoChunk.getAnnotationNodes().iterator().next();
        assertThat(protoAnnotationNode).isEqualTo(annotationNode);
    }

    @Test
    public void testWriteAndReadSingleContainerNode() throws IOException {
        ProtoContainerNode containerNode = ProtobufTestUtils.createProtoContainerNode();
        try (ZipOutputStream bdioOutput = new ZipOutputStream(new FileOutputStream(FILE_PATH))) {
            v2Writer.writeToHeader(bdioOutput, protoHeader);
            v2Writer.writeToEntry(bdioOutput, containerNode);
        }

        ProtoChunk protoChunk = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(FILE_PATH))) {
            skipToFirstNode(zis);
            protoChunk = v2Reader.readProtoChunk(zis);
        }
        assertThat(protoChunk.getContainerNodes().size()).isEqualTo(1);

        ProtoContainerNode protoContainerNode = protoChunk.getContainerNodes().iterator().next();
        assertThat(protoContainerNode).isEqualTo(containerNode);
    }

    @Test
    public void testWriteAndReadSingleContainerLayerNode() throws IOException {
        ProtoContainerLayerNode containerLayerNode = ProtobufTestUtils.createProtoContainerLayerNode();
        try (ZipOutputStream bdioOutput = new ZipOutputStream(new FileOutputStream(FILE_PATH))) {
            v2Writer.writeToHeader(bdioOutput, protoHeader);
            v2Writer.writeToEntry(bdioOutput, containerLayerNode);
        }

        ProtoChunk protoChunk = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(FILE_PATH))) {
            skipToFirstNode(zis);
            protoChunk = v2Reader.readProtoChunk(zis);
        }
        assertThat(protoChunk.getContainerLayerNodes().size()).isEqualTo(1);

        ProtoContainerLayerNode protoContainerLayerNode = protoChunk.getContainerLayerNodes().iterator().next();
        assertThat(protoContainerLayerNode).isEqualTo(containerLayerNode);
    }

    @Test
    public void testWriteAndReadSingleBdbaFileNode() throws IOException {
        ProtoBdbaFileNode bdbaFileNode = ProtobufTestUtils.createProtoBdbaFileNode();
        try (ZipOutputStream bdioOutput = new ZipOutputStream(new FileOutputStream(FILE_PATH))) {
            v2Writer.writeToHeader(bdioOutput, protoHeader);
            v2Writer.writeToEntry(bdioOutput, bdbaFileNode);
        }

        ProtoChunk protoChunk = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(FILE_PATH))) {
            skipToFirstNode(zis);
            protoChunk = v2Reader.readProtoChunk(zis);
        }
        assertThat(protoChunk.getBdbaFileNodes().size()).isEqualTo(1);

        ProtoBdbaFileNode protoBdbaFileNode = protoChunk.getBdbaFileNodes().iterator().next();
        assertThat(protoBdbaFileNode).isEqualTo(bdbaFileNode);
    }

    private void skipToFirstNode(ZipInputStream zis) throws IOException {
        zis.getNextEntry();
        zis.getNextEntry();

        byte[] b = new byte[4];
        zis.read(b);
    }

}
