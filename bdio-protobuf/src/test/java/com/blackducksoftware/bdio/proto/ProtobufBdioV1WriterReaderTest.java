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
import java.nio.file.Paths;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;

import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioVersionWriter;
import com.blackducksoftware.bdio.proto.domain.ProtoChunk;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;
import com.blackducksoftware.bdio.proto.domain.ProtoScanHeader;
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioV1Reader;
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioV1Validator;
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioV1Writer;

public class ProtobufBdioV1WriterReaderTest {

    private static final String FILE_PATH = "src/test/resources/scan_data/bdioData.zip";

    private ProtobufBdioV1Validator v1Validator = new ProtobufBdioV1Validator();

    private ProtobufBdioV1Reader v1Reader = new ProtobufBdioV1Reader(v1Validator);

    private IProtobufBdioVersionWriter v1Writer = new ProtobufBdioV1Writer(v1Validator);

    private ProtoScanHeader protoHeader = ProtobufTestUtils.createProtoScanHeader();

    @After
    public void deleteBdioArchive() throws IOException {
        Files.delete(Paths.get(FILE_PATH));
    }

    @Test
    public void testValidatorIsCalled() throws IOException {
        IProtobufBdioValidator mockedValidator = Mockito.mock(IProtobufBdioValidator.class);
        ProtobufBdioV1Writer writer = new ProtobufBdioV1Writer(mockedValidator);

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
            v1Writer.writeToHeader(bdioOutput, protoHeader);
            v1Writer.writeToEntry(bdioOutput, fileNode);
        }

        IProtobufBdioValidator mockedValidator = Mockito.mock(IProtobufBdioValidator.class);
        ProtobufBdioV1Reader reader = new ProtobufBdioV1Reader(mockedValidator);
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
            v1Writer.writeToHeader(bdioOutput, protoHeader);
            v1Writer.writeToEntry(bdioOutput, fileNode);
        }

        ProtoChunk protoChunk = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(FILE_PATH))) {
            skipToFirstNode(zis);
            protoChunk = v1Reader.readProtoChunk(zis);
        }
        assertThat(protoChunk.getFileNodes().size()).isEqualTo(1);

        ProtoFileNode protoFileNode = protoChunk.getFileNodes().iterator().next();
        assertThat(protoFileNode).isEqualTo(fileNode);
    }

    private void skipToFirstNode(ZipInputStream zis) throws IOException {
        zis.getNextEntry();
        zis.getNextEntry();

        byte[] b = new byte[4];
        zis.read(b);
    }
}
