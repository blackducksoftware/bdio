/*
 * Copyright (C) 2023 Synopsys Inc.
 * http://www.synopsys.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Synopsys ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Synopsys.
 */
package com.blackducksoftware.bdio.proto;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.zip.ZipOutputStream;

import com.blackducksoftware.bdio.proto.api.BdioHeader;
import com.blackducksoftware.bdio.proto.api.IBdioNode;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioVersionWriter;
import com.blackducksoftware.bdio.proto.domain.ProtoScanHeader;
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioConverter;
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioServiceProvider;
import com.google.protobuf.Message;

/**
 * Utility class for writing bdio data in protobuf format to archive
 *
 * @author sharapov
 * @author johara
 *
 */
public class ProtobufBdioWriter implements Closeable {

    private ProtobufBdioServiceProvider serviceProvider;

    private final ZipOutputStream bdioArchive;

    private boolean headerWritten = false;

    private final short version;

    /**
     * Creates writer for the latest data model version
     *
     * @param outputStream
     */
    public ProtobufBdioWriter(OutputStream outputStream) {
        this(outputStream, BdioConstants.CURRENT_VERSION);
    }

    /**
     * Create writer for specified data model version
     *
     * @param outputStream
     * @param version
     */
    public ProtobufBdioWriter(OutputStream outputStream, short version) {
        if (outputStream instanceof ZipOutputStream) {
            this.bdioArchive = (ZipOutputStream) outputStream;
        } else {
            this.bdioArchive = new ZipOutputStream(outputStream);
        }

        this.version = version;

        serviceProvider = new ProtobufBdioServiceProvider();
    }

    /**
     * Write header entry to bdio archive
     *
     * @param protoHeader
     *            header to write
     * @throws IOException
     */
    public void writeHeader(BdioHeader header) throws IOException {
        ProtoScanHeader protoHeader = ProtobufBdioConverter.toProtoScanHeader(header);

        IProtobufBdioVersionWriter writer = serviceProvider.getProtobufBdioWriter(version);

        writer.writeToHeader(bdioArchive, protoHeader);

        headerWritten = true;
    }

    /**
     * Write collection of nodes to bdio archive entry
     *
     * @param protoNodes
     * @throws IOException
     */
    public void writeBdioNodes(Collection<IBdioNode> bdioNodes) throws IOException {
        for (IBdioNode node : bdioNodes) {
            writeBdioNode(node);
        }
    }

    /**
     * Write single node to bdio archive entry
     *
     * @param bdioNode
     *            data node
     * @throws IOException
     */
    public void writeBdioNode(IBdioNode bdioNode) throws IOException {
        Message protoNode = ProtobufBdioConverter.toProtobuf(bdioNode);
        serviceProvider.getProtobufBdioValidator(version).validate(protoNode);

        IProtobufBdioVersionWriter writer = serviceProvider.getProtobufBdioWriter(version);

        writer.writeToEntry(bdioArchive, protoNode);
    }

    @Override
    public void close() throws IOException {
        if (!headerWritten) {
            throw new IllegalStateException("Header file must be written before archive can be closed");
        }
        bdioArchive.close();
    }
}
