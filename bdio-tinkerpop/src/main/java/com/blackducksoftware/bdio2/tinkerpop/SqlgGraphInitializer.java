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

import static org.umlg.sqlg.structure.IndexType.NON_UNIQUE;
import static org.umlg.sqlg.structure.PropertyType.BOOLEAN;
import static org.umlg.sqlg.structure.PropertyType.STRING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.umlg.sqlg.structure.PropertyColumn;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.Topology;
import org.umlg.sqlg.structure.VertexLabel;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoMapper.GraphInitializer;
import com.blackducksoftware.common.base.ExtraEnums;
import com.google.common.base.Enums;
import com.google.common.collect.Streams;

/**
 * Graph initializer specific to Sqlg. Uses the implementation specific topology to pre-define vertex and edge schema in
 * the database (including necessary column indexes).
 * <p>
 * Note that in general we do not have enough information in the topology to define schema for user defined properties
 * in the topology; a separate initializer would be required to do that.
 *
 * @author jgustie
 */
class SqlgGraphInitializer extends GraphInitializer {

    // TODO Switch this to JSON and store JSON natively
    private static final PropertyType JSON = PropertyType.STRING;

    protected SqlgGraphInitializer(SqlgGraph graph) {
        super(graph);
    }

    @Override
    protected SqlgGraph graph() {
        return (SqlgGraph) super.graph();
    }

    @Override
    protected void initialize(GraphTopology graphTopology) {
        graphTopology.metadataLabel().ifPresent(label -> defineMetadata(label, graphTopology));
        graphTopology.forEachTypeLabel(label -> defineLabel(label, graphTopology));
        defineEdgeLabels(graphTopology);
    }

    private void defineMetadata(String label, GraphTopology graphTopology) {
        Map<String, PropertyType> columns = new HashMap<>();
        for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
            if (domain(dataProperty).filter(Bdio.Domain::metadata).anyMatch(x -> true)) {
                Bdio.Datatype datatype = dataRange(dataProperty).map(Bdio.DataPropertyRange::value).orElse(Bdio.Datatype.Default);
                Bdio.Container container = dataProperty.container();
                columns.put(dataProperty.name(), toPropertyType(datatype, container));
            }
        }
        ensureVertexLabelExist(label, columns, Collections.emptyList(), graphTopology);
    }

    private void defineLabel(String label, GraphTopology graphTopology) {
        ExtraEnums.tryByName(Bdio.Class.class, label).ifPresent(bdioClass -> {
            Map<String, PropertyType> columns = new HashMap<>();
            List<String> nonUniqueIndexNames = new ArrayList<>();
            for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
                if (domain(dataProperty).flatMap(d -> Arrays.stream(d.value())).anyMatch(Predicate.isEqual(bdioClass))) {
                    Bdio.Datatype datatype = dataRange(dataProperty).map(Bdio.DataPropertyRange::value).orElse(Bdio.Datatype.Default);
                    Bdio.Container container = dataProperty.container();
                    columns.put(dataProperty.name(), toPropertyType(datatype, container));
                }
            }
            if (bdioClass == Bdio.Class.File) {
                // TODO Instead of non-unique, we need a unique on _partition/path (or just path)
                nonUniqueIndexNames.add(Bdio.DataProperty.path.name());
            }
            ensureVertexLabelExist(label, columns, nonUniqueIndexNames, graphTopology);
        });
    }

    private VertexLabel ensureVertexLabelExist(String label, Map<String, PropertyType> labelColumns, List<String> labelNonUniqueIndexColumns,
            GraphTopology graphTopology) {
        // Build up an effective columns with additional topology specific columns
        Map<String, PropertyType> columns = new TreeMap<>();
        List<String> nonUniqueIndexNames = new ArrayList<>();

        columns.putAll(labelColumns);
        nonUniqueIndexNames.addAll(labelNonUniqueIndexColumns);

        graphTopology.partitionStrategy().map(PartitionStrategy::getPartitionKey).ifPresent(c -> {
            columns.put(c, STRING);
            nonUniqueIndexNames.add(c);
        });
        graphTopology.unknownKey().ifPresent(c -> {
            columns.put(c, JSON);
        });
        graphTopology.identifierKey().ifPresent(c -> {
            columns.put(c, STRING);
            // TODO Instead of non-unique, we need a unique on _partition/_id (or just _id)
            nonUniqueIndexNames.add(c);
        });
        graphTopology.implicitKey().ifPresent(c -> {
            columns.put(c, BOOLEAN);
        });

        // Create the table and indexes
        VertexLabel vertexLabel = graph().getTopology().ensureVertexLabelExist(label, columns);
        nonUniqueIndexNames.stream()
                .flatMap(((Function<String, Optional<PropertyColumn>>) vertexLabel::getProperty).andThen(Streams::stream))
                .map(Collections::singletonList)
                .forEach(properties -> vertexLabel.ensureIndexExists(NON_UNIQUE, properties));

        return vertexLabel;
    }

    /**
     * Defines the topology for some of the edge labels.
     */
    private void defineEdgeLabels(GraphTopology graphTopology) {
        // In general we do not store BDIO information on edges, however there are a few common properties we use
        Map<String, PropertyType> properties = new TreeMap<>();
        graphTopology.partitionStrategy().map(PartitionStrategy::getPartitionKey).ifPresent(c -> properties.put(c, STRING));
        graphTopology.implicitKey().ifPresent(c -> properties.put(c, BOOLEAN));

        Topology topology = graph().getTopology();
        for (Bdio.ObjectProperty objectProperty : Bdio.ObjectProperty.values()) {
            List<VertexLabel> range = objectRange(objectProperty)
                    .flatMap(p -> Arrays.stream(p.value()))
                    .map(Bdio.Class::name)
                    .map(topology::ensureVertexLabelExist)
                    .collect(Collectors.toList());

            domain(objectProperty)
                    .flatMap(d -> Arrays.stream(d.value()))
                    .map(Bdio.Class::name)
                    .map(topology::ensureVertexLabelExist)
                    .forEach(d -> {
                        for (VertexLabel r : range) {
                            topology.ensureEdgeLabelExist(objectProperty.name(), d, r, properties);
                        }
                    });
        }
    }

    private static Stream<Bdio.Domain> domain(Enum<?> e) {
        Bdio.Domain domain = Enums.getField(e).getAnnotation(Bdio.Domain.class);
        return domain != null ? Stream.of(domain) : Stream.empty();
    }

    private static Stream<Bdio.ObjectPropertyRange> objectRange(Bdio.ObjectProperty p) {
        Bdio.ObjectPropertyRange range = Enums.getField(p).getAnnotation(Bdio.ObjectPropertyRange.class);
        return range != null ? Stream.of(range) : Stream.empty();
    }

    private static Optional<Bdio.DataPropertyRange> dataRange(Bdio.DataProperty p) {
        return Optional.ofNullable(Enums.getField(p).getAnnotation(Bdio.DataPropertyRange.class));
    }

    private static PropertyType toPropertyType(Bdio.Datatype datatype, Bdio.Container container) {
        switch (datatype) {
        case DateTime:
            return container != Bdio.Container.single ? PropertyType.ZONEDDATETIME_ARRAY : PropertyType.ZONEDDATETIME;
        case Long:
            return container != Bdio.Container.single ? PropertyType.LONG_ARRAY : PropertyType.LONG;
        default:
            return container != Bdio.Container.single ? PropertyType.STRING_ARRAY : PropertyType.STRING;
        }
    }

}
