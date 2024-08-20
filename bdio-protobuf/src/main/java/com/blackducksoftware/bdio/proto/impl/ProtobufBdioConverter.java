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
package com.blackducksoftware.bdio.proto.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio.proto.api.BdioAnnotationNode;
import com.blackducksoftware.bdio.proto.api.BdioBdbaFileNode;
import com.blackducksoftware.bdio.proto.api.BdioChunk;
import com.blackducksoftware.bdio.proto.api.BdioComponentNode;
import com.blackducksoftware.bdio.proto.api.BdioContainerLayerNode;
import com.blackducksoftware.bdio.proto.api.BdioContainerNode;
import com.blackducksoftware.bdio.proto.api.BdioDependencyNode;
import com.blackducksoftware.bdio.proto.api.BdioFileNode;
import com.blackducksoftware.bdio.proto.api.BdioHeader;
import com.blackducksoftware.bdio.proto.api.BdioValidationException;
import com.blackducksoftware.bdio.proto.api.IBdioNode;
import com.blackducksoftware.bdio.proto.domain.BdbaMatchType;
import com.blackducksoftware.bdio.proto.domain.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.domain.ProtoBdbaFileNode;
import com.blackducksoftware.bdio.proto.domain.ProtoChunk;
import com.blackducksoftware.bdio.proto.domain.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerLayerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;
import com.blackducksoftware.bdio.proto.domain.ProtoScanHeader;
import com.blackducksoftware.bdio.proto.domain.ScanType;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;

/**
 *
 * @author sharapov
 *
 */
public class ProtobufBdioConverter {

    private enum SignatureType {
        MATCH_SHA1, MATCH_CLEAN_SHA1, DEEP_WITH_SIZE, DEEP_NO_SIZE, STRUCTURE_ONLY, SHALLOW_WITH_SIZE, SHALLOW_NO_SIZE
    }

    private static final BiMap<Integer, String> SIGNATURE_TYPES = new ImmutableBiMap.Builder<Integer, String>()
            .put(0, SignatureType.MATCH_SHA1.name())
            .put(1, SignatureType.MATCH_CLEAN_SHA1.name())
            .put(2, SignatureType.DEEP_WITH_SIZE.name())
            .put(3, SignatureType.DEEP_NO_SIZE.name())
            .put(4, SignatureType.STRUCTURE_ONLY.name())
            .put(5, SignatureType.SHALLOW_WITH_SIZE.name())
            .put(6, SignatureType.SHALLOW_NO_SIZE.name()).build();

    public static Message toProtobuf(IBdioNode bdioNode) {
        if (bdioNode instanceof BdioFileNode) {
            return toProtoFileNode((BdioFileNode) bdioNode);
        } else if (bdioNode instanceof BdioDependencyNode) {
            return toProtoDependencyNode((BdioDependencyNode) bdioNode);
        } else if (bdioNode instanceof BdioComponentNode) {
            return toProtoComponentNode((BdioComponentNode) bdioNode);
        } else if (bdioNode instanceof BdioAnnotationNode) {
            return toProtoAnnotationNode((BdioAnnotationNode) bdioNode);
        } else if (bdioNode instanceof BdioContainerNode) {
            return toProtoContainerNode((BdioContainerNode) bdioNode);
        } else if (bdioNode instanceof BdioContainerLayerNode) {
            return toProtoContainerLayerNode((BdioContainerLayerNode) bdioNode);
        } else if (bdioNode instanceof BdioBdbaFileNode) {
            return toProtoBdbaFileNode((BdioBdbaFileNode) bdioNode);
        }

        throw new BdioValidationException("Unknown bdio node type: " + bdioNode.getClass().getName());
    }

