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
import com.google.protobuf.Any;

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
	 *
	 * @param in
	 * @param verifyType
	 * @return
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

		byte[] formatVersionBytes = new byte[2];
		in.read(formatVersionBytes);
		short formatVersion = Shorts.fromByteArray(formatVersionBytes);

		if (formatVersion == 1) {
			return ProtoScanHeader.parseFrom(in);
		} else if (formatVersion == 2) {
			Any any = Any.parseFrom(in);
			if (any.is(ProtoScanHeader.class)) {
				return any.unpack(ProtoScanHeader.class);
			} else {
				throw new RuntimeException("Unknown type for header data: " + any.getDescriptorForType().getFullName());
			}
		} else {
			throw new RuntimeException("Unsupported header message version: " + formatVersion);
		}
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
		Set<ProtoFileNode> fileData = new HashSet<>();
		Set<ProtoDependencyNode> dependencyData = new HashSet<>();
		Set<ProtoComponentNode> componentData = new HashSet<>();
		Set<ProtoAnnotationNode> annotationData = new HashSet<>();
		Set<ProtoImageNode> imageData = new HashSet<>();
		Set<ProtoImageLayerNode> layerData = new HashSet<>();

		if (verifyType) {
			byte[] bdioEntryTypeBytes = new byte[2];
			in.read(bdioEntryTypeBytes);
			short bdioEntryType = Shorts.fromByteArray(bdioEntryTypeBytes);

			if (bdioEntryType != BdioEntryType.CHUNK.ordinal()) {
				throw new RuntimeException("Unsupported bdio entry type");
			}
		}

		readBdioNodes(in, dependencyData, componentData, fileData, annotationData, imageData, layerData);

		return new BdioChunk(fileData, dependencyData, componentData, annotationData, imageData, layerData);
	}

	private static void readBdioNodes(InputStream in, Set<ProtoDependencyNode> dependencyData,
			Set<ProtoComponentNode> componentData, Set<ProtoFileNode> fileData, Set<ProtoAnnotationNode> annotationData,
			Set<ProtoImageNode> imageData, Set<ProtoImageLayerNode> layerData) throws IOException {

		short version = readVersion(in);

		if (version == 1) {
			ProtoFileNode node;

			// only signature scans may have version 1, so just read file node list
			while ((node = ProtoFileNode.parseDelimitedFrom(in)) != null) {
				fileData.add(node);
			}

			return;
		} else if (version == 2) {

			while (true) {

				Any any = Any.parseDelimitedFrom(in);

				if (any == null) {
					// the end of the steam
					break;
				}

				if (any.is(ProtoDependencyNode.class)) {
					dependencyData.add(any.unpack(ProtoDependencyNode.class));
					continue;
				} else if (any.is(ProtoComponentNode.class)) {
					componentData.add(any.unpack(ProtoComponentNode.class));
					continue;
				} else if (any.is(ProtoFileNode.class)) {
					fileData.add(any.unpack(ProtoFileNode.class));
					continue;
				} else if (any.is(ProtoAnnotationNode.class)) {
					annotationData.add(any.unpack(ProtoAnnotationNode.class));
					continue;
				} else if (any.is(ProtoImageNode.class)) {
					imageData.add(any.unpack(ProtoImageNode.class));
					continue;
				} else if (any.is(ProtoImageLayerNode.class)) {
					layerData.add(any.unpack(ProtoImageLayerNode.class));
					continue;
				}

				throw new RuntimeException("Unknown message type detected " + any.getClass().getName());

			}

			return;
		}

		throw new RuntimeException("Unsupported bdio bdba data version: " + version);
	}

	private static short readVersion(InputStream in) throws IOException {
		byte[] messageVersionBytes = new byte[2];
		in.read(messageVersionBytes);
		return Shorts.fromByteArray(messageVersionBytes);
	}
}
