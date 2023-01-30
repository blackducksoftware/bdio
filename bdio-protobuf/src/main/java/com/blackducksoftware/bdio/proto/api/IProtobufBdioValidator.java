package com.blackducksoftware.bdio.proto.api;

import com.google.protobuf.Message;

/**
 * 
 * @author sharapov
 *
 */
public interface IProtobufBdioValidator {

	/**
	 * Validate single protobuf data node
	 * 
	 * @param message node to validate
	 * @throws BdioValidationException
	 */
	void validate(Message message);
}
