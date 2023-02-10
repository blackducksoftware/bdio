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

import java.util.UUID;

import org.junit.Test;

import com.blackducksoftware.bdio.proto.api.BdioValidationException;
import com.blackducksoftware.bdio.proto.domain.BdbaMatchType;
import com.blackducksoftware.bdio.proto.domain.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.domain.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerLayerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioV2Validator;

public class ProtobufBdioV2ValidatorTest {

    private ProtobufBdioV2Validator validator = new ProtobufBdioV2Validator();

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

    @Test
    public void testValidateComponentNode1() {
        validator.validate(ProtobufTestUtils.createProtoComponentNode());
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateComponentNode2() {
        ProtoComponentNode componentnNode = ProtoComponentNode.newBuilder()
                .setIdentifier("identifier")
                .setNamespace("namespace")
                .build();

        validator.validate(componentnNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateComponentNode3() {
        ProtoComponentNode componentnNode = ProtoComponentNode.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setNamespace("namespace")
                .build();

        validator.validate(componentnNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateComponentNode4() {
        ProtoComponentNode componentnNode = ProtoComponentNode.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setIdentifier("identifier")
                .build();

        validator.validate(componentnNode);
    }

    @Test
    public void testValidateDependencyNode1() {
        validator.validate(ProtobufTestUtils.createProtoDependencyNode());
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateDependencyNode2() {
        ProtoDependencyNode dependencyNode = ProtoDependencyNode.newBuilder()
                .addMatchTypes(BdbaMatchType.CodeSimilarity)
                .build();

        validator.validate(dependencyNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateDependencyNode3() {
        ProtoDependencyNode dependencyNode = ProtoDependencyNode.newBuilder()
                .setComponentId(UUID.randomUUID().toString())
                .build();

        validator.validate(dependencyNode);
    }

    @Test
    public void testValidateAnnotationNode1() {
        validator.validate(ProtobufTestUtils.createProtoAnnotationNode());
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateAnnotationNode2() {
        ProtoAnnotationNode annotationNode = ProtoAnnotationNode.newBuilder()
                .setComment("comment")
                .build();
        validator.validate(annotationNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateAnnotationNode3() {
        ProtoAnnotationNode annotationNode = ProtoAnnotationNode.newBuilder()
                .setId(UUID.randomUUID().toString())
                .build();
        validator.validate(annotationNode);
    }

    @Test
    public void testValidateContainerNode1() {
        validator.validate(ProtobufTestUtils.createProtoContainerNode());
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateContainerNode2() {
        ProtoContainerNode containerNode = ProtoContainerNode.newBuilder()
                .setImage("imageId")
                .setArchitecture("linux/arm64")
                .setOs("linux")
                .setConfig("config")
                .addLayers("layerId")
                .build();

        validator.validate(containerNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateContainerNode3() {
        ProtoContainerNode containerNode = ProtoContainerNode.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setArchitecture("linux/arm64")
                .setOs("linux")
                .setConfig("config")
                .addLayers("layerId")
                .build();

        validator.validate(containerNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateContainerNode4() {
        ProtoContainerNode containerNode = ProtoContainerNode.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setImage("imageId")
                .setOs("linux")
                .setConfig("config")
                .addLayers("layerId")
                .build();

        validator.validate(containerNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateContainerNode5() {
        ProtoContainerNode containerNode = ProtoContainerNode.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setImage("imageId")
                .setArchitecture("linux/arm64")
                .setConfig("config")
                .addLayers("layerId")
                .build();

        validator.validate(containerNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateContainerNode6() {
        ProtoContainerNode containerNode = ProtoContainerNode.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setImage("imageId")
                .setArchitecture("linux/arm64")
                .setOs("linux")
                .addLayers("layerId")
                .build();

        validator.validate(containerNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateContainerNode7() {
        ProtoContainerNode containerNode = ProtoContainerNode.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setImage("imageId")
                .setArchitecture("linux/arm64")
                .setOs("linux")
                .setConfig("config")
                .build();

        validator.validate(containerNode);
    }

    @Test
    public void testValidateContainerLayerNode1() {
        validator.validate(ProtobufTestUtils.createProtoContainerLayerNode());
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateContainerLayerNode2() {
        ProtoContainerLayerNode containerLayerNode = ProtoContainerLayerNode.newBuilder()
                .setLayer("layerId")
                .setSize(1L)
                .build();
        validator.validate(containerLayerNode);
    }

    @Test(expected = BdioValidationException.class)
    public void testValidateContainerLayerNode3() {
        ProtoContainerLayerNode containerLayerNode = ProtoContainerLayerNode.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setSize(1L)
                .build();
        validator.validate(containerLayerNode);
    }
}
