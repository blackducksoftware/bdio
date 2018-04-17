/*
 * Copyright 2016 Black Duck Software, Inc.
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

import static com.blackducksoftware.common.test.JsonSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static org.hamcrest.CoreMatchers.instanceOf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.NodeDoesNotExistException;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.model.Project;
import com.blackducksoftware.bdio2.test.BdioTest;
import com.blackducksoftware.common.value.ContentType;
import com.blackducksoftware.common.value.Digest;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.Lists;

/**
 * Tests for the {@link BlackDuckIoReader}.
 *
 * @author jgustie
 */
public class BlackDuckIoReaderTest extends BaseTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    public BlackDuckIoReaderTest(Configuration configuration) {
        super(configuration);
    }

    @Test
    public void readEmpty() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);
        new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id)).readGraph(inputStream);

        GraphTraversal<Vertex, Vertex> namedGraphs = graph.traversal().V().hasLabel(TT.Metadata);
        assertThat(namedGraphs.hasNext()).isTrue();
    }

    @Test
    public void readLogicallyEmpty() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("{}".getBytes(UTF_8));
        new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id)).readGraph(inputStream);

        GraphTraversal<Vertex, Vertex> namedGraphs = graph.traversal().V().hasLabel(TT.Metadata);
        assertThat(namedGraphs.hasNext()).isTrue();
    }

    @Test
    public void readMetadata() throws Exception {
        ZonedDateTime creationDateTime = ZonedDateTime.now();
        BdioMetadata metadata = BdioMetadata.createRandomUUID().creationDateTime(creationDateTime);

        InputStream inputStream = BdioTest.zipJsonBytes(metadata.asNamedGraph());
        new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id)).readGraph(inputStream);

        GraphTraversal<Vertex, Vertex> namedGraphs = graph.traversal().V().hasLabel(TT.Metadata);
        assertThat(namedGraphs.hasNext()).isTrue();
        Vertex namedGraph = namedGraphs.next();
        assertThat(namedGraphs.hasNext()).isFalse();

        VertexProperty<String> idProperty = namedGraph.property(TT.id);
        assertThat(idProperty.isPresent()).isTrue();
        assertThat(idProperty.value()).isEqualTo(metadata.id());

        VertexProperty<ZonedDateTime> creationProperty = namedGraph.property(Bdio.DataProperty.creationDateTime.name());
        assertThat(creationProperty.isPresent()).isTrue();
        assertThat(creationProperty.value()).isEqualTo(creationDateTime);
    }

    @Test
    public void readMetadataPartition() throws Exception {
        BdioMetadata metadata = BdioMetadata.createRandomUUID();

        InputStream inputStream = BdioTest.zipJsonBytes(metadata.asNamedGraph());
        new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id)).withStrategies(testPartition("abc")).readGraph(inputStream);

        Vertex namedGraph = graph.traversal().V().hasLabel(TT.Metadata).next();
        VertexProperty<String> partition = namedGraph.property(TT.partition);
        assertThat(partition.isPresent()).isTrue();
        assertThat(partition.value()).isEqualTo("abc");
    }

    @Test
    public void readFile() throws Exception {
        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        File fileModel = new File(BdioObject.randomId());
        fileModel.byteCount(101L);
        fileModel.fingerprint(singleton(Digest.of("sha1", "2d05a5f70ffb6fbf6fcbf65bb6f4cd48a8b2592a")));
        fileModel.fingerprint(singleton(Digest.of("md5", "b26af8f84049e82f6a4805d68b0f178d")));

        InputStream inputStream = BdioTest.zipJsonBytes(metadata.asNamedGraph(Lists.newArrayList(fileModel)));
        new BlackDuckIoCore(graph).readGraph(inputStream);

        GraphTraversal<Vertex, Vertex> files = graph.traversal().V().hasLabel(Bdio.Class.File.name());
        assertThat(files.hasNext()).isTrue();
        Vertex file = files.next();
        assertThat(files.hasNext()).isFalse();

        // NOTE: We cannot ensure that the long will make it through compaction
        VertexProperty<Number> byteCountProperty = file.property(Bdio.DataProperty.byteCount.name());
        assertThat(byteCountProperty.isPresent()).isTrue();
        assertThat(byteCountProperty.value().longValue()).isEqualTo(101L);

        // NOTE: Multivalued properties will be returned as arrays by Sqlg
        if (graph instanceof SqlgGraph) {
            VertexProperty<String[]> fingerprintProperty = file.property(Bdio.DataProperty.fingerprint.name());
            assertThat(fingerprintProperty.isPresent()).isTrue();
            assertThat(fingerprintProperty.value()).asList().containsExactly(
                    "sha1:2d05a5f70ffb6fbf6fcbf65bb6f4cd48a8b2592a",
                    "md5:b26af8f84049e82f6a4805d68b0f178d");
        } else {
            VertexProperty<List<Digest>> fingerprintProperty = file.property(Bdio.DataProperty.fingerprint.name());
            assertThat(fingerprintProperty.isPresent()).isTrue();
            assertThat(fingerprintProperty.value()).containsExactly(
                    Digest.of("sha1", "2d05a5f70ffb6fbf6fcbf65bb6f4cd48a8b2592a"),
                    Digest.of("md5", "b26af8f84049e82f6a4805d68b0f178d"));
        }
    }

    @Test
    public void readFileSingleFingerprint() throws Exception {
        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        File fileModel = new File(BdioObject.randomId());
        fileModel.fingerprint(singleton(Digest.of("sha1", "2d05a5f70ffb6fbf6fcbf65bb6f4cd48a8b2592a")));

        InputStream inputStream = BdioTest.zipJsonBytes(metadata.asNamedGraph(Lists.newArrayList(fileModel)));
        new BlackDuckIoCore(graph).readGraph(inputStream);

        GraphTraversal<Vertex, Vertex> files = graph.traversal().V().hasLabel(Bdio.Class.File.name());
        assertThat(files.hasNext()).isTrue();
        Vertex file = files.next();
        assertThat(files.hasNext()).isFalse();

        if (graph instanceof SqlgGraph) {
            VertexProperty<String[]> fingerprintProperty = file.property(Bdio.DataProperty.fingerprint.name());
            assertThat(fingerprintProperty.isPresent()).isTrue();
            assertThat(fingerprintProperty.value()).asList().containsExactly(
                    "sha1:2d05a5f70ffb6fbf6fcbf65bb6f4cd48a8b2592a");
        } else {
            VertexProperty<List<Digest>> fingerprintProperty = file.property(Bdio.DataProperty.fingerprint.name());
            assertThat(fingerprintProperty.isPresent()).isTrue();
            assertThat(fingerprintProperty.value()).containsExactly(
                    Digest.of("sha1", "2d05a5f70ffb6fbf6fcbf65bb6f4cd48a8b2592a"));
        }
    }

    @Test
    public void readBaseFile() throws Exception {
        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        Project projectModel = new Project(BdioObject.randomId());
        File fileModel = new File(BdioObject.randomId());
        fileModel.byteCount(103L);
        projectModel.base(fileModel);

        InputStream inputStream = BdioTest.zipJsonBytes(metadata.asNamedGraph(Lists.newArrayList(projectModel, fileModel)));
        new BlackDuckIoCore(graph).readGraph(inputStream);

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
        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        File fileModel1 = new File(BdioObject.randomId());
        File fileModel2 = new File(fileModel1.id());
        fileModel1.byteCount(103L);
        fileModel2.contentType(ContentType.parse("text/plain"));

        InputStream inputStream = BdioTest.zipJsonBytes(
                metadata.asNamedGraph(Lists.newArrayList(fileModel1)),
                metadata.asNamedGraph(Lists.newArrayList(fileModel2), JsonLdConsts.ID));
        new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id)).readGraph(inputStream);

        GraphTraversal<Vertex, Vertex> files = graph.traversal().V().hasLabel(Bdio.Class.File.name());
        assertThat(files.hasNext()).isTrue();
        Vertex file = files.next();
        assertThat(files.hasNext()).isFalse();

        assertThat(file.property(Bdio.DataProperty.byteCount.name()).isPresent()).isTrue();
        assertThat(file.property(Bdio.DataProperty.contentType.name()).isPresent()).isTrue();
    }

    @Test
    public void splitNodeEdges() throws Exception {
        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        Project projectModel1 = new Project(BdioObject.randomId());
        Project projectModel2 = new Project(projectModel1.id());
        File fileModel = new File(BdioObject.randomId());
        projectModel2.base(fileModel);

        InputStream inputStream = BdioTest.zipJsonBytes(
                metadata.asNamedGraph(Lists.newArrayList(projectModel1)),
                metadata.asNamedGraph(Lists.newArrayList(projectModel2, fileModel), JsonLdConsts.ID));
        new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id)).readGraph(inputStream);

        GraphTraversalSource g = graph.traversal();
        assertThat(g.V().hasLabel(Bdio.Class.Project.name()).count().next()).isEqualTo(1);
        assertThat(g.V().hasLabel(Bdio.Class.Project.name()).out(Bdio.ObjectProperty.base.name()).values(TT.id).tryNext()).hasValue(fileModel.id());
    }

    @Test
    public void readUnknownCustomProperty() throws Exception {
        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        Project projectModel = new Project(BdioObject.randomId());
        projectModel.put("http://example.com/gus", "testing");

        InputStream inputStream = BdioTest.zipJsonBytes(metadata.asNamedGraph(Lists.newArrayList(projectModel)));
        new BlackDuckIoCore(graph).withTokens(testTokens(TT.Metadata, TT.id, TT.unknown)).readGraph(inputStream);

        Optional<Object> unknown = graph.traversal().V().hasLabel(Bdio.Class.Project.name()).values(TT.unknown).tryNext();
        assertThat(unknown).isPresent();
        assertThat(unknown.get()).isInstanceOf(JsonNode.class);
        assertThat((JsonNode) unknown.get()).containsPair("http://example.com/gus", "testing");
    }

    @Test
    public void readCustomProperty() throws Exception {
        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        Project projectModel = new Project(BdioObject.randomId());
        projectModel.put("http://example.com/gus", "testing");

        BlackDuckIoTokens tokens = DefaultBlackDuckIoTokens.build()
                .unknownKey(TT.unknown)
                .addDataProperty("foobar", "http://example.com/gus")
                .create();
        InputStream inputStream = BdioTest.zipJsonBytes(metadata.asNamedGraph(Lists.newArrayList(projectModel)));
        new BlackDuckIoCore(graph).withTokens(tokens).readGraph(inputStream);

        Optional<Object> unknown = graph.traversal().V().hasLabel(Bdio.Class.Project.name()).values("_unknown").tryNext();
        assertThat(unknown).isEmpty();

        Optional<Object> foobar = graph.traversal().V().hasLabel(Bdio.Class.Project.name()).values("foobar").tryNext();
        assertThat(foobar).hasValue("testing");
    }

    @Test
    public void readNodeDoesNotExist() throws Exception {
        thrown.expect(instanceOf(BlackDuckIoReadGraphException.class));
        thrown.expectCause(instanceOf(NodeDoesNotExistException.class));

        BdioMetadata metadata = BdioMetadata.createRandomUUID();
        Project projectModel = new Project(BdioObject.randomId());
        projectModel.base(new File(BdioObject.randomId()));

        InputStream inputStream = BdioTest.zipJsonBytes(metadata.asNamedGraph(Lists.newArrayList(projectModel)));
        new BlackDuckIoCore(graph).readGraph(inputStream);
    }

}
