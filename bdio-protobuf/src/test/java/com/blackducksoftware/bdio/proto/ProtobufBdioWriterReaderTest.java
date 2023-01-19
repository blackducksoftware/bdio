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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang.RandomStringUtils;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import com.blackducksoftware.bdio.proto.v1.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.v1.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageNode;
import com.blackducksoftware.bdio.proto.v1.ProtoImageLayerNode;
import com.blackducksoftware.bdio.proto.v1.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.v1.ProtoFileNode;
import com.blackducksoftware.bdio.proto.v1.ProtoScanHeader;
import com.blackducksoftware.bdio.proto.v1.ScanType;

import com.google.common.collect.ImmutableList;
import java.util.concurrent.ThreadLocalRandom;

import com.blackducksoftware.bdio.proto.ProtobufBdioReader;
import com.blackducksoftware.bdio.proto.ProtobufBdioWriter;
import com.blackducksoftware.bdio.proto.BdioChunk;

import com.google.protobuf.Timestamp;


public class ProtobufBdioWriterReaderTest {

    public static final String HEADER_ID = UUID.randomUUID().toString();

    @Test
    public void testReadHeader() throws Exception {
        String filePath = "src/test/resources/scan_data/binaryData.zip";

        try {
            createBdioFile(filePath);
            ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(filePath));
            ProtoScanHeader header = ProtobufBdioReader.readHeaderFromScanFile(zipInputStream);

            assertThat(header).isNotNull();
            assertThat(HEADER_ID).isEqualTo(header.getId());
        } finally {
            Files.delete(Paths.get(filePath));
        }
    }

    @Test
    public void testReadBdbaBdioChunk() throws IOException {
        String filePath = "src/test/resources/scan_data/binaryData.zip";
        try {
            createBdioFile(filePath);
            ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(filePath));

            // skip header and go to first entry
            zipInputStream.getNextEntry();
            zipInputStream.getNextEntry();

            // read first entry
            BdioChunk chunk = ProtobufBdioReader.readBdbaBdioChunk(zipInputStream);

            assertThat(chunk.getComponentNodes()).hasSize(40);
            assertThat(chunk.getFileNodes()).hasSize(40);
            assertThat(chunk.getAnnotationNodes()).hasSize(40);
            assertThat(chunk.getImageLayerNodes()).hasSize(20);
            assertThat(chunk.getImageNodes()).hasSize(1);
        } finally {
            Files.delete(Paths.get(filePath));
        }
    }

    public void createBdioFile(String filePath) throws IOException {
        FileOutputStream outStream = new FileOutputStream(filePath);
        ZipOutputStream zipOutputStream = new ZipOutputStream(outStream);

        ProtobufBdioWriter writer = new ProtobufBdioWriter(zipOutputStream);
        ProtoScanHeader header = createHeader();
        writer.writeHeader(header);

        BdbaScanData bdioData = createBdbaScanData(40, 20);
        writer.writeBdbaScanNodes(bdioData.getAll());
        writer.close();
    }

    public ProtoScanHeader createHeader() {
        Timestamp creationTime = Timestamp.newBuilder()
                .setSeconds(1000L)
                .setNanos(0)
                .build();

        ProtoScanHeader.Builder builder = ProtoScanHeader.newBuilder()
                .setId(HEADER_ID)
                .setScanType(ScanType.BINARY)
                .setCodeLocationName("code location name")
                .setPublisherName("publisher name")
                .setPublisherVersion("publisher version")
                .setPublisherComment("publisher comment")
                .setCreator("creator")
                .setCreationTime(creationTime)
                .setBaseDir("/baseDir")
                .setWithStringSearch(true)
                .setWithSnippetMatching(true);

        return builder.build();
    }

    public ImageAndLayersData createContainerImageNode(int layersCount) {
        ProtoImageNode.Builder builder = ProtoImageNode.newBuilder();

        builder.setId(RandomStringUtils.randomAlphabetic(40));
        builder.setImageId(RandomStringUtils.randomAlphabetic(50));
        builder.setArchitecture(RandomStringUtils.randomAlphabetic(20));
        builder.setOs(RandomStringUtils.randomAlphabetic(20));
        builder.addRepoTag("repoTag1").addRepoTag("repoTag2").addRepoTag("repoTag3");
        builder.setCreatedAt(com.google.protobuf.Timestamp.getDefaultInstance());
        builder.setConfig(RandomStringUtils.randomAlphabetic(100));

        List<String> layerIds = new ArrayList<>();
        List<ProtoImageLayerNode> containerLayers = new ArrayList<>();
        for (int i = 0; i < layersCount; i++) {
            ProtoImageLayerNode.Builder builder1 = ProtoImageLayerNode.newBuilder();

            String layerInternalId = UUID.randomUUID().toString();
            layerIds.add(layerInternalId);
            containerLayers.add(
                    builder1.setId(layerInternalId).setLayerId(RandomStringUtils.randomAlphabetic(50)).setSize(randomInt(1000000, 1000000000))
                            .setCommand(RandomStringUtils.randomAlphabetic(200)).setCreatedAt(com.google.protobuf.Timestamp.getDefaultInstance())
                            .setComment(RandomStringUtils.randomAlphabetic(100)).build());
        }

        layerIds.stream().forEach(l -> builder.addLayer(l));

        ProtoImageNode containerImage = builder.build();

        return new ImageAndLayersData(containerImage, containerLayers);
    }

    private BdbaScanData createBdbaScanData(int componentCount, int layerCount) {

        ImageAndLayersData imageAndLayersData = createContainerImageNode(layerCount);
        List<String> layerIds = imageAndLayersData.getLayers().stream().map(l -> l.getId()).collect(Collectors.toList());

        List<ProtoComponentNode> components = new ArrayList<>();
        List<ProtoFileNode> files = new ArrayList<>();
        List<ProtoAnnotationNode> annotations = new ArrayList<>();
        List<ProtoDependencyNode> dependencies = new ArrayList<>();

        for (int i = 0; i < componentCount; i++) {
            ProtoComponentNode component = createComponentNode();
            ProtoFileNode file = createFileNode();
            ProtoAnnotationNode annotation = createAnnotationNode();
            components.add(component);
            files.add(file);
            annotations.add(annotation);

            ProtoDependencyNode.Builder builder2 = ProtoDependencyNode.newBuilder();
            builder2.setDependsOn(component.getId());
            builder2.setEvidence(file.getId());

            int cl = randomInt(0, layerIds.size());
            builder2.setContainerLayer(layerIds.get(cl));

            int wl = randomInt(0, layerIds.size());
            builder2.setWhiteoutLayer(layerIds.get(wl));

            builder2.setDescription(RandomStringUtils.randomAlphabetic(200));
            builder2.setMatchType(com.blackducksoftware.bdio.proto.v1.MatchType.SOME_MATCH_TYPE);

            dependencies.add(builder2.build());
        }

        return new BdbaScanData(components, files, annotations, dependencies, imageAndLayersData.getImage(), imageAndLayersData.getLayers());
    }

    private ProtoComponentNode createComponentNode() {
        ProtoComponentNode.Builder builder = ProtoComponentNode.newBuilder();

        builder.setId(randomLong(0L, 10000L));
        builder.setNamespace(RandomStringUtils.randomAlphabetic(50));
        builder.setIdentifier(RandomStringUtils.randomAlphabetic(50));
        builder.setDescription(RandomStringUtils.randomAlphabetic(200));

        return builder.build();
    }

    private ProtoFileNode createFileNode() {
        ProtoFileNode.Builder builder = ProtoFileNode.newBuilder();

        builder.setId(randomLong(0L, 10000L));
        builder.setUri(RandomStringUtils.randomAlphabetic(200));

        return builder.build();
    }

    private int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    private long randomLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max);
    }

    private ProtoAnnotationNode createAnnotationNode() {
        ProtoAnnotationNode.Builder builder = ProtoAnnotationNode.newBuilder();

        builder.setId(UUID.randomUUID().toString());
        builder.setComment(RandomStringUtils.randomAlphabetic(200));

        return builder.build();
    }

    private static class ImageAndLayersData {
        private ProtoImageNode image;

        private List<ProtoImageLayerNode> layers;

        public ImageAndLayersData(ProtoImageNode image, List<ProtoImageLayerNode> layers) {
            this.image = image;
            this.layers = layers;
        }

        public ProtoImageNode getImage() {
            return image;
        }

        public List<ProtoImageLayerNode> getLayers() {
            return layers;
        }
    }

    private static class BdbaScanData {

        private List<ProtoComponentNode> components;

        private List<ProtoFileNode> files;

        private List<ProtoAnnotationNode> annotations;

        private List<ProtoDependencyNode> dependencies;

        private ProtoImageNode image;

        private List<ProtoImageLayerNode> layers;

        public BdbaScanData(List<ProtoComponentNode> components, List<ProtoFileNode> files, List<ProtoAnnotationNode> annotations,
                List<ProtoDependencyNode> dependencies, ProtoImageNode image, List<ProtoImageLayerNode> layers) {
            this.components = components;
            this.files = files;
            this.annotations = annotations;
            this.dependencies = dependencies;
            this.image = image;
            this.layers = layers;
        }

        public List<ProtoComponentNode> getComponents() {
            return components;
        }

        public List<ProtoFileNode> getFiles() {
            return files;
        }

        public List<ProtoAnnotationNode> getAnnotations() {
            return annotations;
        }

        public List<ProtoDependencyNode> getDependencies() {
            return dependencies;
        }

        public List<com.google.protobuf.GeneratedMessageV3> getAll() {
            List<com.google.protobuf.GeneratedMessageV3> result = new ArrayList<>();
            result.addAll(dependencies);
            result.addAll(components);
            result.addAll(files);
            result.addAll(annotations);
            result.add(image);
            result.addAll(layers);

            return result;
        }

    }


}
