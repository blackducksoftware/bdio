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

import com.blackducksoftware.bdio.proto.domain.BdbaMatchType;
import com.blackducksoftware.bdio.proto.domain.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.domain.ProtoBdbaFileNode;
import com.blackducksoftware.bdio.proto.domain.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerLayerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;
import com.blackducksoftware.bdio.proto.domain.ProtoScanHeader;
import com.blackducksoftware.bdio.proto.domain.ScanType;
import com.google.protobuf.Timestamp;

public class ProtobufTestUtils {

    private static final String ID = UUID.randomUUID().toString();

    public static ProtoScanHeader createProtoScanHeader() {
        return ProtoScanHeader.newBuilder()
                .setId(ID)
                .setScanType(ScanType.SIGNATURE)
                .setBaseDir("/baseDir")
                .setCodeLocationName("code location name")
                .setPublisherName("publisher name")
                .setPublisherVersion("publisher version")
                .setPublisherComment("publisher comment")
                .setCreator("creator")
                .setCreationTime(Timestamp.getDefaultInstance())
                .build();
    }

    public static ProtoDependencyNode createProtoDependencyNode() {
        return ProtoDependencyNode.newBuilder()
                .setComponentId(ID)
                .addMatchTypes(BdbaMatchType.CodeSimilarity)
                .build();
    }

    public static ProtoFileNode createProtoFileNode() {
        return ProtoFileNode.newBuilder()
                .setId(1L)
                .setParentId(2L)
                .setName("file name")
                .setPath("path")
                .setUri("uri")
                .setFileSystemType("FILE")
                .setSize(2L)
                .build();
    }

    public static ProtoComponentNode createProtoComponentNode() {
        return ProtoComponentNode.newBuilder()
                .setId(ID)
                .setIdentifier("identifier")
                .setNamespace("namespace")
                .build();
    }

    public static ProtoAnnotationNode createProtoAnnotationNode() {
        return ProtoAnnotationNode.newBuilder()
                .setId(ID)
                .setComment("comment")
                .build();
    }

    public static ProtoContainerNode createProtoContainerNode() {
        return ProtoContainerNode.newBuilder()
                .setId(ID)
                .setImage("imageId")
                .setArchitecture("linux/arm64")
                .setOs("linux")
                .setConfig("config")
                .addLayers("layerId")
                .build();
    }

    public static ProtoContainerLayerNode createProtoContainerLayerNode() {
        return ProtoContainerLayerNode.newBuilder()
                .setId(ID)
                .setLayer("layerId")
                .setSize(1L)
                .build();
    }

    public static ProtoBdbaFileNode createProtoBdbaFileNode() {
        return ProtoBdbaFileNode.newBuilder()
                .setId(ID)
                .setUri("someuri")
                .setSize(1L)
                .setLastModifiedDateTime(Timestamp.getDefaultInstance())
                .setFileSystemType("FILE")
                .setSize(2L)
                .build();
    }

}
