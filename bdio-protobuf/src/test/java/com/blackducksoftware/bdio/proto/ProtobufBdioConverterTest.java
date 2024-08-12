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

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Test;

import com.blackducksoftware.bdio.proto.api.BdioAnnotationNode;
import com.blackducksoftware.bdio.proto.api.BdioBdbaFileNode;
import com.blackducksoftware.bdio.proto.api.BdioComponentNode;
import com.blackducksoftware.bdio.proto.api.BdioContainerLayerNode;
import com.blackducksoftware.bdio.proto.api.BdioContainerNode;
import com.blackducksoftware.bdio.proto.api.BdioDependencyNode;
import com.blackducksoftware.bdio.proto.api.BdioFileNode;
import com.blackducksoftware.bdio.proto.api.BdioHeader;
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
import com.blackducksoftware.bdio.proto.impl.ProtobufBdioConverter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Timestamp;

public class ProtobufBdioConverterTest {

    private static final String ID = UUID.randomUUID().toString();

    private static final String NAMESPACE = "namespace";

    private static final String IDENTIFIER = "identifier";

    private static final String COMMENT = "comment";

    private static final String DESCRIPTION_ID = UUID.randomUUID().toString();

    private static final String EVIDENCE_ID = UUID.randomUUID().toString();

    private static final UUID CORRELATION_ID = UUID.randomUUID();

    private static final List<String> MATCH_TYPES = Arrays.stream(BdbaMatchType.values())
            .map(mt -> mt.name())
            .filter(mtn -> !mtn.equals("UNRECOGNIZED"))
            .collect(Collectors.toList());

    private static final Map<String, String> SIGNATURES = ImmutableMap.<String, String> builder()
            .put("MATCH_SHA1", "sha1")
            .put("MATCH_CLEAN_SHA1", "clean_sha1")
            .put("DEEP_WITH_SIZE", "deep_with_size")
            .put("DEEP_NO_SIZE", "deep_no_size")
            .put("STRUCTURE_ONLY", "structure_only")
            .put("SHALLOW_WITH_SIZE", "shallow_with_size")
            .put("SHALLOW_NO_SIZE", "shallow_no_size")
            .build();

    @Test
    // test when all fields are non null
    public void testConvertBdioHeader1() {
        Instant instant = Instant.now();
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();

        BdioHeader header = new BdioHeader(ID, ScanType.SIGNATURE.name(), "codeLocationName", "projectName", "versionName",
                "publisherName", "publisherVersion", "publisherComment", "creator",
                instant, "sourceRepository", "sourceBranch", "projectGroupName",
                CORRELATION_ID, 10L, "/baseDir", true,
                true, Boolean.TRUE, 100L);

        ProtoScanHeader protoHeader = ProtobufBdioConverter.toProtoScanHeader(header);

        assertThat(protoHeader.getId()).isEqualTo(ID);
        assertThat(protoHeader.getScanType()).isEqualTo(ScanType.SIGNATURE);
        assertThat(protoHeader.getCodeLocationName()).isEqualTo("codeLocationName");
        assertThat(protoHeader.getProjectName()).isEqualTo("projectName");
        assertThat(protoHeader.getProjectVersionName()).isEqualTo("versionName");
        assertThat(protoHeader.getPublisherName()).isEqualTo("publisherName");
        assertThat(protoHeader.getPublisherVersion()).isEqualTo("publisherVersion");
        assertThat(protoHeader.getPublisherComment()).isEqualTo("publisherComment");
        assertThat(protoHeader.getCreator()).isEqualTo("creator");
        assertThat(protoHeader.getCreationTime()).isEqualTo(timestamp);
        assertThat(protoHeader.getSourceRepository()).isEqualTo("sourceRepository");
        assertThat(protoHeader.getSourceBranch()).isEqualTo("sourceBranch");
        assertThat(protoHeader.getProjectGroupName()).isEqualTo("projectGroupName");
        assertThat(protoHeader.getCorrelationId()).isEqualTo(CORRELATION_ID.toString());
        assertThat(protoHeader.getMatchConfidenceThreshold()).isEqualTo(10L);
        assertThat(protoHeader.getBaseDir()).isEqualTo("/baseDir");
        assertThat(protoHeader.getWithSnippetMatching()).isEqualTo(true);
        assertThat(protoHeader.getWithStringSearch()).isEqualTo(true);
        assertThat(protoHeader.getRetainUnmatchedFiles()).isEqualTo(true);
        assertThat(protoHeader.getFileSystemSizeInBytes()).isEqualTo(100L);
    }

