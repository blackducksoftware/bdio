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
package com.blackducksoftware.bdio.proto.impl;

import java.io.IOException;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.blackducksoftware.bdio.proto.BdioConstants;
import com.blackducksoftware.bdio.proto.BdioEntryType;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioVersionWriter;
import com.google.common.primitives.Shorts;
import com.google.protobuf.Message;

/**
 * Provides common methods for writing bdio data
 *
 * @author sharapov
 *
 */
public abstract class AbstractProtobufBdioVersionWriter implements IProtobufBdioVersionWriter {

    private long bytesRemaining = 0L;

    private int entryCount = 0;

    protected final IProtobufBdioValidator validator;

    public AbstractProtobufBdioVersionWriter(IProtobufBdioValidator validator) {
        this.validator = Objects.requireNonNull(validator);
    }

    protected abstract short getVersion();

    protected abstract void writeHeaderNode(ZipOutputStream bdioArchive, Message header) throws IOException;

    protected abstract void writeDataNode(ZipOutputStream bdioArchive, Message protoNode) throws IOException;

    @Override
    public void writeToHeader(ZipOutputStream bdioArchive, Message header) throws IOException {
        bdioArchive.putNextEntry(new ZipEntry(BdioConstants.HEADER_FILE_NAME));
        bdioArchive.write(Shorts.toByteArray((short) BdioEntryType.HEADER.ordinal())); // bdio entry type
        bdioArchive.write(Shorts.toByteArray(getVersion())); // format version

        writeHeaderNode(bdioArchive, header);
    }

    @Override
    public void writeToEntry(ZipOutputStream bdioArchive, Message protoNode) throws IOException {
        boolean newEntry = (bytesRemaining - protoNode.getSerializedSize() - 4) <= 0;
        bytesRemaining = newEntry ? BdioConstants.MAX_CHUNK_SIZE : bytesRemaining; // reset remaining bytes for new
                                                                                   // entry
        bytesRemaining -= protoNode.getSerializedSize() + 4;

        if (newEntry) {
            createNewArchiveEntry(bdioArchive);
            entryCount++;
        }

        validator.validate(protoNode);

        writeDataNode(bdioArchive, protoNode);
    }

    private void createNewArchiveEntry(ZipOutputStream bdioArchive) throws IOException {
        bdioArchive.putNextEntry(new ZipEntry(String.format(BdioConstants.ENTRY_FILE_NAME_TEMPLATE, entryCount)));
        bdioArchive.write(Shorts.toByteArray((short) BdioEntryType.CHUNK.ordinal())); // bdio entry type
        bdioArchive.write(Shorts.toByteArray(getVersion())); // format version
    }

}