    public static ProtoScanHeader toProtoScanHeader(BdioHeader bdioHeader) {

        ProtoScanHeader.Builder builder = ProtoScanHeader.newBuilder();

        callIfParamNonNull(builder::setId, bdioHeader.getId());
        builder.setScanType(ScanType.valueOf(bdioHeader.getScanType()));
        callIfParamNonNull(builder::setCodeLocationName, bdioHeader.getCodeLocationName());
        callIfParamNonNull(builder::setPublisherName, bdioHeader.getPublisherName());
        callIfParamNonNull(builder::setPublisherVersion, bdioHeader.getPublisherVersion());
        callIfParamNonNull(builder::setPublisherComment, bdioHeader.getPublisherComment());
        callIfParamNonNull(builder::setCreator, bdioHeader.getCreator());
        callIfParamNonNull(builder::setCreationTime, toTimestamp(bdioHeader.getCreationTime()));
        callIfParamNonNull(builder::setBaseDir, bdioHeader.getBaseDir());
        builder.setWithStringSearch(bdioHeader.isWithStringSearch());
        builder.setWithSnippetMatching(bdioHeader.isWithSnippetMatching());

        bdioHeader.getProjectGroupName().ifPresent(builder::setProjectGroupName);
        bdioHeader.getProjectName().ifPresent(builder::setProjectName);
        bdioHeader.getVersionName().ifPresent(builder::setProjectVersionName);
        bdioHeader.getSourceRepository().ifPresent(builder::setSourceRepository);
        bdioHeader.getSourceBranch().ifPresent(builder::setSourceBranch);
        bdioHeader.getCorrelationId().map(cid -> cid.toString()).ifPresent(builder::setCorrelationId);
        bdioHeader.getMatchConfidenceThreshold().ifPresent(builder::setMatchConfidenceThreshold);
        bdioHeader.isRetainUnmatchedFiles().ifPresent(builder::setRetainUnmatchedFiles);
        bdioHeader.getFileSystemSizeInBytes().ifPresent(builder::setFileSystemSizeInBytes);

        return builder.build();
    }

    public static ProtoFileNode toProtoFileNode(BdioFileNode bdioFileNode) {
        Map<Integer, String> sigs = new HashMap<>();
        for (Map.Entry<String, String> entry : bdioFileNode.getSignatures().entrySet()) {
            Integer signatureType = SIGNATURE_TYPES.inverse().get(entry.getKey());
            if (signatureType == null) {
                throw new BdioValidationException("Unknown signature type: " + entry.getKey());
            }
            sigs.put(signatureType, entry.getValue());
        }

        ProtoFileNode.Builder builder = ProtoFileNode.newBuilder();

        builder.setId(bdioFileNode.getId());
        builder.setParentId(bdioFileNode.getParentId());
        callIfParamNonNull(builder::setName, bdioFileNode.getName());
        callIfParamNonNull(builder::setFileSystemType, bdioFileNode.getFileSystemType());
        callIfParamNonNull(builder::setShallowDirectoryCount, bdioFileNode.getShallowDirectoryCount().orElse(null));
        callIfParamNonNull(builder::setDeepDirectoryCount, bdioFileNode.getDeepDirectoryCount().orElse(null));
        callIfParamNonNull(builder::setDeepFileCount, bdioFileNode.getDeepFileCount().orElse(null));
        callIfParamNonNull(builder::setDistanceFromRoot, bdioFileNode.getDistanceFromRoot().orElse(null));
        callIfParamNonNull(builder::setDistanceFromInnerRoot, bdioFileNode.getDistanceFromInnerRoot().orElse(null));
        callIfParamNonNull(builder::setPath, bdioFileNode.getPath());
        callIfParamNonNull(builder::setArchiveContext, bdioFileNode.getArchiveContext().orElse(null));
        callIfParamNonNull(builder::setUri, bdioFileNode.getUri());
        builder.setSize(bdioFileNode.getSize());
        builder.putAllSignatures(sigs);

        return builder.build();
    }

    public static ProtoDependencyNode toProtoDependencyNode(BdioDependencyNode bdioDepenencyNode) {
        ProtoDependencyNode.Builder builder = ProtoDependencyNode.newBuilder();

        callIfParamNonNull(builder::setComponentId, bdioDepenencyNode.getComponentId());
        callIfParamNonNull(builder::setEvidenceId, bdioDepenencyNode.getEvidenceId().orElse(null));
        callIfParamNonNull(builder::setContainerLayer, bdioDepenencyNode.getContainerLayer().orElse(null));
        callIfParamNonNull(builder::setWhiteoutLayer, bdioDepenencyNode.getWhiteoutLayer().orElse(null));
        callIfParamNonNull(builder::setDescriptionId, bdioDepenencyNode.getDescriptionId().orElse(null));

        bdioDepenencyNode.getMatchTypes().stream().map(mt -> BdbaMatchType.valueOf(mt)).forEach(builder::addMatchTypes);

        return builder.build();
    }