    @Test
    // Test when optional fields are missing
    public void testConvertBdioHeader2() {
        Instant instant = Instant.now();
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();

        BdioHeader header = new BdioHeader(ID, ScanType.SIGNATURE.name(), "codeLocationName", null, null,
                "publisherName", "publisherVersion", "publisherComment", "creator",
                instant, null, null, null, null, null, "/baseDir", true, true, null, null);

        ProtoScanHeader protoHeader = ProtobufBdioConverter.toProtoScanHeader(header);

        assertThat(protoHeader.getId()).isEqualTo(ID);
        assertThat(protoHeader.getScanType()).isEqualTo(ScanType.SIGNATURE);
        assertThat(protoHeader.getCodeLocationName()).isEqualTo("codeLocationName");
        assertThat(protoHeader.hasProjectName()).isFalse();
        assertThat(protoHeader.hasProjectVersionName()).isFalse();
        assertThat(protoHeader.getPublisherName()).isEqualTo("publisherName");
        assertThat(protoHeader.getPublisherVersion()).isEqualTo("publisherVersion");
        assertThat(protoHeader.getPublisherComment()).isEqualTo("publisherComment");
        assertThat(protoHeader.getCreator()).isEqualTo("creator");
        assertThat(protoHeader.getCreationTime()).isEqualTo(timestamp);
        assertThat(protoHeader.hasSourceRepository()).isFalse();
        assertThat(protoHeader.hasSourceBranch()).isFalse();
        assertThat(protoHeader.hasProjectGroupName()).isFalse();
        assertThat(protoHeader.hasCorrelationId()).isFalse();
        assertThat(protoHeader.hasMatchConfidenceThreshold()).isFalse();
        assertThat(protoHeader.getBaseDir()).isEqualTo("/baseDir");
        assertThat(protoHeader.getWithSnippetMatching()).isEqualTo(true);
        assertThat(protoHeader.getWithStringSearch()).isEqualTo(true);
        assertThat(protoHeader.hasRetainUnmatchedFiles()).isFalse();
        assertThat(protoHeader.hasFileSystemSizeInBytes()).isFalse();
    }

    @Test
    // test when all fields are non null
    public void testConvertToProtoFileNode1() {
        BdioFileNode bdioNode = new BdioFileNode(1L, -1L, "name", "FILE", 10L,
                11L, 12L, 13L, 14L, "path", "archiveContext", "uri", 100L,
                SIGNATURES);

        ProtoFileNode protoNode = ProtobufBdioConverter.toProtoFileNode(bdioNode);

        assertThat(protoNode.getId()).isEqualTo(1L);
        assertThat(protoNode.getParentId()).isEqualTo(-1L);
        assertThat(protoNode.getName()).isEqualTo("name");
        assertThat(protoNode.getFileSystemType()).isEqualTo("FILE");
        assertThat(protoNode.getShallowDirectoryCount()).isEqualTo(10L);
        assertThat(protoNode.getDeepDirectoryCount()).isEqualTo(11L);
        assertThat(protoNode.getDeepFileCount()).isEqualTo(12L);
        assertThat(protoNode.getDistanceFromRoot()).isEqualTo(13L);
        assertThat(protoNode.getDistanceFromInnerRoot()).isEqualTo(14L);
        assertThat(protoNode.getPath()).isEqualTo("path");
        assertThat(protoNode.getArchiveContext()).isEqualTo("archiveContext");
        assertThat(protoNode.getUri()).isEqualTo("uri");
        assertThat(protoNode.getSize()).isEqualTo(100L);

        assertThat(protoNode.getSignaturesMap().size()).isEqualTo(7);

        assertThat(protoNode.getSignaturesMap().containsKey(0)).isTrue();
        assertThat(protoNode.getSignaturesMap().containsValue("sha1")).isTrue();

        assertThat(protoNode.getSignaturesMap().containsKey(1)).isTrue();
        assertThat(protoNode.getSignaturesMap().containsValue("clean_sha1")).isTrue();

        assertThat(protoNode.getSignaturesMap().containsKey(2)).isTrue();
        assertThat(protoNode.getSignaturesMap().containsValue("deep_with_size")).isTrue();

        assertThat(protoNode.getSignaturesMap().containsKey(3)).isTrue();
        assertThat(protoNode.getSignaturesMap().containsValue("deep_no_size")).isTrue();

        assertThat(protoNode.getSignaturesMap().containsKey(4)).isTrue();
        assertThat(protoNode.getSignaturesMap().containsValue("structure_only")).isTrue();

        assertThat(protoNode.getSignaturesMap().containsKey(5)).isTrue();
        assertThat(protoNode.getSignaturesMap().containsValue("shallow_with_size")).isTrue();

        assertThat(protoNode.getSignaturesMap().containsKey(6)).isTrue();
        assertThat(protoNode.getSignaturesMap().containsValue("shallow_no_size")).isTrue();
    }

