/*
 * Copyright 2017 Black Duck Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blackducksoftware.bdio2.tinkerpop;

import static com.blackducksoftware.common.test.JsonSubject.assertThatJson;
import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Test;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.test.BdioTest;
import com.blackducksoftware.common.io.HeapOutputStream;

public class BlackDuckIoWriterTest extends BaseTest {

    public BlackDuckIoWriterTest(Configuration configuration) {
        super(configuration);
    }

    /**
     * Create a graph that only contains a metadata vertex and verify it writes out correctly.
     */
    @Test
    public void writeMetadata() throws Exception {
        String metadataId = "urn:uuid:" + UUID.randomUUID();
        Instant creation = Instant.now();
        graph.addVertex(
                T.label, TT.Metadata,
                TT.id, metadataId,
                Bdio.DataProperty.creation.name(), creation.toString());

        HeapOutputStream buffer = new HeapOutputStream();
        graph.io(BlackDuckIo.build().onConfig(storeMetadataAndIds()))
                .writeGraph(buffer);

        List<String> entries = BdioTest.zipEntries(buffer.getInputStream());
        assertThat(entries).hasSize(1);

        assertThatJson(entries.get(0)).at("/@id").isEqualTo(metadataId);
        assertThatJson(entries.get(0)).at(Bdio.DataProperty.creation, 0, "@value").isEqualTo(creation.toString());
        assertThatJson(entries.get(0)).arrayAt("/@graph").hasSize(0);
    }

    /**
     * Configure the writer to not include metadata and verify metadata just ends up as a normal node.
     */
    @Test
    public void writeNoMetadata() throws Exception {
        graph.addVertex(T.label, TT.Metadata);

        HeapOutputStream buffer = new HeapOutputStream();
        graph.io(BlackDuckIo.build()).writeGraph(buffer);

        List<String> entries = BdioTest.zipEntries(buffer.getInputStream());
        assertThat(entries).hasSize(2);

        assertThatJson(entries.get(0)).hasLength(2);
        assertThatJson(entries.get(0)).containsName("@id");
        assertThatJson(entries.get(0)).arrayAt("/@graph").hasSize(0);
        assertThatJson(entries.get(1)).arrayAt("/@graph").hasSize(1);
        assertThatJson(entries.get(1)).at("/@graph/0").hasLength(2);
        assertThatJson(entries.get(1)).arrayAt("/@graph/0/@type").containsExactly(TT.Metadata);
    }

    /**
     * Create a graph without metadata and still configure the writer to include it.
     */
    @Test
    public void writeMissingMetadata() throws Exception {
        graph.addVertex(
                T.label, Bdio.Class.Project.name(),
                TT.id, "urn:uuid:" + UUID.randomUUID());

        HeapOutputStream buffer = new HeapOutputStream();
        graph.io(BlackDuckIo.build().onConfig(storeMetadataAndIds()))
                .writeGraph(buffer);

        List<String> entries = BdioTest.zipEntries(buffer.getInputStream());
        assertThat(entries).hasSize(2);

        assertThatJson(entries.get(0)).arrayAt("/@graph").hasSize(0);
        assertThatJson(entries.get(1)).arrayAt("/@graph").hasSize(1);
        assertThatJson(entries.get(1)).arrayAt("/@graph/0/@type").containsExactly(Bdio.Class.Project.toString());
    }

    /**
     * Create a graph with two connected vertices and verify they serialize correctly.
     */
    @Test
    public void writeRelationship() throws Exception {
        String metadataId = "urn:uuid:" + UUID.randomUUID();
        Instant creation = Instant.now();
        graph.addVertex(
                T.label, TT.Metadata,
                TT.id, metadataId,
                Bdio.DataProperty.creation.name(), creation.toString());

        String fileId = "urn:uuid:" + UUID.randomUUID();
        Vertex file = graph.addVertex(
                T.label, "File",
                TT.id, fileId);

        String projectId = "urn:uuid:" + UUID.randomUUID();
        graph.addVertex(
                T.label, "Project",
                TT.id, projectId)
                .addEdge("base", file);

        HeapOutputStream buffer = new HeapOutputStream();
        graph.io(BlackDuckIo.build().onConfig(storeMetadataAndIds()))
                .writeGraph(buffer);

        List<String> entries = BdioTest.zipEntries(buffer.getInputStream());
        assertThat(entries).hasSize(2);

        assertThatJson(entries.get(0)).at("/@id").isEqualTo(metadataId);
        assertThatJson(entries.get(0)).at(Bdio.DataProperty.creation, 0, "@value").isEqualTo(creation.toString());
        assertThatJson(entries.get(0)).arrayAt("/@graph").hasSize(0);

        assertThatJson(entries.get(1)).at("/@id").isEqualTo(metadataId);
        assertThatJson(entries.get(1)).arrayAt("/@graph").hasSize(2);

        int projectIndex = BdioTest.nodeIdentifiers(entries.get(1)).indexOf(projectId);
        assertThat(projectIndex).isAtLeast(0);
        assertThatJson(entries.get(1)).at("@graph", projectIndex, Bdio.ObjectProperty.base, 0, "@value").isEqualTo(fileId);
    }

}