    public static ProtoComponentNode toProtoComponentNode(BdioComponentNode bdioComponentNode) {
        ProtoComponentNode.Builder builder = ProtoComponentNode.newBuilder();

        callIfParamNonNull(builder::setId, bdioComponentNode.getId());
        callIfParamNonNull(builder::setNamespace, bdioComponentNode.getNamespace());
        callIfParamNonNull(builder::setIdentifier, bdioComponentNode.getIdentifier());
        callIfParamNonNull(builder::setDescriptionId, bdioComponentNode.getDescriptionId().orElse(null));

        return builder.build();
    }

    public static ProtoAnnotationNode toProtoAnnotationNode(BdioAnnotationNode bdioAnnotationNode) {
        ProtoAnnotationNode.Builder builder = ProtoAnnotationNode.newBuilder();

        callIfParamNonNull(builder::setId, bdioAnnotationNode.getId());
        callIfParamNonNull(builder::setComment, bdioAnnotationNode.getComment());

        return builder.build();
    }

    public static ProtoContainerNode toProtoContainerNode(BdioContainerNode bdioContainerNode) {
        ProtoContainerNode.Builder builder = ProtoContainerNode.newBuilder();

        callIfParamNonNull(builder::setId, bdioContainerNode.getId());
        callIfParamNonNull(builder::setImage, bdioContainerNode.getImage());
        callIfParamNonNull(builder::setArchitecture, bdioContainerNode.getArchitecture());
        bdioContainerNode.getRepoTags().stream().forEach(builder::addRepoTags);
        callIfParamNonNull(builder::setOs, bdioContainerNode.getOs());
        callIfParamNonNull(builder::setCreatedAt, toTimestamp(bdioContainerNode.getCreatedAt().orElse(null)));
        callIfParamNonNull(builder::setConfig, bdioContainerNode.getConfig());
        bdioContainerNode.getLayers().stream().forEach(builder::addLayers);
        bdioContainerNode.getImagePaths().stream().filter(ip -> ip != null).forEach(builder::addImagePaths);

        return builder.build();
    }

    public static ProtoContainerLayerNode toProtoContainerLayerNode(BdioContainerLayerNode bdioContainerLayerNode) {
        ProtoContainerLayerNode.Builder builder = ProtoContainerLayerNode.newBuilder();

        callIfParamNonNull(builder::setId, bdioContainerLayerNode.getId());
        callIfParamNonNull(builder::setLayer, bdioContainerLayerNode.getLayer());
        builder.setSize(bdioContainerLayerNode.getSize());
        callIfParamNonNull(builder::setCommand, bdioContainerLayerNode.getCommand().orElse(null));
        callIfParamNonNull(builder::setCreatedAt, toTimestamp(bdioContainerLayerNode.getCreatedAt().orElse(null)));
        callIfParamNonNull(builder::setComment, bdioContainerLayerNode.getComment().orElse(null));

        return builder.build();
    }

    public static ProtoBdbaFileNode toProtoBdbaFileNode(BdioBdbaFileNode bdioBdbaFileNode) {
        ProtoBdbaFileNode.Builder builder = ProtoBdbaFileNode.newBuilder();

        callIfParamNonNull(builder::setId, bdioBdbaFileNode.getId());
        callIfParamNonNull(builder::setUri, bdioBdbaFileNode.getUri());
        builder.setSize(bdioBdbaFileNode.getSize());
        callIfParamNonNull(builder::setLastModifiedDateTime, toTimestamp(bdioBdbaFileNode.getLastModifiedDateTime()));
        callIfParamNonNull(builder::setFileSystemType, bdioBdbaFileNode.getFileSystemType().orElse(null));
        builder.putAllSignatures(bdioBdbaFileNode.getSignatures());

        return builder.build();
    }

