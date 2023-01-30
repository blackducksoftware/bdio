package com.blackducksoftware.bdio.proto;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.blackducksoftware.bdio.proto.impl.ProtobufBdioServiceProvider;
import com.blackducksoftware.bdio.proto.v1.ProtoFileNode;
import com.blackducksoftware.bdio.proto.v1.ProtoScanHeader;
import com.google.common.primitives.Shorts;
import com.google.protobuf.Any;
import com.google.protobuf.Message;

/**
 * Utility class for writing bdio data in protobuf format
 *
 * @author sharapov
 * @author johara
 *
 */
public class ProtobufBdioWriter implements Closeable {

	public static final String HEADER_FILE_NAME = "bdio-header.pb";

	public static final String ENTRY_FILE_NAME_TEMPLATE = "bdio-entry-%02d.pb";

	private static final long MAX_CHUNK_SIZE = Math.multiplyExact(16, 1024 * 1024); // 16 Mb

	private final ZipOutputStream bdioArchive;

	private boolean headerWritten = false;

	private long bytesRemaining = 0L;

	private int entryCount = 0;

	private ProtobufBdioServiceProvider serviceProvider;

	public ProtobufBdioWriter(OutputStream outputStream) {
		if (outputStream instanceof ZipOutputStream) {
			this.bdioArchive = (ZipOutputStream) outputStream;
		} else {
			this.bdioArchive = new ZipOutputStream(outputStream);
		}

		serviceProvider = new ProtobufBdioServiceProvider();
	}

	/**
	 * Write header entry to bdio archive
	 *
	 * @param protoHeader header to write
	 * @throws IOException
	 */
	public void writeHeader(ProtoScanHeader protoHeader) throws IOException {
		if (headerWritten) {
			throw new IllegalStateException("BDIO header file has already been written to this archive");
		}

		bdioArchive.putNextEntry(new ZipEntry(HEADER_FILE_NAME));
		bdioArchive.write(Shorts.toByteArray((short) BdioEntryType.HEADER.ordinal())); // bdio entry type
		bdioArchive.write(Shorts.toByteArray(VersionConstants.VERSION_2)); // format version

		Any any = Any.pack(protoHeader);
		any.writeTo(bdioArchive);

		headerWritten = true;
	}

	/**
	 * Write single file node to bdio archive entry
	 *
	 * @param protoNode node to write
	 * @throws IOException
	 */
	public void writeBdioNodeV1(ProtoFileNode protoNode) throws IOException {

		serviceProvider.getProtobufBdioValidator(VersionConstants.VERSION_1).validate(protoNode);

		// Ensure we're not surpassing the chunk size limit, adding 4 bytes to account
		// for the length header
		// for each message. This will not be 100% accurate since writeDelimitedTo uses
		// varints for the header,
		// which can be variable in size. See:
		// https://developers.google.com/protocol-buffers/docs/encoding#varints
		if ((bytesRemaining -= protoNode.getSerializedSize() + 4) > 0L) {
			protoNode.writeDelimitedTo(bdioArchive);
		} else {
			bytesRemaining = MAX_CHUNK_SIZE;
			bdioArchive.putNextEntry(new ZipEntry(String.format(ENTRY_FILE_NAME_TEMPLATE, entryCount++)));
			bdioArchive.write(Shorts.toByteArray((short) BdioEntryType.CHUNK.ordinal())); // message type
			bdioArchive.write(Shorts.toByteArray(VersionConstants.VERSION_1)); // format version

			if ((bytesRemaining -= protoNode.getSerializedSize() + 4) > 0L) {
				protoNode.writeDelimitedTo(bdioArchive);
			} else {
				throw new RuntimeException("File metadata larger than maximum allowed chunk size");
			}
		}
	}

	/**
	 * Write collection of nodes to bdio archive entry .
	 *
	 * @param protoNodes
	 * @throws IOException
	 */
	public void writeBdioNodes(Collection<Message> protoNodes) throws IOException {
		for (Message node : protoNodes) {
			writeBdioNode(node);
		}
	}

	/**
	 * Write single node for to bdio archive entry .
	 *
	 * @param protoNode
	 * @throws IOException
	 */
	public void writeBdioNode(Message protoNode) throws IOException {
		writeBdioNode(protoNode, VersionConstants.VERSION_2);
	}

	private void writeBdioNode(Message protoNode, short version) throws IOException {
		serviceProvider.getProtobufBdioValidator(version).validate(protoNode);
		Any any = Any.pack(protoNode);

		if ((bytesRemaining -= protoNode.getSerializedSize() + 4) > 0L) {
			any.writeDelimitedTo(bdioArchive);
		} else {
			bytesRemaining = MAX_CHUNK_SIZE;
			bdioArchive.putNextEntry(new ZipEntry(String.format(ENTRY_FILE_NAME_TEMPLATE, entryCount++)));
			bdioArchive.write(Shorts.toByteArray((short) BdioEntryType.CHUNK.ordinal())); // bdio entry type
			bdioArchive.write(Shorts.toByteArray(VersionConstants.VERSION_2)); // format version

			if ((bytesRemaining -= protoNode.getSerializedSize() + 4) > 0L) {
				any.writeDelimitedTo(bdioArchive);
			} else {
				throw new RuntimeException("File metadata larger than maximum allowed chunk size");
			}
		}
	}

	@Override
	public void close() throws IOException {
		if (!headerWritten) {
			throw new IllegalStateException("Header file must be written before archive can be closed");
		}
		bdioArchive.close();
	}

}
