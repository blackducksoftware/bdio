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

import static com.google.common.collect.Lists.asList;
import static java.util.Collections.emptyList;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.umlg.sqlg.sql.dialect.SqlDialect;
import org.umlg.sqlg.structure.SqlgDataSourceFactory.SqlgDataSource;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioContext.ActiveContext;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.test.GraphRunner;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.flyway.BdioCallback;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.flyway.SqlgFlywayExecutor;
import com.blackducksoftware.bdio2.tinkerpop.strategy.PropertyConstantStrategy;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

@RunWith(GraphRunner.class)
public abstract class BaseTest {

    /**
     * (T)est (T)okens.
     */
    public static final class TT {

        public static final String Metadata = "Metadata";

        public static final String root = "_root";

        public static final String id = "_id";

        public static final String unknown = "_unknown";

        public static final String partition = "_partition";

        public static final String implicit = "_implicit";

    }

    protected static BlackDuckIo.Builder testBdio(String... tokens) {
        Set<String> tokenSet = ImmutableSet.copyOf(tokens);
        BlackDuckIoOptions.Builder options = BlackDuckIoOptions.build();
        if (tokenSet.isEmpty() || tokenSet.contains(TT.Metadata)) {
            options.metadataLabel(TT.Metadata);
        }
        if (tokenSet.isEmpty() || tokenSet.contains(TT.root)) {
            options.rootLabel(TT.root);
        }
        if (tokenSet.isEmpty() || tokenSet.contains(TT.id)) {
            options.identifierKey(TT.id);
        }
        if (tokenSet.isEmpty() || tokenSet.contains(TT.unknown)) {
            options.unknownKey(TT.unknown);
        }
        return BlackDuckIo.build().options(options.create());
    }

    /**
     * Creates a constant.
     */
    protected static PropertyConstantStrategy testImplicitConstant() {
        return PropertyConstantStrategy.build()
                .addProperty(TT.implicit, Boolean.TRUE)
                .create();
    }

    /**
     * Creates a partition with the specified read/write partition value.
     */
    protected static PartitionStrategy testPartition(String id) {
        return PartitionStrategy.build()
                .partitionKey(TT.partition)
                .writePartition(id)
                .readPartitions(id)
                .create();
    }

    /**
     * Creates a partition with the specified read/write partition value.
     */
    protected static PartitionStrategy testPartition(String write, String firstRead, String... restRead) {
        return PartitionStrategy.build()
                .partitionKey(TT.partition)
                .writePartition(write)
                .readPartitions(asList(firstRead, restRead))
                .create();
    }

    /**
     * Creates a partition identified by a new random value.
     */
    protected static PartitionStrategy createRandomPartition() {
        return testPartition(UUID.randomUUID().toString());
    }

