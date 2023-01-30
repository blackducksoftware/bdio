package com.blackducksoftware.bdio.proto.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.blackducksoftware.bdio.proto.v1.ProtoScanHeader;

/**
 * 
 * @author sharapov
 *
 */
public interface IProtobufBdioVersionReader {

	/**
	 * Get list of message type classes representing data model for specific version
	 * 
	 * @return List<Class<? extends com.google.protobuf.Message>>
	 */
	List<Class<? extends com.google.protobuf.Message>> getClassesList();

	/**
	 * Reads header chunk represented by input stream
	 * 
	 * @param in input stream
	 * @return ProtoScanHeader
	 * @throws IOException
	 */
	ProtoScanHeader readHeaderChunk(InputStream in) throws IOException;

	/**
	 * Reads the bdio data represented by input stream.
	 * 
	 * @param in input stream
	 * @return BdioChunk deserialized bdio data
	 * @throws IOException
	 */
	BdioChunk readBdioChunk(InputStream in) throws IOException;

}