    @Test
    // test when optional fields are missing
    public void testConvertToProtoFileNode2() {
        Map<String, String> signatures = Collections.emptyMap();

        BdioFileNode bdioNode = new BdioFileNode(1L, -1L, "name", "FILE", null,
                null, null, null, null, "path", null, "uri", 100L,
                signatures);

        ProtoFileNode protoNode = ProtobufBdioConverter.toProtoFileNode(bdioNode);

        assertThat(protoNode.getId()).isEqualTo(1L);
        assertThat(protoNode.getParentId()).isEqualTo(-1L);
        assertThat(protoNode.getName()).isEqualTo("name");
        assertThat(protoNode.getFileSystemType()).isEqualTo("FILE");
        assertThat(protoNode.hasShallowDirectoryCount()).isFalse();
        assertThat(protoNode.hasDeepDirectoryCount()).isFalse();
        assertThat(protoNode.hasDeepFileCount()).isFalse();
        assertThat(protoNode.hasDistanceFromRoot()).isFalse();
        assertThat(protoNode.hasDistanceFromInnerRoot()).isFalse();
        assertThat(protoNode.getPath()).isEqualTo("path");
        assertThat(protoNode.hasArchiveContext()).isFalse();
        assertThat(protoNode.getUri()).isEqualTo("uri");
        assertThat(protoNode.getSize()).isEqualTo(100L);

        assertThat(protoNode.getSignaturesMap().size()).isEqualTo(0);
    }