    /**
     * The default frame to use for testing.
     */
    private static final BdioFrame DEFAULT_FRAME = new BdioFrame.Builder()
            .context(new BdioContext.Builder()
                    .expandContext(ImmutableMap.builder()
                            .put(Bdio.DataProperty.path.name(), Bdio.DataProperty.path.toString())
                            .put(Bdio.DataProperty.name.name(), Bdio.DataProperty.name.toString())
                            .put(Bdio.DataProperty.creationDateTime.name(), ImmutableMap.builder()
                                    .put(JsonLdConsts.ID, Bdio.DataProperty.creationDateTime.toString())
                                    .put(JsonLdConsts.TYPE, Bdio.Datatype.DateTime.toString())
                                    .build())
                            .put(Bdio.DataProperty.fingerprint.name(), ImmutableMap.builder()
                                    .put(JsonLdConsts.ID, Bdio.DataProperty.fingerprint.toString())
                                    .put(JsonLdConsts.TYPE, Bdio.Datatype.Digest.toString())
                                    .put(JsonLdConsts.CONTAINER, JsonLdConsts.SET)
                                    .build())
                            .put(Bdio.DataProperty.byteCount.name(), ImmutableMap.builder()
                                    .put(JsonLdConsts.ID, Bdio.DataProperty.byteCount.toString())
                                    .put(JsonLdConsts.TYPE, Bdio.Datatype.Long.toString())
                                    .build())
                            .put(Bdio.DataProperty.fileSystemType.name(), Bdio.DataProperty.fileSystemType.toString())
                            .put(Bdio.DataProperty.linkPath.name(), Bdio.DataProperty.linkPath.toString())
                            .put(Bdio.DataProperty.contentType.name(), ImmutableMap.builder()
                                    .put(JsonLdConsts.ID, Bdio.DataProperty.contentType.toString())
                                    .put(JsonLdConsts.TYPE, Bdio.Datatype.ContentType.toString())
                                    .build())
                            .put(Bdio.DataProperty.encoding.name(), Bdio.DataProperty.encoding.toString())
                            .put(Bdio.ObjectProperty.base.name(), ImmutableMap.builder()
                                    .put(JsonLdConsts.ID, Bdio.ObjectProperty.base.toString())
                                    .put(JsonLdConsts.TYPE, JsonLdConsts.ID)
                                    .build())
                            .put(Bdio.ObjectProperty.subproject.name(), ImmutableMap.builder()
                                    .put(JsonLdConsts.ID, Bdio.ObjectProperty.subproject.toString())
                                    .put(JsonLdConsts.TYPE, JsonLdConsts.ID)
                                    .build())
                            .put(Bdio.ObjectProperty.previousVersion.name(), ImmutableMap.builder()
                                    .put(JsonLdConsts.ID, Bdio.ObjectProperty.previousVersion.toString())
                                    .put(JsonLdConsts.TYPE, JsonLdConsts.ID)
                                    .build())
                            .build())
                    .build())
            .build();

    /**
     * The graph instance to test.
     */
    protected Graph graph;

    /**
     * The frame describing the graph schema.
     */
    private final BdioFrame frame;

    /**
     * The active context (obtained via the frame).
     */
    private ActiveContext activeContext;

    public BaseTest(Graph graph, BdioFrame frame) {
        this.graph = Objects.requireNonNull(graph);
        this.frame = Objects.requireNonNull(frame);
    }

    public BaseTest(Graph graph) {
        this(graph, DEFAULT_FRAME);
    }

    /**
     * Helper method to commit the current graph transaction only if the graph supports them.
     */
    public final void commit() {
        if (graph.features().graph().supportsTransactions()) {
            graph.tx().commit();
        }
    }

    /**
     * Activates the context from the frame.
     */
    @Before
    public final void activateContext() {
        activeContext = frame.context().activate();
    }

    /**
     * Clear the context.
     */
    @After
    public final void clearContext() {
        if (activeContext != null) {
            activeContext.close();
            activeContext = null;
        }
    }

    /**
     * Restores the graph to a clean state at the start of each test.
     */
    @Before
    public final void cleanGraph() throws Exception {
        if (graph instanceof TinkerGraph) {
            ((TinkerGraph) graph).clear();
        } else if (graph instanceof SqlgGraph) {
            SqlgDataSource dataSource = ((SqlgGraph) graph).getSqlgDataSource();
            BdioCallback bdioCallback = new BdioCallback(b -> b.fromExistingFrame(frame), emptyList()) {
                @Override
                protected SqlgFlywayExecutor executor(Configuration configuration) {
                    return new SqlgFlywayExecutor(configuration) {
                        @Override
                        public void execute(SqlgTask task) throws Exception {
                            task.run((SqlgGraph) graph);
                        }
                    };
                }

                @Override
                protected SqlDialect sqlDialect(Configuration configuration) {
                    return ((SqlgGraph) graph).getSqlDialect();
                }
            };

            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource.getDatasource())
                    .callbacks(bdioCallback)
                    .installedBy("BaseTest")
                    .locations("com/blackducksoftware/bdio2/tinkerpop/sqlg/flyway")
                    .load();

            flyway.clean();
            flyway.migrate();

            // The old graph is can't be saved, and we can't close it or we loose our data source
            graph = SqlgGraph.open(graph.configuration(), (d, c) -> dataSource);
        }
    }

    @After
    public void rollbackOpenTx() {
        if (graph.features().graph().supportsTransactions() && graph.tx().isOpen()) {
            graph.tx().rollback();
        }
    }

}
