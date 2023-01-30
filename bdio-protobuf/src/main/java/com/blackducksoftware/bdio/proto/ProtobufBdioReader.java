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
import java.util.zip.ZipInputStream;

import com.blackducksoftware.bdio.proto.api.BdioChunk;
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioServiceProvider;
import com.blackducksoftware.bdio.proto.v1.ProtoScanHeader;
import com.google.common.primitives.Shorts;

/**
 * Utility class for reading and deserializing bdio data in protobuf format for
 * different scan types
 *
 * @author sharapov
 * @author johara
 */
public class ProtobufBdioReader {

	/**
	 * Returns true if given input stream represents header chunk
	 *
	 * @param in input stream
	 * @return true if given input stream represents header chunk
	 * @throws IOException
	 */
	public static boolean isHeader(InputStream in) throws IOException {
		byte[] messageTypeBytes = new byte[2];
		in.read(messageTypeBytes);
		short messageType = Shorts.fromByteArray(messageTypeBytes);

		return messageType == BdioEntryType.HEADER.ordinal();
	}

	/**
	 * Read header chunk from the provided input stream
	 * 
	 * @param in
	 * @param verifyType
	 * @return ProtoScanHeader
	 * @throws IOException
	 */
	public static ProtoScanHeader readHeaderChunk(InputStream in, boolean verifyType) throws IOException {
		// Callers may have already done this, only perform this step if specified
		if (verifyType) {
			byte[] bdioEntryMessageTypeBytes = new byte[2];
			in.read(bdioEntryMessageTypeBytes);
			short bdioEntryType = Shorts.fromByteArray(bdioEntryMessageTypeBytes);

			if (bdioEntryType != BdioEntryType.HEADER.ordinal()) {
				throw new RuntimeException("Unsupported header message type");
			}
		}

		short version = readVersion(in);
		ProtobufBdioServiceProvider provider = new ProtobufBdioServiceProvider();

		return provider.getProtobufBdioReader(version).readHeaderChunk(in);
	}

	/**
	 * Reads just the header document from the supplied BDIO ZIP archive.
	 *
	 * @param in the BDIO ZIP archive
	 * @return the scan header
	 * @throws IOException if the header could not be read from the supplied stream
	 */
	public static ProtoScanHeader readHeaderFromScanFile(ZipInputStream in) throws IOException {
		byte[] bdioEntryTypeBytes = new byte[2];

		while (in.getNextEntry() != null) {
			in.read(bdioEntryTypeBytes);
			short bdioEntryType = Shorts.fromByteArray(bdioEntryTypeBytes);

			if (bdioEntryType == BdioEntryType.HEADER.ordinal()) {
				return readHeaderChunk(in, false);
			}
		}

		throw new RuntimeException("Header file not present in BDIO archive");
	}

	/**
	 * Read bdio nodes from supplied input stream, representing bdio chunk (entry).
	 *
	 * @param in input stream
	 * @return BdioChunk data structure containing deserialized nodes
	 * @throws IOException
	 */
	public static BdioChunk readBdioChunk(InputStream in, boolean verifyType) throws IOException {
		if (verifyType) {
			byte[] bdioEntryTypeBytes = new byte[2];
			in.read(bdioEntryTypeBytes);
			short bdioEntryType = Shorts.fromByteArray(bdioEntryTypeBytes);

			if (bdioEntryType != BdioEntryType.CHUNK.ordinal()) {
				throw new RuntimeException("Unsupported bdio entry type");
			}
		}

		short version = readVersion(in);

		ProtobufBdioServiceProvider provider = new ProtobufBdioServiceProvider();

		return provider.getProtobufBdioReader(version).readBdioChunk(in);
	}

	private static short readVersion(InputStream in) throws IOException {
		byte[] messageVersionBytes = new byte[2];
		in.read(messageVersionBytes);
		return Shorts.fromByteArray(messageVersionBytes);
	}
}