    @Test
    // Test when all fields are non null
    public void testConvertToProtoComponentNode1() {
        BdioComponentNode bdioNode = new BdioComponentNode(ID, NAMESPACE, IDENTIFIER, DESCRIPTION_ID);
        ProtoComponentNode protoNode = ProtobufBdioConverter.toProtoComponentNode(bdioNode);

        assertThat(protoNode.getId()).isEqualTo(ID);
        assertThat(protoNode.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(protoNode.getIdentifier()).isEqualTo(IDENTIFIER);
        assertThat(protoNode.getDescriptionId()).isEqualTo(DESCRIPTION_ID);
    }

    @Test
    // Test when optional fields are missing
    public void testConvertToProtoComponentNode2() {
        BdioComponentNode bdioNode = new BdioComponentNode(ID, NAMESPACE, IDENTIFIER, null);
        ProtoComponentNode protoNode = ProtobufBdioConverter.toProtoComponentNode(bdioNode);

        assertThat(protoNode.getId()).isEqualTo(ID);
        assertThat(protoNode.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(protoNode.getIdentifier()).isEqualTo(IDENTIFIER);
        assertThat(protoNode.hasDescriptionId()).isEqualTo(false);
    }

    @Test
    // Test when all fields are non null
    public void testConvertToProtoDependencyNode1() {
        BdioDependencyNode bdioNode = new BdioDependencyNode(ID, EVIDENCE_ID, "containerLayerId", "whiteoutLayerId", DESCRIPTION_ID,
                MATCH_TYPES);

        ProtoDependencyNode protoNode = ProtobufBdioConverter.toProtoDependencyNode(bdioNode);
        assertThat(protoNode.getComponentId()).isEqualTo(ID);
        assertThat(protoNode.getEvidenceId()).isEqualTo(EVIDENCE_ID);
        assertThat(protoNode.getContainerLayer()).isEqualTo("containerLayerId");
        assertThat(protoNode.getWhiteoutLayer()).isEqualTo("whiteoutLayerId");
        assertThat(protoNode.getDescriptionId()).isEqualTo(DESCRIPTION_ID);

        List<BdbaMatchType> matchTypes = Arrays.stream(BdbaMatchType.values())
                .filter(mt -> !mt.name().equals("UNRECOGNIZED"))
                .collect(Collectors.toList());
        assertThat(protoNode.getMatchTypesList()).isEqualTo(matchTypes);
    }

    @Test
    // Test when optional fields are missing
    public void testConvertToProtoDependencyNode2() {
        BdioDependencyNode bdioNode = new BdioDependencyNode(ID, null, null, null, null,
                ImmutableList.of("Signature"));

        ProtoDependencyNode protoNode = ProtobufBdioConverter.toProtoDependencyNode(bdioNode);
        assertThat(protoNode.getComponentId()).isEqualTo(ID);
        assertThat(protoNode.hasEvidenceId()).isFalse();
        assertThat(protoNode.hasContainerLayer()).isFalse();
        assertThat(protoNode.hasWhiteoutLayer()).isFalse();
        assertThat(protoNode.hasDescriptionId()).isFalse();

        assertThat(protoNode.getMatchTypesList()).isEqualTo(ImmutableList.of(BdbaMatchType.Signature));
    }

    @Test
    public void testConvertToProtoAnnotationNode() {
        BdioAnnotationNode bdioNode = new BdioAnnotationNode(ID, COMMENT);
        ProtoAnnotationNode protoNode = ProtobufBdioConverter.toProtoAnnotationNode(bdioNode);

        assertThat(protoNode.getId()).isEqualTo(ID);
        assertThat(protoNode.getComment()).isEqualTo(COMMENT);
    }

    @Test
    // Test when all fields are non null
    public void testConvertToProtoContainerNode1() {
        Instant instant = Instant.now();
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
        BdioContainerNode bdioNode = new BdioContainerNode(ID, "imageId", "linux/arm64", ImmutableList.of("repoTag1"), "linux",
                instant, "config", ImmutableList.of("layerId1", "layerId2"), ImmutableList.of("foo/bar/image.tar"));

        ProtoContainerNode protoNode = ProtobufBdioConverter.toProtoContainerNode(bdioNode);
        assertThat(protoNode.getId()).isEqualTo(ID);
        assertThat(protoNode.getImage()).isEqualTo("imageId");
        assertThat(protoNode.getArchitecture()).isEqualTo("linux/arm64");
        assertThat(protoNode.getRepoTagsList()).isEqualTo(ImmutableList.of("repoTag1"));
        assertThat(protoNode.getOs()).isEqualTo("linux");
        assertThat(protoNode.getCreatedAt()).isEqualTo(timestamp);
        assertThat(protoNode.getConfig()).isEqualTo("config");
        assertThat(protoNode.getLayersList()).isEqualTo(ImmutableList.of("layerId1", "layerId2"));
        assertThat(protoNode.getImagePathsList()).isEqualTo(ImmutableList.of("foo/bar/image.tar"));
    }

    @Test
    // Test when optional fields are missing
    public void testConvertToProtoContainerNode2() {
        BdioContainerNode bdioNode = new BdioContainerNode(ID, "imageId", "linux/arm64", ImmutableList.of(), "linux",
                null, "config", ImmutableList.of("layerId1", "layerId2"), ImmutableList.of());

        ProtoContainerNode protoNode = ProtobufBdioConverter.toProtoContainerNode(bdioNode);
        assertThat(protoNode.getId()).isEqualTo(ID);
        assertThat(protoNode.getImage()).isEqualTo("imageId");
        assertThat(protoNode.getArchitecture()).isEqualTo("linux/arm64");
        assertThat(protoNode.getRepoTagsList().isEmpty()).isTrue();
        assertThat(protoNode.getOs()).isEqualTo("linux");
        assertThat(protoNode.getConfig()).isEqualTo("config");
        assertThat(protoNode.getLayersList()).isEqualTo(ImmutableList.of("layerId1", "layerId2"));
        assertThat(protoNode.getImagePathsList()).isEqualTo(ImmutableList.of());
    }

    @Test
    // Test when all fields are non null
    public void testConvertToProtoContainerLayerNode1() {
        Instant instant = Instant.now();
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();

        BdioContainerLayerNode bdioNode = new BdioContainerLayerNode(ID, "layerId1", 100L, "command", instant, DESCRIPTION_ID);
        ProtoContainerLayerNode protoNode = ProtobufBdioConverter.toProtoContainerLayerNode(bdioNode);

        assertThat(protoNode.getId()).isEqualTo(ID);
        assertThat(protoNode.getLayer()).isEqualTo("layerId1");
        assertThat(protoNode.getSize()).isEqualTo(100L);
        assertThat(protoNode.getCommand()).isEqualTo("command");
        assertThat(protoNode.getCreatedAt()).isEqualTo(timestamp);
        assertThat(protoNode.getComment()).isEqualTo(DESCRIPTION_ID);
    }

    @Test
    // Test when optional fields are missing
    public void testConvertToProtoContainerLayerNode2() {
        BdioContainerLayerNode bdioNode = new BdioContainerLayerNode(ID, "layerId1", 100L, null, null, null);
        ProtoContainerLayerNode protoNode = ProtobufBdioConverter.toProtoContainerLayerNode(bdioNode);

        assertThat(protoNode.getId()).isEqualTo(ID);
        assertThat(protoNode.getLayer()).isEqualTo("layerId1");
        assertThat(protoNode.getSize()).isEqualTo(100L);
        assertThat(protoNode.hasCommand()).isFalse();
        assertThat(protoNode.hasCreatedAt()).isFalse();
        assertThat(protoNode.hasComment()).isFalse();
    }

    @Test
    // Test when all fields are non null
    public void testConvertToBdioScanHeader1() {
        Timestamp timestamp = Timestamp.getDefaultInstance();
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());

        ProtoScanHeader protoHeader = ProtoScanHeader.newBuilder()
                .setId(ID)
                .setScanType(ScanType.SIGNATURE)
                .setCodeLocationName("codeLocationName")
                .setProjectName("projectName")
                .setProjectVersionName("projectVersionName")
                .setPublisherName("publisherName")
                .setPublisherVersion("publisherVersion")
                .setPublisherComment("publisherComment")
                .setCreator("creator")
                .setCreationTime(timestamp)
                .setSourceRepository("sourceRepository")
                .setSourceBranch("sourceBranch")
                .setProjectGroupName("projectGroupName")
                .setCorrelationId(CORRELATION_ID.toString())
                .setMatchConfidenceThreshold(10L)
                .setBaseDir("/baseDir")
                .setWithSnippetMatching(true)
                .setWithStringSearch(true)
                .setRetainUnmatchedFiles(true)
                .setFileSystemSizeInBytes(100L)
                .build();

        short version = 1;
        BdioHeader bdioHeader = ProtobufBdioConverter.toBdioHeader(protoHeader, version);
        assertThat(bdioHeader.getId()).isEqualTo(ID);
        assertThat(bdioHeader.getScanType()).isEqualTo("SIGNATURE");
        assertThat(bdioHeader.getCodeLocationName()).isEqualTo("codeLocationName");
        assertThat(bdioHeader.getProjectName().get()).isEqualTo("projectName");
        assertThat(bdioHeader.getVersionName().get()).isEqualTo("projectVersionName");
        assertThat(bdioHeader.getPublisherName()).isEqualTo("publisherName");
        assertThat(bdioHeader.getPublisherVersion()).isEqualTo("publisherVersion");
        assertThat(bdioHeader.getPublisherComment()).isEqualTo("publisherComment");
        assertThat(bdioHeader.getCreator()).isEqualTo("creator");
        assertThat(bdioHeader.getCreationTime()).isEqualTo(instant);
        assertThat(bdioHeader.getSourceRepository().get()).isEqualTo("sourceRepository");
        assertThat(bdioHeader.getSourceBranch().get()).isEqualTo("sourceBranch");
        assertThat(bdioHeader.getProjectGroupName().get()).isEqualTo("projectGroupName");
        assertThat(bdioHeader.getCorrelationId().get()).isEqualTo(CORRELATION_ID);
        assertThat(bdioHeader.getMatchConfidenceThreshold().get()).isEqualTo(10L);
        assertThat(bdioHeader.getBaseDir()).isEqualTo("/baseDir");
        assertThat(bdioHeader.isWithSnippetMatching()).isEqualTo(true);
        assertThat(bdioHeader.isWithStringSearch()).isEqualTo(true);
        assertThat(bdioHeader.isRetainUnmatchedFiles().get()).isEqualTo(true);
        assertThat(bdioHeader.getFileSystemSizeInBytes().get()).isEqualTo(100L);
    }

    @Test
    // Test when optional fields are missing
    public void testConvertToBdioScanHeader2() {
        Timestamp timestamp = Timestamp.getDefaultInstance();
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());

        ProtoScanHeader protoHeader = ProtoScanHeader.newBuilder()
                .setId(ID)
                .setScanType(ScanType.SIGNATURE)
                .setCodeLocationName("codeLocationName")
                .setPublisherName("publisherName")
                .setPublisherVersion("publisherVersion")
                .setPublisherComment("publisherComment")
                .setCreator("creator")
                .setCreationTime(timestamp)
                .setBaseDir("/baseDir")
                .setWithSnippetMatching(true)
                .setWithStringSearch(true)
                .build();

        short version = 1;
        BdioHeader bdioHeader = ProtobufBdioConverter.toBdioHeader(protoHeader, version);
        assertThat(bdioHeader.getId()).isEqualTo(ID);
        assertThat(bdioHeader.getScanType()).isEqualTo("SIGNATURE");
        assertThat(bdioHeader.getCodeLocationName()).isEqualTo("codeLocationName");
        assertThat(bdioHeader.getPublisherName()).isEqualTo("publisherName");
        assertThat(bdioHeader.getPublisherVersion()).isEqualTo("publisherVersion");
        assertThat(bdioHeader.getPublisherComment()).isEqualTo("publisherComment");
        assertThat(bdioHeader.getCreator()).isEqualTo("creator");
        assertThat(bdioHeader.getCreationTime()).isEqualTo(instant);
        assertThat(bdioHeader.getBaseDir()).isEqualTo("/baseDir");
        assertThat(bdioHeader.isWithSnippetMatching()).isEqualTo(true);
        assertThat(bdioHeader.isWithStringSearch()).isEqualTo(true);
    }

