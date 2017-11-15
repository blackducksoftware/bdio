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

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.umlg.sqlg.structure.PropertyType.BOOLEAN;
import static org.umlg.sqlg.structure.PropertyType.STRING;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collector;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.AbstractIoRegistry;
import org.umlg.sqlg.structure.IndexType;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.RecordId;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.Topology;
import org.umlg.sqlg.structure.VertexLabel;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.datatype.DatatypeSupport;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoMapper.DatatypeRegistration;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoMapper.GraphInitializer;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoMapper.MultiValueCollectorRegistration;
import com.blackducksoftware.common.base.ExtraFunctions;

/**
 * An I/O registry that provides specific configuration for supported graph implementations.
 *
 * @author jgustie
 */
public class BlackDuckIoRegistry extends AbstractIoRegistry {

    protected BlackDuckIoRegistry(Graph graph) {
        // Special case a bunch of behavior only for Sqlg
        if (graph instanceof SqlgGraph) {
            // Initialize the graph by creating the necessary schema elements and indexes
            register(BlackDuckIo.class, null, new GraphInitializer() {
                @Override
                public void initialize(GraphTopology graphTopology) {
                    defineTopology((SqlgGraph) graph, graphTopology);
                }
            });

            // Basically turn any multi-valued anything into a String[] or Sqlg won't accept it
            register(BlackDuckIo.class, null, new MultiValueCollectorRegistration() {
                @Override
                public Collector<? super Object, ?, ?> collector() {
                    return collectingAndThen(mapping(Object::toString, toList()), l -> l.toArray(new String[l.size()]));
                }
            });

            // This is just to make sure Sqlg works (otherwise RecordId wouldn't serialize correctly)
            registerOverride(Bdio.Datatype.Default, DatatypeHandler.from(
                    x -> DatatypeSupport.Default().isInstance(x) || x instanceof RecordId,
                    DatatypeSupport.Default()::serialize,
                    DatatypeSupport.Default()::deserialize));

            // Really this is sqlg specific because TinkerGraph lets any object in
            registerOverride(Bdio.Datatype.Digest, DatatypeHandler.from(
                    DatatypeSupport.Digest()::isInstance,
                    DatatypeSupport.Digest()::serialize,
                    ExtraFunctions.nullSafe(DatatypeSupport.Digest()::deserialize).andThen(Object::toString)));
            registerOverride(Bdio.Datatype.Products, DatatypeHandler.from(
                    DatatypeSupport.Products()::isInstance,
                    DatatypeSupport.Products()::serialize,
                    ExtraFunctions.nullSafe(DatatypeSupport.Products()::deserialize).andThen(Object::toString)));
            registerOverride(Bdio.Datatype.ContentRange, DatatypeHandler.from(
                    DatatypeSupport.ContentRange()::isInstance,
                    DatatypeSupport.ContentRange()::serialize,
                    ExtraFunctions.nullSafe(DatatypeSupport.ContentRange()::deserialize).andThen(Object::toString)));
            registerOverride(Bdio.Datatype.ContentType, DatatypeHandler.from(
                    DatatypeSupport.ContentType()::isInstance,
                    DatatypeSupport.ContentType()::serialize,
                    ExtraFunctions.nullSafe(DatatypeSupport.ContentType()::deserialize).andThen(Object::toString)));
        }
    }

    /**
     * Define a minimal topology to help reduce the number of dynamic topology changes.
     */
    public void defineTopology(SqlgGraph sqlgGraph, GraphTopology graphTopology) {
        sqlgGraph.tx().open();
        try {
            Topology topology = sqlgGraph.getTopology();

            graphTopology.metadataLabel().ifPresent(label -> defineVertexLabel(topology, graphTopology, label));
            graphTopology.forEachTypeLabel(label -> defineVertexLabel(topology, graphTopology, label));
            defineEdgeLabels(topology, graphTopology);

            sqlgGraph.tx().commit();
        } catch (RuntimeException | Error e) {
            sqlgGraph.tx().rollback();
            throw e;
        }
    }

    /**
     * Defines the topology for a specific vertex label. Most labels contain the same base set of properties.
     */
    private void defineVertexLabel(Topology topology, GraphTopology graphTopology, String label) {
        // TODO We need a unique constraint on _partition/_id for all labels (if applicable)
        // TODO We need a unique constraint on _partition/path for files (if applicable)

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

    /**
     * Helper to register an override for a built-in BDIO datatype.
     */
    private void registerOverride(Bdio.Datatype datatype, DatatypeHandler<?> handler) {
        register(BlackDuckIo.class, null, new DatatypeRegistration() {
            @Override
            public String iri() {
                return datatype.toString();
            }

            @Override
            public DatatypeHandler<?> handler() {
                return handler;
            }
        });
    }

}
