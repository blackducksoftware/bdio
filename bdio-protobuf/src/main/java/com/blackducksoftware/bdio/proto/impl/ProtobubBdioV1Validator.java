package com.blackducksoftware.bdio.proto.impl;

import com.blackducksoftware.bdio.proto.api.BdioValidationException;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.v1.ProtoFileNode;
import com.google.protobuf.Message;

public class ProtobubBdioV1Validator implements IProtobufBdioValidator {

	@Override
	public void validate(Message message) {
		if (message instanceof ProtoFileNode) {

			return;
		}

		throw new BdioValidationException("Unknown message type in version 1: " + message.getClass().getName());
	}

}
