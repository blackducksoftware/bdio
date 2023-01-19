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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;

import com.blackducksoftware.bdio.proto.v1.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.v1.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.v1.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.v1.ProtoFileNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageLayerNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageNode;
import com.blackducksoftware.bdio.proto.v1.ProtoScanHeader;
import com.google.common.primitives.Shorts;

/**
 * Utility class for reading and deserializing bdio data in protobuf format for different scan types
 *
 * @author sharapov
 * @author johara
 */
public class ProtobufBdioReader {

    /**
     * Returns true if given input stream represents header chunk
     *
     * @param in
     *            input stream
     * @return true if given input stream represents header chunk
     * @throws IOException
     */
    public static boolean isHeader(InputStream in) throws IOException {
        byte[] messageTypeBytes = new byte[2];
        in.read(messageTypeBytes);
        short messageType = Shorts.fromByteArray(messageTypeBytes);

        return messageType == MessageType.SCAN_HEADER.ordinal();
    }

    /**
     *
     * @param in
     * @param verifyType
     * @return
     * @throws IOException
     */
    public static ProtoScanHeader readHeaderChunk(InputStream in, boolean verifyType) throws IOException {
        // Callers may have already done this, only perform this step if specified
        if (verifyType) {
            byte[] messageTypeBytes = new byte[2];
            in.read(messageTypeBytes);
            short messageType = Shorts.fromByteArray(messageTypeBytes);

            if (messageType != MessageType.SCAN_HEADER.ordinal()) {
                throw new RuntimeException("Unsupported header message type");
            }
        }

        byte[] messageVersionBytes = new byte[2];
        in.read(messageVersionBytes);
        short messageVersion = Shorts.fromByteArray(messageVersionBytes);

        if (messageVersion == 1) {
            return ProtoScanHeader.parseFrom(in);
        } else {
            throw new RuntimeException("Unsupported header message version");
        }
    }

    /**
     * Reads just the header document from the supplied BDIO ZIP archive.
     *
     * @param in
     *            the BDIO ZIP archive
     * @return the scan header
     * @throws IOException
     *             if the header could not be read from the supplied stream
     */
    public static ProtoScanHeader readHeaderFromScanFile(ZipInputStream in) throws IOException {
        byte[] messageTypeBytes = new byte[2];

        while (in.getNextEntry() != null) {
            in.read(messageTypeBytes);
            short messageType = Shorts.fromByteArray(messageTypeBytes);

            if (messageType == MessageType.SCAN_HEADER.ordinal()) {
                return readHeaderChunk(in, false);
            }
        }

        throw new RuntimeException("Header file not present in BDIO archive");
    }

    /**
     * Read the set of file nodes from the supplied InputStream. Expects that the stream is already positioned at the
     * start of the scan chunk, just before the message type and version headers. If verifyType is false, it should be
     * positioned at the start of
     * the version header, just after the message type header.
     *
     * @param in
     *            the InputStream to read from
     * @param verifyType
     *            whether to verify the message type headers. If false, its expected that the caller has done that
     *            verification.
     * @return the deserialized set of file nodes
     * @throws IOException
     *             if the file nodes could not be read from the supplied input stream
     */
    public static BdioChunk readSignatureBdioChunk(InputStream in, boolean verifyType) throws IOException {
        // Callers may have already done this, only perform this step if specified
        if (verifyType) {
            byte[] messageTypeBytes = new byte[2];
            in.read(messageTypeBytes);
            short messageType = Shorts.fromByteArray(messageTypeBytes);

            if (messageType != MessageType.FILE_NODE.ordinal()) {
                throw new RuntimeException("Unsupported content message type");
            }
        }

        byte[] messageVersionBytes = new byte[2];
        in.read(messageVersionBytes);
        short messageVersion = Shorts.fromByteArray(messageVersionBytes);

        if (messageVersion == 1) {
            Set<ProtoFileNode> fileNodes = new HashSet<>();
            ProtoFileNode node;

            while ((node = ProtoFileNode.parseDelimitedFrom(in)) != null) {
                fileNodes.add(node);
            }

            return new BdioChunk(fileNodes);
        } else {
            throw new RuntimeException("Unsupported file data message version");
        }
    }


    /**
     * Read bdba scan nodes from supplied input stream, representing bdio chunk (entry).
     *
     * @param in
     *            input stream
     * @return BdioChunk data structure containing deserialized nodes for bdba scan
     * @throws IOException
     */
    public static BdioChunk readBdbaBdioChunk(InputStream in) throws IOException {
        Set<ProtoFileNode> fileData = new HashSet<>();
        Set<ProtoDependencyNode> dependencyData = new HashSet<>();
        Set<ProtoComponentNode> componentData = new HashSet<>();
        Set<ProtoAnnotationNode> annotationData = new HashSet<>();
        Set<ProtoImageNode> imageData = new HashSet<>();
        Set<ProtoImageLayerNode> layerData = new HashSet<>();

        readBdbaNodes(in, dependencyData, componentData, fileData, annotationData, imageData, layerData);

        return new BdioChunk(fileData, dependencyData, componentData, annotationData, imageData, layerData);
    }

    private static short readVersion(InputStream in) throws IOException {
        byte[] messageVersionBytes = new byte[2];
        in.read(messageVersionBytes);
        return Shorts.fromByteArray(messageVersionBytes);
    }

    private static void readBdbaNodes(InputStream in, Set<ProtoDependencyNode> dependencyData, Set<ProtoComponentNode> componentData,
            Set<ProtoFileNode> fileData, Set<ProtoAnnotationNode> annotationData, Set<ProtoImageNode> imageData, Set<ProtoImageLayerNode> layerData)
            throws IOException {
        byte[] messageTypeBytes = new byte[2];

        while (true) {
            int numberOfBytes = in.read(messageTypeBytes);
            if (numberOfBytes == -1) {
                break;
            }

            short version = readVersion(in);
            if (version != 1) {
                throw new RuntimeException("Unsupported bdio bdba data version: " + version);
            }

            short messageType = Shorts.fromByteArray(messageTypeBytes);

            MessageType mt = MessageType.values()[messageType];
            switch (mt) {
            case DEPENDENCY_NODE:
                dependencyData.add(ProtoDependencyNode.parseDelimitedFrom(in));
                break;
            case COMPONENT_NODE:
                componentData.add(ProtoComponentNode.parseDelimitedFrom(in));
                break;
            case FILE_NODE:
                fileData.add(ProtoFileNode.parseDelimitedFrom(in));
                break;
            case ANNOTATION_NODE:
                annotationData.add(ProtoAnnotationNode.parseDelimitedFrom(in));
                break;
            case CONTAINER_IMAGE_NODE:
                imageData.add(ProtoImageNode.parseDelimitedFrom(in));
                break;
            case CONTAINER_LAYER_NODE:
                layerData.add(ProtoImageLayerNode.parseDelimitedFrom(in));
                break;
            default:
                throw new RuntimeException("Unknown message type detected " + mt.name());
            }
        }
    }

}
