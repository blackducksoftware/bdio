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
package com.blackducksoftware.bdio.proto;

import org.junit.Test;

import com.blackducksoftware.bdio.proto.api.BdioValidationException;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioV1Validator;

public class ProtobufBdioV1ValidatorTest {

    private ProtobufBdioV1Validator validator = new ProtobufBdioV1Validator();

    @Test
    public void testValidateFileNode1() {
        validator.validate(ProtobufTestUtils.createProtoFileNode());
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateFileNode2() {
        ProtoFileNode fileNode = ProtoFileNode.newBuilder()
                .setId(1L)
                .setParentId(-1L)
                .setPath("path")
                .setUri("uri")
                .setFileSystemType("fileSystemType")
                .build();

        validator.validate(fileNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateFileNode3() {
        ProtoFileNode fileNode = ProtoFileNode.newBuilder()
                .setId(1L)
                .setParentId(-1L)
                .setName("name")
                .setUri("uri")
                .setFileSystemType("fileSystemType")
                .build();

        validator.validate(fileNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateFileNode4() {
        ProtoFileNode fileNode = ProtoFileNode.newBuilder()
                .setId(1L)
                .setParentId(-1L)
                .setName("name")
                .setPath("path")
                .setFileSystemType("fileSystemType")
                .build();

        validator.validate(fileNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateFileNode6() {
        ProtoFileNode fileNode = ProtoFileNode.newBuilder()
                .setId(1L)
                .setParentId(-1L)
                .setName("name")
                .setPath("path")
                .setUri("uri")
                .build();

        validator.validate(fileNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateComponentNode() {
        validator.validate(ProtobufTestUtils.createProtoComponentNode());
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateDependencyNode() {
        validator.validate(ProtobufTestUtils.createProtoDependencyNode());
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateAnnotationNode() {
        validator.validate(ProtobufTestUtils.createProtoAnnotationNode());
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateContainerNode() {
        validator.validate(ProtobufTestUtils.createProtoContainerNode());
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateContainerLayerNode() {
        validator.validate(ProtobufTestUtils.createProtoContainerLayerNode());
    }

}