    @Test
    // Test when all fields are non null
    public void testConvertToBdioFileNode1() {
        Map<Integer, String> signatures = ImmutableMap.<Integer, String> builder()
                .put(0, "sha1")
                .put(1, "clean_sha1")
                .put(2, "deep_with_size")
                .put(3, "deep_no_size")
                .put(4, "structure_only")
                .put(5, "shallow_with_size")
                .put(6, "shallow_no_size")
                .build();

        ProtoFileNode protoNode = ProtoFileNode.newBuilder()
                .setId(1L)
                .setParentId(-1L)
                .setName("name")
                .setFileSystemType("FILE")
                .setShallowDirectoryCount(10L)
                .setDeepDirectoryCount(11L)
                .setDeepFileCount(12L)
                .setDistanceFromRoot(13L)
                .setDistanceFromInnerRoot(14L)
                .setPath("path")
                .setArchiveContext("archiveContext")
                .setUri("uri")
                .setSize(100L)
                .putAllSignatures(signatures)
                .build();

        BdioFileNode bdioNode = ProtobufBdioConverter.toBdioFileNode(protoNode);

        assertThat(bdioNode.getId()).isEqualTo(1L);
        assertThat(bdioNode.getParentId()).isEqualTo(-1L);
        assertThat(bdioNode.getName()).isEqualTo("name");
        assertThat(bdioNode.getFileSystemType()).isEqualTo("FILE");
        assertThat(bdioNode.getShallowDirectoryCount().get()).isEqualTo(10L);
        assertThat(bdioNode.getDeepDirectoryCount().get()).isEqualTo(11L);
        assertThat(bdioNode.getDeepFileCount().get()).isEqualTo(12L);
        assertThat(bdioNode.getDistanceFromRoot().get()).isEqualTo(13L);
        assertThat(bdioNode.getDistanceFromInnerRoot().get()).isEqualTo(14L);
        assertThat(bdioNode.getPath()).isEqualTo("path");
        assertThat(bdioNode.getArchiveContext().get()).isEqualTo("archiveContext");
        assertThat(bdioNode.getUri()).isEqualTo("uri");
        assertThat(bdioNode.getSize()).isEqualTo(100L);

        assertThat(bdioNode.getSignatures().size()).isEqualTo(7);

        assertThat(bdioNode.getSignatures().containsKey("MATCH_SHA1")).isTrue();
        assertThat(bdioNode.getSignatures().containsValue("sha1")).isTrue();

        assertThat(bdioNode.getSignatures().containsKey("MATCH_CLEAN_SHA1")).isTrue();
        assertThat(bdioNode.getSignatures().containsValue("clean_sha1")).isTrue();

        assertThat(bdioNode.getSignatures().containsKey("DEEP_WITH_SIZE")).isTrue();
        assertThat(bdioNode.getSignatures().containsValue("deep_with_size")).isTrue();

        assertThat(bdioNode.getSignatures().containsKey("DEEP_NO_SIZE")).isTrue();
        assertThat(bdioNode.getSignatures().containsValue("deep_no_size")).isTrue();

        assertThat(bdioNode.getSignatures().containsKey("STRUCTURE_ONLY")).isTrue();
        assertThat(bdioNode.getSignatures().containsValue("structure_only")).isTrue();

        assertThat(bdioNode.getSignatures().containsKey("SHALLOW_WITH_SIZE")).isTrue();
        assertThat(bdioNode.getSignatures().containsValue("shallow_with_size")).isTrue();

        assertThat(bdioNode.getSignatures().containsKey("SHALLOW_NO_SIZE")).isTrue();
        assertThat(bdioNode.getSignatures().containsValue("shallow_no_size")).isTrue();
    }