    public static BdioHeader toBdioHeader(ProtoScanHeader protoScanHeader, short version) {
        BdioHeader header = new BdioHeader(
                protoScanHeader.getId(),
                protoScanHeader.getScanType().name(),
                protoScanHeader.getCodeLocationName(),
                protoScanHeader.hasProjectName() ? protoScanHeader.getProjectName() : null,
                protoScanHeader.hasProjectVersionName() ? protoScanHeader.getProjectVersionName() : null,
                protoScanHeader.getPublisherName(),
                protoScanHeader.getPublisherVersion(),
                protoScanHeader.getPublisherComment(),
                protoScanHeader.getCreator(),
                toInstant(protoScanHeader.getCreationTime()),
                protoScanHeader.hasSourceRepository() ? protoScanHeader.getSourceRepository() : null,
                protoScanHeader.hasSourceBranch() ? protoScanHeader.getSourceBranch() : null,
                protoScanHeader.hasProjectGroupName() ? protoScanHeader.getProjectGroupName() : null,
                protoScanHeader.hasCorrelationId() ? toUUID(protoScanHeader.getCorrelationId()) : null,
                protoScanHeader.hasMatchConfidenceThreshold() ? protoScanHeader.getMatchConfidenceThreshold() : null,
                protoScanHeader.getBaseDir(),
                protoScanHeader.getWithStringSearch(),
                protoScanHeader.getWithSnippetMatching(),
                protoScanHeader.hasRetainUnmatchedFiles() ? protoScanHeader.getRetainUnmatchedFiles() : null,
                protoScanHeader.hasFileSystemSizeInBytes() ? protoScanHeader.getFileSystemSizeInBytes() : null);

        header.setVersion(version);

        return header;
    }

    public static BdioFileNode toBdioFileNode(ProtoFileNode protoFileNode) {
        Map<String, String> signatures = new HashMap<>();
        for (Map.Entry<Integer, String> entry : protoFileNode.getSignaturesMap().entrySet()) {
            String signatureType = SIGNATURE_TYPES.get(entry.getKey());
            if (signatureType == null) {
                throw new BdioValidationException("Unknown signature type: " + entry.getKey());
            }
            signatures.put(signatureType, entry.getValue());
        }

        return new BdioFileNode(
                protoFileNode.getId(),
                protoFileNode.getParentId(),
                protoFileNode.getName(),
                protoFileNode.getFileSystemType(),
                protoFileNode.hasShallowDirectoryCount() ? protoFileNode.getShallowDirectoryCount() : null,
                protoFileNode.hasDeepDirectoryCount() ? protoFileNode.getDeepDirectoryCount() : null,
                protoFileNode.hasDeepFileCount() ? protoFileNode.getDeepFileCount() : null,
                protoFileNode.hasDistanceFromRoot() ? protoFileNode.getDistanceFromRoot() : null,
                protoFileNode.hasDistanceFromInnerRoot() ? protoFileNode.getDistanceFromInnerRoot() : null,
                protoFileNode.getPath(),
                protoFileNode.hasArchiveContext() ? protoFileNode.getArchiveContext() : null,
                protoFileNode.getUri(),
                protoFileNode.getSize(),
                signatures);
    }

    public static BdioDependencyNode toBdioDependencyNode(ProtoDependencyNode protoDependencyNode) {
        return new BdioDependencyNode(
                protoDependencyNode.getComponentId(),
                protoDependencyNode.hasEvidenceId() ? protoDependencyNode.getEvidenceId() : null,
                protoDependencyNode.hasContainerLayer() ? protoDependencyNode.getContainerLayer() : null,
                protoDependencyNode.hasWhiteoutLayer() ? protoDependencyNode.getWhiteoutLayer() : null,
                protoDependencyNode.hasDescriptionId() ? protoDependencyNode.getDescriptionId() : null,
                protoDependencyNode.getMatchTypesList().stream().map(mt -> mt.name()).collect(Collectors.toList()));
    }

    public static BdioComponentNode toBdioComponentNode(ProtoComponentNode protoComponentNode) {
        return new BdioComponentNode(
                protoComponentNode.getId(),
                protoComponentNode.getNamespace(),
                protoComponentNode.getIdentifier(),
                protoComponentNode.hasDescriptionId() ? protoComponentNode.getDescriptionId() : null);
    }

