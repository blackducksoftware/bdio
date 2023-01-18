package com.blackducksoftware.bdio.proto;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.blackducksoftware.bdio.proto.MessageType;
import com.blackducksoftware.bdio.proto.v1.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.v1.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.v1.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.v1.ProtoFileNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageLayerNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageNode;
import com.blackducksoftware.bdio.proto.v1.ProtoScanHeader;
import com.google.common.primitives.Shorts;
import com.google.protobuf.GeneratedMessageV3;

/**
 * Utility class for writing bdio data in protobuf format
 *
 * @author sharapov
 *
 */
public class ProtobufBdioWriter implements Closeable {

    public static final String HEADER_FILE_NAME = "bdio-header.pb";

    public static final String ENTRY_FILE_NAME_TEMPLATE = "bdio-entry-%02d.pb";

    private static final long MAX_CHUNK_SIZE = Math.multiplyExact(16, 1024*1024); // 16 Mb

    /**
     * default version for protobuf node type. When there is a need to create specific version for given node, create
     * another constant, for example FILE_NODE_VERSION = 2;
     */
    private static final short NODE_VERSION = 1;

    private final ZipOutputStream bdioArchive;

    private boolean headerWritten = false;

    private long bytesRemaining = 0L;

    private int entryCount = 0;

    public ProtobufBdioWriter(OutputStream outputStream) {
        if (outputStream instanceof ZipOutputStream) {
            this.bdioArchive = (ZipOutputStream) outputStream;
        } else {
            this.bdioArchive = new ZipOutputStream(outputStream);
        }
    }

    /**
     * Write header entry to bdio archive
     *
     * @param protoHeader
     *            header to write
     * @throws IOException
     */
    public void writeHeader(ProtoScanHeader protoHeader) throws IOException {
        if (headerWritten) {
            throw new IllegalStateException("BDIO header file has already been written to this archive");
        }

        bdioArchive.putNextEntry(new ZipEntry(HEADER_FILE_NAME));
        bdioArchive.write(Shorts.toByteArray((short) MessageType.SCAN_HEADER.ordinal())); // message type
        bdioArchive.write(Shorts.toByteArray(NODE_VERSION)); // format version

        protoHeader.writeTo(bdioArchive);
        headerWritten = true;
    }

    /**
     * Write single file node for signature scan to bdio archive entry
     *
     * @param protoNode
     *            node to write
     * @throws IOException
     */
    public void writeSignatureScanFileNode(ProtoFileNode protoNode) throws IOException {
        // Ensure we're not surpassing the chunk size limit, adding 4 bytes to account for the length header
        // for each message. This will not be 100% accurate since writeDelimitedTo uses varints for the header,
        // which can be variable in size. See: https://developers.google.com/protocol-buffers/docs/encoding#varints
        if ((bytesRemaining -= protoNode.getSerializedSize() + 4) > 0L) {
            protoNode.writeDelimitedTo(bdioArchive);
        } else {
            bytesRemaining = MAX_CHUNK_SIZE;
            bdioArchive.putNextEntry(new ZipEntry(String.format(ENTRY_FILE_NAME_TEMPLATE, entryCount++)));
            bdioArchive.write(Shorts.toByteArray((short) MessageType.FILE_NODE.ordinal())); // message type
            bdioArchive.write(Shorts.toByteArray(NODE_VERSION)); // format version

            if ((bytesRemaining -= protoNode.getSerializedSize() + 4) > 0L) {
                protoNode.writeDelimitedTo(bdioArchive);
            } else {
                throw new RuntimeException("File metadata larger than maximum allowed chunk size");
            }
        }
    }

    /**
     * Write collection of nodes for bdba scan (binary/container) to bdio archive entry .
     *
     * @param protoNodes
     * @throws IOException
     */
    public void writeBdbaScanNodes(Collection<GeneratedMessageV3> protoNodes) throws IOException {
        for (com.google.protobuf.GeneratedMessageV3 node : protoNodes) {
            writeBdbaScanNode(node);
        }
    }

    /**
     * Write single node for bdba scan (binary/container) to bdio archive entry .
     *
     * @param protoNode
     * @throws IOException
     */
    public void writeBdbaScanNode(com.google.protobuf.GeneratedMessageV3 protoNode) throws IOException {

        int messageType = determineMessageType(protoNode);
        if ((bytesRemaining -= protoNode.getSerializedSize() + 4) > 0L) {
            bdioArchive.write(Shorts.toByteArray((short) messageType)); // message type
            bdioArchive.write(Shorts.toByteArray(NODE_VERSION)); // format version
            protoNode.writeDelimitedTo(bdioArchive);
        } else {
            bytesRemaining = MAX_CHUNK_SIZE;
            bdioArchive.putNextEntry(new ZipEntry(String.format(ENTRY_FILE_NAME_TEMPLATE, entryCount++)));
            bdioArchive.write(Shorts.toByteArray((short) messageType)); // message type
            bdioArchive.write(Shorts.toByteArray(NODE_VERSION)); // format version

            if ((bytesRemaining -= protoNode.getSerializedSize() + 4) > 0L) {
                protoNode.writeDelimitedTo(bdioArchive);
            } else {
                throw new RuntimeException("File metadata larger than maximum allowed chunk size");
            }
        }
    }

    private int determineMessageType(com.google.protobuf.GeneratedMessageV3 protoNode) {
        if (protoNode instanceof ProtoDependencyNode) {
            return MessageType.DEPENDENCY_NODE.ordinal();
        } else if (protoNode instanceof ProtoComponentNode) {
            return MessageType.COMPONENT_NODE.ordinal();
        } else if (protoNode instanceof ProtoFileNode) {
            return MessageType.FILE_NODE.ordinal();
        } else if (protoNode instanceof ProtoAnnotationNode) {
            return MessageType.ANNOTATION_NODE.ordinal();
        } else if (protoNode instanceof ProtoImageNode) {
            return MessageType.CONTAINER_IMAGE_NODE.ordinal();
        } else if (protoNode instanceof ProtoImageLayerNode) {
            return MessageType.CONTAINER_LAYER_NODE.ordinal();
        }

        throw new RuntimeException("Unsupported node type: " + protoNode.getClass().getName());
    }

    @Override
    public void close() throws IOException {
        if (!headerWritten) {
            throw new IllegalStateException("Header file must be written before archive can be closed");
        }
        bdioArchive.close();
    }

}
