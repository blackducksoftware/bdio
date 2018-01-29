/*
 * Copyright 2018 Black Duck Software, Inc.
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

import static org.umlg.sqlg.structure.PropertyType.BOOLEAN;
import static org.umlg.sqlg.structure.PropertyType.STRING;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.umlg.sqlg.structure.IndexType;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.Topology;
import org.umlg.sqlg.structure.VertexLabel;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoMapper.GraphInitializer;

/**
 * Graph initializer specific to Sqlg. Uses the implementation specific topology to pre-define vertex and edge schema in
 * the database (including necessary column indexes).
 *
 * @author jgustie
 */
class SqlgGraphInitializer implements GraphInitializer {

    private final SqlgGraph graph;

    protected SqlgGraphInitializer(SqlgGraph graph) {
        this.graph = Objects.requireNonNull(graph);
    }

    @Override
    public void initialize(GraphTopology graphTopology) {
        graph.tx().open();
        try {
            Topology topology = graph.getTopology();

            graphTopology.metadataLabel().ifPresent(label -> defineVertexLabel(topology, graphTopology, label));
            graphTopology.forEachTypeLabel(label -> defineVertexLabel(topology, graphTopology, label));
            defineEdgeLabels(topology, graphTopology);

            graph.tx().commit();
        } catch (RuntimeException | Error e) {
            graph.tx().rollback();
            throw e;
        }
    }

    /**
     * Defines the topology for a specific vertex label. Most labels contain the same base set of properties.
     */
    private void defineVertexLabel(Topology topology, GraphTopology graphTopology, String label) {
        // TODO We need a unique constraint on _partition/_id for all labels (if applicable)
        // TODO We need a unique constraint on _partition/path for files (if applicable)

        // TODO Use reflection on Bdio.Domain to determine which columns to add
        // This is just to help make the code look more uniform below
        Optional<String> fileFileSystemType = Optional.of(label).filter(Predicate.isEqual(Bdio.Class.File.name()))
                .map(x -> Bdio.DataProperty.fileSystemType.name());
        Optional<String> filePathKey = Optional.of(label).filter(Predicate.isEqual(Bdio.Class.File.name()))
                .map(x -> Bdio.DataProperty.path.name());

        // Define the initial columns used to persist this vertex label
        Map<String, PropertyType> columns = new LinkedHashMap<>();
        graphTopology.partitionStrategy().map(PartitionStrategy::getPartitionKey).ifPresent(c -> columns.put(c, STRING));
        graphTopology.identifierKey().ifPresent(c -> columns.put(c, STRING));
        fileFileSystemType.ifPresent(c -> columns.put(c, STRING));
        filePathKey.ifPresent(c -> columns.put(c, STRING));
        graphTopology.implicitKey().ifPresent(c -> columns.put(c, BOOLEAN));
        graphTopology.unknownKey().ifPresent(c -> columns.put(c, STRING)); // TODO PropertyType.JSON

        // Ensure the vertex label exists with the proper columns
        VertexLabel vertexLabel = topology.ensureVertexLabelExist(label, columns);

        // Add indexes
        graphTopology.partitionStrategy().map(PartitionStrategy::getPartitionKey)
                .flatMap(vertexLabel::getProperty).map(Collections::singletonList)
                .ifPresent(properties -> vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, properties));
        graphTopology.identifierKey()
                .flatMap(vertexLabel::getProperty).map(Collections::singletonList)
                .ifPresent(properties -> vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, properties));
        filePathKey
                .flatMap(vertexLabel::getProperty).map(Collections::singletonList)
                .ifPresent(properties -> vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, properties));
    }

    /**
     * Defines the topology for some of the edge labels.
     */
    private void defineEdgeLabels(Topology topology, GraphTopology graphTopology) {
        // In general we do not store BDIO information on edges, however there are a few common properties we use
        Map<String, PropertyType> properties = new LinkedHashMap<>();
        graphTopology.partitionStrategy().map(PartitionStrategy::getPartitionKey).ifPresent(c -> properties.put(c, STRING));
        graphTopology.implicitKey().ifPresent(c -> properties.put(c, BOOLEAN));

        // Collect the vertex labels used to define edges
        VertexLabel projectVertex = topology.ensureVertexLabelExist(Bdio.Class.Project.name());
        VertexLabel fileVertex = topology.ensureVertexLabelExist(Bdio.Class.File.name());

        // Define a small number of edges (those that most commonly used or are extremely dense)
        topology.ensureEdgeLabelExist(Bdio.ObjectProperty.base.name(), projectVertex, fileVertex, properties);
        topology.ensureEdgeLabelExist(Bdio.ObjectProperty.parent.name(), fileVertex, fileVertex, properties);
    }

}
