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

import static com.google.common.truth.Truth.assertThat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;

import com.blackducksoftware.bdio.proto.api.BdioAnnotationNode;
import com.blackducksoftware.bdio.proto.api.BdioBdbaFileNode;
import com.blackducksoftware.bdio.proto.api.BdioChunk;
import com.blackducksoftware.bdio.proto.api.BdioComponentNode;
import com.blackducksoftware.bdio.proto.api.BdioContainerLayerNode;
import com.blackducksoftware.bdio.proto.api.BdioContainerNode;
import com.blackducksoftware.bdio.proto.api.BdioDependencyNode;
import com.blackducksoftware.bdio.proto.api.BdioFileNode;
import com.blackducksoftware.bdio.proto.api.BdioHeader;
import com.blackducksoftware.bdio.proto.api.IBdioNode;
import com.blackducksoftware.bdio.proto.domain.BdbaMatchType;
import com.blackducksoftware.bdio.proto.domain.ScanType;
import com.google.common.collect.ImmutableList;

public class ProtobufBdioWriterReaderTest {

    public static final int COMPONENTS_COUNT = 20;

    public static final int LAYERS_COUNT = 40;

    public static final String HEADER_ID = UUID.randomUUID().toString();

    @Test
    public void testReadHeader() throws Exception {
        String filePath = "src/test/resources/scan_data/binaryData.zip";

        try {
            createBdioFile(filePath);
            ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(filePath));
            BdioHeader header = ProtobufBdioReader.readHeaderFromBdioArchive(zipInputStream);

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
            BdioChunk chunk = ProtobufBdioReader.readBdioChunk(zipInputStream, true);

            assertThat(chunk.getComponentNodes()).hasSize(COMPONENTS_COUNT);
            assertThat(chunk.getBdbaFileNodes()).hasSize(COMPONENTS_COUNT);
            assertThat(chunk.getAnnotationNodes()).hasSize(COMPONENTS_COUNT);
            assertThat(chunk.getContainerLayerNodes()).hasSize(LAYERS_COUNT);
            assertThat(chunk.getContainerNodes()).hasSize(1);
        } finally {
            Files.delete(Paths.get(filePath));
        }
    }

    public void createBdioFile(String filePath) throws IOException {
        FileOutputStream outStream = new FileOutputStream(filePath);
        ZipOutputStream zipOutputStream = new ZipOutputStream(outStream);

        ProtobufBdioWriter writer = new ProtobufBdioWriter(zipOutputStream);
        BdioHeader header = createHeader();
        writer.writeHeader(header);

        BdbaScanData bdioData = createBdbaScanData(COMPONENTS_COUNT, LAYERS_COUNT);
        writer.writeBdioNodes(bdioData.getAll());
        writer.close();
    }

    public BdioHeader createHeader() {

        return new BdioHeader(
                HEADER_ID,
                ScanType.BINARY.toString(),
                "codeLocation name",
                "project name",
                "version name",
                "publisher name",
                "publisher version",
                "publisher comment",
                "creator",
                Instant.now(), null, null, null, null,
                Long.valueOf(1L), "/baseDir", true,
                true, null, null);
    }

    public ContainerAndLayersData createContainerImageNode(int layersCount) {
        List<String> layerIds = new ArrayList<>();
        List<BdioContainerLayerNode> containerLayers = new ArrayList<>();
        for (int i = 0; i < layersCount; i++) {
            String layerId = RandomStringUtils.randomAlphabetic(50);
            layerIds.add(layerId);
            containerLayers.add(new BdioContainerLayerNode(UUID.randomUUID().toString(), layerId,
                    randomLong(1000000L, 1000000000L), RandomStringUtils.randomAlphabetic(200), Instant.now(),
                    UUID.randomUUID().toString()));
        }

        List<String> repoTags = ImmutableList.of("repoTag1", "repoTag2", "repoTag3");
        List<String> imagePaths = ImmutableList.of("foo/bar/image.tar");
        BdioContainerNode bdioContainerNode = new BdioContainerNode(UUID.randomUUID().toString(),
                RandomStringUtils.randomAlphabetic(50),
                RandomStringUtils.randomAlphabetic(20),
                repoTags,
                RandomStringUtils.randomAlphabetic(20),
                Instant.now(),
                RandomStringUtils.randomAlphabetic(100),
                layerIds,
                imagePaths);

        return new ContainerAndLayersData(bdioContainerNode, containerLayers);
    }

    private BdbaScanData createBdbaScanData(int componentCount, int layerCount) {

        ContainerAndLayersData imageAndLayersData = createContainerImageNode(layerCount);
        List<String> layerIds = imageAndLayersData.getLayers().stream().map(l -> l.getLayer())
                .collect(Collectors.toList());

        List<BdioComponentNode> components = new ArrayList<>();
        List<BdioBdbaFileNode> files = new ArrayList<>();
        List<BdioAnnotationNode> annotations = new ArrayList<>();
        List<BdioDependencyNode> dependencies = new ArrayList<>();

        for (int i = 0; i < componentCount; i++) {
            BdioComponentNode component = createComponentNode();
            BdioBdbaFileNode file = createBdbaFileNode();
            BdioAnnotationNode annotation = createAnnotationNode();
            components.add(component);
            files.add(file);
            annotations.add(annotation);

            int cl = randomInt(0, layerIds.size());
            int wl = randomInt(0, layerIds.size());
            BdioDependencyNode dependency = new BdioDependencyNode(
                    component.getId(), file.getId(),
                    layerIds.get(cl), layerIds.get(wl),
                    annotation.getId(), ImmutableList.of(BdbaMatchType.CocoapodPackage.name()));

            dependencies.add(dependency);
        }

        return new BdbaScanData(components, files, annotations, dependencies, imageAndLayersData.getImage(),
                imageAndLayersData.getLayers());
    }

    private BdioComponentNode createComponentNode() {
        return new BdioComponentNode(
                UUID.randomUUID().toString(),
                "npmjs", "@sindresorhus/is/0.14.0",
                UUID.randomUUID().toString());
    }

    private BdioFileNode createFileNode() {
        return new BdioFileNode(randomLong(1, 1000L),
                randomLong(1, 1000L),
                RandomStringUtils.randomAlphabetic(20),
                "FILE",
                null, null, null, null, null, "path",
                null, RandomStringUtils.randomAlphabetic(200),
                randomLong(1, 100L), Collections.emptyMap());
    }

    private BdioBdbaFileNode createBdbaFileNode() {
        return new BdioBdbaFileNode(UUID.randomUUID().toString(),
                "RandomStringUtils.randomAlphabetic(20)",
                randomLong(1, 1000L),
                Instant.now(),
                "FILE",
                Collections.emptyMap());
    }

    private int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    private long randomLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max);
    }

    private BdioAnnotationNode createAnnotationNode() {
        return new BdioAnnotationNode(UUID.randomUUID().toString(), RandomStringUtils.randomAlphabetic(200));
    }

    private static class ContainerAndLayersData {
        private BdioContainerNode image;

        private List<BdioContainerLayerNode> layers;

        public ContainerAndLayersData(BdioContainerNode image, List<BdioContainerLayerNode> layers) {
            this.image = image;
            this.layers = layers;
        }

        public BdioContainerNode getImage() {
            return image;
        }

        public List<BdioContainerLayerNode> getLayers() {
            return layers;
        }
    }

    private static class BdbaScanData {

        private List<BdioComponentNode> components;

        private List<BdioBdbaFileNode> files;

        private List<BdioAnnotationNode> annotations;

        private List<BdioDependencyNode> dependencies;

        private BdioContainerNode image;

        private List<BdioContainerLayerNode> layers;

        public BdbaScanData(List<BdioComponentNode> components, List<BdioBdbaFileNode> files,
                List<BdioAnnotationNode> annotations, List<BdioDependencyNode> dependencies, BdioContainerNode image,
                List<BdioContainerLayerNode> layers) {
            this.components = components;
            this.files = files;
            this.annotations = annotations;
            this.dependencies = dependencies;
            this.image = image;
            this.layers = layers;
        }

        public List<IBdioNode> getAll() {
            List<IBdioNode> result = new ArrayList<>();
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
