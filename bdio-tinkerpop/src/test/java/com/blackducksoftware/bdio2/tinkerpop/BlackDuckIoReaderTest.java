/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio2.tinkerpop;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.Test;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.model.Project;
import com.blackducksoftware.bdio2.test.BdioTest;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.Lists;

public class BlackDuckIoReaderTest {

    @Test
    public void readMetadata() throws Exception {
        Instant creation = Instant.now();
        BdioMetadata metadata = new BdioMetadata().id("urn:uuid:" + UUID.randomUUID()).creation(creation);
        TinkerGraph graph = TinkerGraph.open();

        BlackDuckIoReader.build().create().readGraph(BdioTest.zipJsonBytes(metadata.asNamedGraph()), graph);

        GraphTraversal<Vertex, Vertex> namedGraphs = graph.traversal().V().hasLabel(Tokens.NamedGraph);
        assertThat(namedGraphs.hasNext()).isTrue();
        Vertex namedGraph = namedGraphs.next();
        assertThat(namedGraphs.hasNext()).isFalse();

        VertexProperty<String> idProperty = namedGraph.property(Tokens.id);
        assertThat(idProperty.isPresent()).isTrue();
        assertThat(idProperty.value()).isEqualTo(metadata.id());

        VertexProperty<Instant> creationProperty = namedGraph.property(Bdio.DataProperty.creation.name());
        assertThat(creationProperty.isPresent()).isTrue();
        assertThat(creationProperty.value()).isEqualTo(creation);
    }

    @Test
    public void readFile() throws Exception {
        BdioMetadata metadata = new BdioMetadata().id("urn:uuid:" + UUID.randomUUID());
        File fileModel = new File("urn:uuid:" + UUID.randomUUID());
        fileModel.byteCount(101L);
        TinkerGraph graph = TinkerGraph.open();

        BlackDuckIoReader.build().create().readGraph(BdioTest.zipJsonBytes(metadata.asNamedGraph(Lists.newArrayList(fileModel))), graph);

        GraphTraversal<Vertex, Vertex> files = graph.traversal().V().hasLabel(Bdio.Class.File.name());
        assertThat(files.hasNext()).isTrue();
        Vertex file = files.next();
        assertThat(files.hasNext()).isFalse();

        // NOTE: We cannot ensure that the long will make it through compaction
        VertexProperty<Number> byteCountProperty = file.property(Bdio.DataProperty.byteCount.name());
        assertThat(byteCountProperty.isPresent()).isTrue();
        assertThat(byteCountProperty.value().longValue()).isEqualTo(101L);
    }

    @Test
    public void readBaseFile() throws Exception {
        BdioMetadata metadata = new BdioMetadata().id("urn:uuid:" + UUID.randomUUID());
        Project projectModel = new Project("urn:uuid:" + UUID.randomUUID());
        File fileModel = new File("urn:uuid:" + UUID.randomUUID());
        fileModel.byteCount(103L);
        projectModel.base(fileModel);

        TinkerGraph graph = TinkerGraph.open();

        BlackDuckIoReader.build().create().readGraph(BdioTest.zipJsonBytes(metadata.asNamedGraph(Lists.newArrayList(projectModel, fileModel))), graph);

        Optional<Number> byteCount = graph.traversal().V()
                .hasLabel(Bdio.Class.Project.name())
                .out(Bdio.ObjectProperty.base.name())
                .tryNext()
                .map(vertex -> vertex.value(Bdio.DataProperty.byteCount.name()));
        assertThat(byteCount.isPresent()).isTrue();
        assertThat(byteCount.get().longValue()).isEqualTo(103L);
    }

    @Test
    public void splitNode() throws Exception {
        BdioMetadata metadata = new BdioMetadata().id("urn:uuid:" + UUID.randomUUID());
        File fileModel1 = new File("urn:uuid:" + UUID.randomUUID());
        File fileModel2 = new File(fileModel1.id());
        fileModel1.byteCount(103L);
        fileModel2.contentType("text/plain");

        TinkerGraph graph = TinkerGraph.open();

        BlackDuckIoReader.build().create().readGraph(BdioTest.zipJsonBytes(
                metadata.asNamedGraph(Lists.newArrayList(fileModel1)),
                metadata.asNamedGraph(Lists.newArrayList(fileModel2), JsonLdConsts.ID)), graph);

        GraphTraversal<Vertex, Vertex> files = graph.traversal().V().hasLabel(Bdio.Class.File.name());
        assertThat(files.hasNext()).isTrue();
        Vertex file = files.next();
        assertThat(files.hasNext()).isFalse();

        assertThat(file.property(Bdio.DataProperty.byteCount.name()).isPresent()).isTrue();
        assertThat(file.property(Bdio.DataProperty.contentType.name()).isPresent()).isTrue();
    }

}
