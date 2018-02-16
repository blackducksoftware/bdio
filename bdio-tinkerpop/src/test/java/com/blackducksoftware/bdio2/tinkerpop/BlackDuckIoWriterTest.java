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

import java.time.ZonedDateTime;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Test;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;
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
        String metadataId = BdioObject.randomId();
        ZonedDateTime creationDateTime = ZonedDateTime.now();
        graph.addVertex(
                T.label, TT.Metadata,
                TT.id, metadataId,
                Bdio.DataProperty.creationDateTime.name(), creationDateTime.toString());
        commit();

        HeapOutputStream outputStream = new HeapOutputStream();
        BlackDuckIoCore bdio = new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id));
        bdio.writeGraph(outputStream);

        List<String> entries = BdioTest.zipEntries(outputStream.getInputStream());
        assertThat(entries).hasSize(1);

        assertThatJson(entries.get(0)).at("/@id").isEqualTo(metadataId);
        assertThatJson(entries.get(0)).at(Bdio.DataProperty.creationDateTime, 0, "@value").isEqualTo(creationDateTime.toString());
        assertThatJson(entries.get(0)).arrayAt("/@graph").hasSize(0);
    }

    /**
     * Configure the writer to not include metadata and verify metadata just ends up as a normal node.
     */
    @Test
    public void writeNoMetadata() throws Exception {
        graph.addVertex(T.label, TT.Metadata);
        commit();

        HeapOutputStream outputStream = new HeapOutputStream();
        graph.io(BlackDuckIo.build()).writer().create().writeGraph(outputStream, graph);

        List<String> entries = BdioTest.zipEntries(outputStream.getInputStream());
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
                TT.id, BdioObject.randomId());
        commit();

        HeapOutputStream outputStream = new HeapOutputStream();
        BlackDuckIoCore bdio = new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id));
        bdio.writeGraph(outputStream);

        List<String> entries = BdioTest.zipEntries(outputStream.getInputStream());
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
        String metadataId = BdioObject.randomId();
        ZonedDateTime creationDateTime = ZonedDateTime.now();
        graph.addVertex(
                T.label, TT.Metadata,
                TT.id, metadataId,
                Bdio.DataProperty.creationDateTime.name(), creationDateTime.toString());

        String fileId = BdioObject.randomId();
        Vertex file = graph.addVertex(
                T.label, Bdio.Class.File.name(),
                TT.id, fileId);

        String projectId = BdioObject.randomId();
        graph.addVertex(
                T.label, Bdio.Class.Project.name(),
                TT.id, projectId)
                .addEdge(Bdio.ObjectProperty.base.name(), file);
        commit();

        HeapOutputStream outputStream = new HeapOutputStream();
        BlackDuckIoCore bdio = new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id));
        bdio.writeGraph(outputStream);

        List<String> entries = BdioTest.zipEntries(outputStream.getInputStream());
        assertThat(entries).hasSize(2);

        assertThatJson(entries.get(0)).at("/@id").isEqualTo(metadataId);
        assertThatJson(entries.get(0)).at(Bdio.DataProperty.creationDateTime, 0, "@value").isEqualTo(creationDateTime.toString());
        assertThatJson(entries.get(0)).arrayAt("/@graph").hasSize(0);

        assertThatJson(entries.get(1)).at("/@id").isEqualTo(metadataId);
        assertThatJson(entries.get(1)).arrayAt("/@graph").hasSize(2);

        int projectIndex = BdioTest.nodeIdentifiers(entries.get(1)).indexOf(projectId);
        assertThat(projectIndex).isAtLeast(0);
        assertThatJson(entries.get(1)).at("@graph", projectIndex, Bdio.ObjectProperty.base, 0, "@value").isEqualTo(fileId);
    }

    @Test
    public void writeCustomProperty() throws Exception {
        graph.addVertex(
                T.label, Bdio.Class.Project.name(),
                TT.id, BdioObject.randomId(),
                "foobar", "testing");
        commit();

        BlackDuckIoCore bdio = new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id));

        // First write it out without registering the custom field and verify it doesn't show up
        HeapOutputStream nonCustomBuffer = new HeapOutputStream();
        bdio.writeGraph(nonCustomBuffer);

        assertThatJson(BdioTest.zipEntries(nonCustomBuffer.getInputStream()).get(1))
                .at("/@graph/0").doesNotContainName("foobar");

        // Now write it out with the registered custom data property
        HeapOutputStream customBuffer = new HeapOutputStream();
        bdio.withTokens(BlackDuckIoContext.build().metadataLabel(TT.Metadata).identifierKey(TT.id)
                .addDataProperty("foobar", "http://example.com/gus").create());
        bdio.writeGraph(customBuffer);

        List<String> entries = BdioTest.zipEntries(customBuffer.getInputStream());
        assertThatJson(entries.get(1)).at("/@graph/0").containsName("http://example.com/gus");
        assertThatJson(entries.get(1)).at("/@graph/0").doesNotContainName("foobar");
    }

    @Test
    public void writeEmbeddedObject() throws Exception {
        Vertex component1 = graph.addVertex(
                T.label, Bdio.Class.Component.name(),
                TT.id, BdioObject.randomId());
        Vertex component2 = graph.addVertex(
                T.label, Bdio.Class.Component.name(),
                TT.id, BdioObject.randomId());
        Vertex dependency = graph.addVertex(
                T.label, Bdio.Class.Dependency.name());

        component2.addEdge(Bdio.ObjectProperty.dependency.name(), dependency);
        dependency.addEdge(Bdio.ObjectProperty.dependsOn.name(), component1);
        commit();

        HeapOutputStream outputStream = new HeapOutputStream();
        BlackDuckIoCore bdio = new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id));
        bdio.writeGraph(outputStream);

        List<String> entries = BdioTest.zipEntries(outputStream.getInputStream());

        assertThatJson(entries.get(1)).arrayAt("/@graph").hasSize(2); // Just the two components
        assertThatJson(entries.get(1)).at("@graph", 1, Bdio.ObjectProperty.dependency, 0, Bdio.ObjectProperty.dependsOn, 0, "@value")
                .isEqualTo(component1.value(TT.id));
    }

}