    @Test
    // Test when optional fields are missing
    public void testConvertToBdioFileNode2() {
        ProtoFileNode protoNode = ProtoFileNode.newBuilder()
                .setId(1L)
                .setParentId(-1L)
                .setName("name")
                .setFileSystemType("FILE")
                .setPath("path")
                .setUri("uri")
                .setSize(100L)
                .putAllSignatures(Collections.emptyMap())
                .build();

        BdioFileNode bdioNode = ProtobufBdioConverter.toBdioFileNode(protoNode);

        assertThat(bdioNode.getId()).isEqualTo(1L);
        assertThat(bdioNode.getParentId()).isEqualTo(-1L);
        assertThat(bdioNode.getName()).isEqualTo("name");
        assertThat(bdioNode.getFileSystemType()).isEqualTo("FILE");
        assertThat(bdioNode.getShallowDirectoryCount().isPresent()).isFalse();
        assertThat(bdioNode.getDeepDirectoryCount().isPresent()).isFalse();
        assertThat(bdioNode.getDeepFileCount().isPresent()).isFalse();
        assertThat(bdioNode.getDistanceFromRoot().isPresent()).isFalse();
        assertThat(bdioNode.getDistanceFromInnerRoot().isPresent()).isFalse();
        assertThat(bdioNode.getPath()).isEqualTo("path");
        assertThat(bdioNode.getArchiveContext().isPresent()).isFalse();
        assertThat(bdioNode.getUri()).isEqualTo("uri");
        assertThat(bdioNode.getSize()).isEqualTo(100L);

        assertThat(bdioNode.getSignatures().size()).isEqualTo(0);
    }