    public static BdioAnnotationNode toBdioAnnotationNode(ProtoAnnotationNode protoAnnotationNode) {
        return new BdioAnnotationNode(
                protoAnnotationNode.getId(),
                protoAnnotationNode.getComment());
    }

    public static BdioContainerNode toBdioContainerNode(ProtoContainerNode protoContainerNode) {
        return new BdioContainerNode(
                protoContainerNode.getId(),
                protoContainerNode.getImage(),
                protoContainerNode.getArchitecture(),
                protoContainerNode.getRepoTagsList(),
                protoContainerNode.getOs(),
                protoContainerNode.hasCreatedAt() ? toInstant(protoContainerNode.getCreatedAt()) : null,
                protoContainerNode.getConfig(),
                protoContainerNode.getLayersList(),
                protoContainerNode.getImagePathsList());
    }

    public static BdioContainerLayerNode toBdioContainerLayerNode(ProtoContainerLayerNode protoContainerLayerNode) {
        return new BdioContainerLayerNode(
                protoContainerLayerNode.getId(),
                protoContainerLayerNode.getLayer(),
                protoContainerLayerNode.getSize(),
                protoContainerLayerNode.hasCommand() ? protoContainerLayerNode.getCommand() : null,
                protoContainerLayerNode.hasCreatedAt() ? toInstant(protoContainerLayerNode.getCreatedAt()) : null,
                protoContainerLayerNode.hasComment() ? protoContainerLayerNode.getComment() : null);
    }

    public static BdioBdbaFileNode toBdioBdbaFileNode(ProtoBdbaFileNode protoBdbaFileNode) {
        return new BdioBdbaFileNode(
                protoBdbaFileNode.getId(),
                protoBdbaFileNode.getUri(),
                protoBdbaFileNode.getSize(),
                toInstant(protoBdbaFileNode.getLastModifiedDateTime()),
                protoBdbaFileNode.hasFileSystemType() ? protoBdbaFileNode.getFileSystemType() : null,
                protoBdbaFileNode.getSignaturesMap());
    }

    public static BdioChunk toBdioChunk(ProtoChunk protoChunk) {

        Set<BdioFileNode> fileNodes = protoChunk.getFileNodes().stream().map(fn -> toBdioFileNode(fn))
                .collect(Collectors.toSet());

        Set<BdioDependencyNode> dependencyNodes = protoChunk.getDependencyNodes().stream().map(dn -> toBdioDependencyNode(dn))
                .collect(Collectors.toSet());

        Set<BdioComponentNode> componentNodes = protoChunk.getComponentNodes().stream().map(cn -> toBdioComponentNode(cn))
                .collect(Collectors.toSet());

        Set<BdioAnnotationNode> annotationNodes = protoChunk.getAnnotationNodes().stream().map(an -> toBdioAnnotationNode(an))
                .collect(Collectors.toSet());

        Set<BdioContainerNode> containerNodes = protoChunk.getContainerNodes().stream().map(cn -> toBdioContainerNode(cn))
                .collect(Collectors.toSet());

        Set<BdioContainerLayerNode> containerLayerNodes = protoChunk.getContainerLayerNodes().stream().map(cln -> toBdioContainerLayerNode(cln))
                .collect(Collectors.toSet());

        Set<BdioBdbaFileNode> bdbaFileNodes = protoChunk.getBdbaFileNodes().stream().map(cln -> toBdioBdbaFileNode(cln))
                .collect(Collectors.toSet());

        return new BdioChunk(fileNodes, dependencyNodes, componentNodes, annotationNodes, containerNodes,
                containerLayerNodes, bdbaFileNodes);
    }

    private static UUID toUUID(String s) {
        if (s != null) {
            return UUID.fromString(s);
        }

        return null;
    }

    private static Instant toInstant(Timestamp timestamp) {
        if (timestamp != null) {
            return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        }

        return null;
    }

    private static Timestamp toTimestamp(Instant date) {
        if (date != null) {
            return Timestamp.newBuilder().setSeconds(date.getEpochSecond()).setNanos(date.getNano()).build();
        }

        return null;
    }

    private static <T> void callIfParamNonNull(Consumer<T> consumer, @Nullable T param) {
        if (param != null) {
            consumer.accept(param);
        }
    }

}