    @Test
    // Test when all fields are non null
    public void testConvertToBdioComponentNode1() {
        ProtoComponentNode protoNode = ProtoComponentNode.newBuilder()
                .setId(ID)
                .setNamespace(NAMESPACE)
                .setIdentifier(IDENTIFIER)
                .setDescriptionId(DESCRIPTION_ID)
                .build();
        BdioComponentNode bdioNode = ProtobufBdioConverter.toBdioComponentNode(protoNode);

        assertThat(bdioNode.getId()).isEqualTo(ID);
        assertThat(bdioNode.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(bdioNode.getIdentifier()).isEqualTo(IDENTIFIER);
        assertThat(bdioNode.getDescriptionId().get()).isEqualTo(DESCRIPTION_ID);
    }

    @Test
    // Test when optional fields are missing
    public void testConvertToBdioComponentNode2() {
        ProtoComponentNode protoNode = ProtoComponentNode.newBuilder()
                .setId(ID)
                .setNamespace(NAMESPACE)
                .setIdentifier(IDENTIFIER)
                .build();
        BdioComponentNode bdioNode = ProtobufBdioConverter.toBdioComponentNode(protoNode);

        assertThat(bdioNode.getId()).isEqualTo(ID);
        assertThat(bdioNode.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(bdioNode.getIdentifier()).isEqualTo(IDENTIFIER);
        assertThat(bdioNode.getDescriptionId().isPresent()).isEqualTo(false);
    }

    @Test
    // Test when all fields are non null
    public void testConvertToBdioDependencyNode1() {
        ProtoDependencyNode protoNode = ProtoDependencyNode.newBuilder()
                .setComponentId(ID)
                .setEvidenceId(EVIDENCE_ID)
                .setContainerLayer("containerLayerId")
                .setWhiteoutLayer("whiteoutLayerId")
                .setDescriptionId(DESCRIPTION_ID)
                .build();

        BdioDependencyNode bdioNode = ProtobufBdioConverter.toBdioDependencyNode(protoNode);
        assertThat(bdioNode.getComponentId()).isEqualTo(ID);
        assertThat(bdioNode.getEvidenceId().get()).isEqualTo(EVIDENCE_ID);
        assertThat(bdioNode.getContainerLayer().get()).isEqualTo("containerLayerId");
        assertThat(bdioNode.getWhiteoutLayer().get()).isEqualTo("whiteoutLayerId");
        assertThat(bdioNode.getDescriptionId().get()).isEqualTo(DESCRIPTION_ID);
    }

    @Test
    // Test when optional fields are missing
    public void testConvertToBdioDependencyNode2() {
        ProtoDependencyNode protoNode = ProtoDependencyNode.newBuilder()
                .setComponentId(ID)
                .build();

        BdioDependencyNode bdioNode = ProtobufBdioConverter.toBdioDependencyNode(protoNode);
        assertThat(bdioNode.getComponentId()).isEqualTo(ID);
        assertThat(bdioNode.getEvidenceId().isPresent()).isFalse();
        assertThat(bdioNode.getContainerLayer().isPresent()).isFalse();
        assertThat(bdioNode.getWhiteoutLayer().isPresent()).isFalse();
        assertThat(bdioNode.getDescriptionId().isPresent()).isFalse();
    }

    @Test
    public void testConvertToBdioAnnotationNode() {
        ProtoAnnotationNode protoNode = ProtoAnnotationNode.newBuilder()
                .setId(ID)
                .setComment(COMMENT)
                .build();

        BdioAnnotationNode bdioNode = ProtobufBdioConverter.toBdioAnnotationNode(protoNode);
        assertThat(bdioNode.getId()).isEqualTo(ID);
        assertThat(bdioNode.getComment()).isEqualTo(COMMENT);
    }

    @Test
    // Test when all fields are non null
    public void testConvertToBdioContainerNode1() {
        Timestamp timestamp = Timestamp.getDefaultInstance();
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        ProtoContainerNode protoNode = ProtoContainerNode.newBuilder()
                .setId(ID)
                .setImage("imageId")
                .setArchitecture("linux/arm64")
                .addAllRepoTags(ImmutableList.of("repoTag1"))
                .setOs("linux")
                .setConfig("config")
                .setCreatedAt(timestamp)
                .addAllLayers(ImmutableList.of("layerId1", "layerId2"))
                .build();
        BdioContainerNode bdioNode = ProtobufBdioConverter.toBdioContainerNode(protoNode);
        assertThat(bdioNode.getId()).isEqualTo(ID);
        assertThat(bdioNode.getImage()).isEqualTo("imageId");
        assertThat(bdioNode.getArchitecture()).isEqualTo("linux/arm64");
        assertThat(bdioNode.getRepoTags()).isEqualTo(ImmutableList.of("repoTag1"));
        assertThat(bdioNode.getOs()).isEqualTo("linux");
        assertThat(bdioNode.getConfig()).isEqualTo("config");
        assertThat(bdioNode.getCreatedAt().get()).isEqualTo(instant);
        assertThat(bdioNode.getLayers()).isEqualTo(ImmutableList.of("layerId1", "layerId2"));
    }

    @Test
    // Test when optional fields are missing
    public void testConvertToBdioContainerNode2() {
        ProtoContainerNode protoNode = ProtoContainerNode.newBuilder()
                .setId(ID)
                .setImage("imageId")
                .setArchitecture("linux/arm64")
                .setOs("linux")
                .setConfig("config")
                .addAllLayers(ImmutableList.of("layerId1", "layerId2"))
                .build();
        BdioContainerNode bdioNode = ProtobufBdioConverter.toBdioContainerNode(protoNode);
        assertThat(bdioNode.getId()).isEqualTo(ID);
        assertThat(bdioNode.getImage()).isEqualTo("imageId");
        assertThat(bdioNode.getArchitecture()).isEqualTo("linux/arm64");
        assertThat(bdioNode.getOs()).isEqualTo("linux");
        assertThat(bdioNode.getConfig()).isEqualTo("config");
        assertThat(bdioNode.getLayers()).isEqualTo(ImmutableList.of("layerId1", "layerId2"));
    }

    @Test
    // Test when all fields are non null
    public void testConvertToBdioContainerLayerNode1() {
        Timestamp timestamp = Timestamp.getDefaultInstance();
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());

        ProtoContainerLayerNode protoNode = ProtoContainerLayerNode.newBuilder()
                .setId(ID)
                .setLayer("layerId1")
                .setSize(100L)
                .setCommand("command")
                .setCreatedAt(timestamp)
                .setComment(DESCRIPTION_ID)
                .build();
        BdioContainerLayerNode bdioNode = ProtobufBdioConverter.toBdioContainerLayerNode(protoNode);

        assertThat(bdioNode.getId()).isEqualTo(ID);
        assertThat(bdioNode.getLayer()).isEqualTo("layerId1");
        assertThat(bdioNode.getSize()).isEqualTo(100L);
        assertThat(bdioNode.getCommand().get()).isEqualTo("command");
        assertThat(bdioNode.getCreatedAt().get()).isEqualTo(instant);
        assertThat(bdioNode.getComment().get()).isEqualTo(DESCRIPTION_ID);
    }

    @Test
    // Test when optional fields are missing
    public void testConvertToBdioContainerLayerNode2() {
        ProtoContainerLayerNode protoNode = ProtoContainerLayerNode.newBuilder()
                .setId(ID)
                .setLayer("layerId1")
                .setSize(100L)
                .build();
        BdioContainerLayerNode bdioNode = ProtobufBdioConverter.toBdioContainerLayerNode(protoNode);

        assertThat(bdioNode.getId()).isEqualTo(ID);
        assertThat(bdioNode.getLayer()).isEqualTo("layerId1");
        assertThat(bdioNode.getSize()).isEqualTo(100L);
    }

    @Test
    // Test when all fields are non null
    public void testConvertToBdioBdbaFileNode1() {
        Timestamp timestamp = Timestamp.getDefaultInstance();
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());

        ProtoBdbaFileNode bdbaFileNode = ProtoBdbaFileNode.newBuilder()
                .setId(ID)
                .setUri("uri")
                .setSize(1L)
                .setLastModifiedDateTime(timestamp)
                .setFileSystemType("FILE")
                .putAllSignatures(ImmutableMap.of("MATCH_SHA_1", "sha1"))
                .build();
        BdioBdbaFileNode bdioNode = ProtobufBdioConverter.toBdioBdbaFileNode(bdbaFileNode);

        assertThat(bdioNode.getId()).isEqualTo(ID);
        assertThat(bdioNode.getUri()).isEqualTo("uri");
        assertThat(bdioNode.getSize()).isEqualTo(1L);
        assertThat(bdioNode.getLastModifiedDateTime()).isEqualTo(instant);
        assertThat(bdioNode.getFileSystemType().get()).isEqualTo("FILE");
        assertThat(bdioNode.getSignatures()).isEqualTo(ImmutableMap.of("MATCH_SHA_1", "sha1"));
    }

    @Test
    // Test when optional fields are missing
    public void testConvertToBdioBdbaFileNode2() {
        Timestamp timestamp = Timestamp.getDefaultInstance();
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());

        ProtoBdbaFileNode bdbaFileNode = ProtoBdbaFileNode.newBuilder()
                .setId(ID)
                .setUri("uri")
                .setSize(1L)
                .setLastModifiedDateTime(timestamp)
                .build();
        BdioBdbaFileNode bdioNode = ProtobufBdioConverter.toBdioBdbaFileNode(bdbaFileNode);

        assertThat(bdioNode.getId()).isEqualTo(ID);
        assertThat(bdioNode.getUri()).isEqualTo("uri");
        assertThat(bdioNode.getSize()).isEqualTo(1L);
        assertThat(bdioNode.getLastModifiedDateTime()).isEqualTo(instant);
    }

}
