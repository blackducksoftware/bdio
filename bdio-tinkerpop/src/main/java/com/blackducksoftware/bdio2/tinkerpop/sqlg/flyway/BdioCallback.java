/*
 * Copyright 2018 Synopsys, Inc.
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
package com.blackducksoftware.bdio2.tinkerpop.sqlg.flyway;

import static com.blackducksoftware.common.base.ExtraStreams.fromOptional;
import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static java.util.stream.Collectors.toList;
import static org.umlg.sqlg.structure.PropertyType.JSON;
import static org.umlg.sqlg.structure.PropertyType.STRING;
import static org.umlg.sqlg.structure.topology.IndexType.NON_UNIQUE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.topology.EdgeLabel;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIo;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.bdio2.tinkerpop.strategy.PropertyConstantStrategy;
import com.blackducksoftware.common.base.ExtraStreams;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Enums;

public class BdioCallback extends SqlgCallback {

    private final Consumer<BlackDuckIo.Builder> configureBdio;

    private final List<TraversalStrategy<?>> strategies;

    public BdioCallback(Consumer<BlackDuckIo.Builder> configureBdio, List<TraversalStrategy<?>> strategies) {
        this.configureBdio = Objects.requireNonNull(configureBdio);
        this.strategies = Objects.requireNonNull(strategies);
    }

    public static BdioCallback create(Consumer<BlackDuckIo.Builder> configureBdio, TraversalStrategy<?>... strategies) {
        return new BdioCallback(configureBdio, Arrays.asList(strategies));
    }

    @Override
    protected void topologyEagerCreation(Topology topology) {
        BlackDuckIo.Builder builder = BlackDuckIo.build();
        configureBdio.accept(builder);
        BlackDuckIo bdio = topology.getSqlgGraph().io(builder);

        BlackDuckIoOptions options = bdio.options();
        BdioFrame frame = bdio.mapper().create().createMapper();
        List<TraversalStrategy<?>> strategies = topology.getSqlgGraph().traversal()
                .withStrategies(this.strategies.toArray(new TraversalStrategy<?>[this.strategies.size()]))
                .getStrategies().toList();

        // Define the schema
        vertex(topology, options, frame, strategies);
        dataProperties(topology, options, frame.context());
        objectProperties(topology, frame.context(), strategies);
        metadata(topology, options, frame.context(), strategies);
    }

    protected void vertex(Topology topology, BlackDuckIoOptions options, BdioFrame frame, List<TraversalStrategy<?>> strategies) {
        // Compute the list of required properties and indices
        Map<String, PropertyType> columns = new TreeMap<>();
        List<String> nonUniqueIndexNames = new ArrayList<>();
        computeColumns(columns, nonUniqueIndexNames, options, strategies);

        // Ensure the a vertex label exists for every type identified in the context frame
        ((List<?>) frame.serialize().get(JsonLdConsts.TYPE)).stream()
                .flatMap(ofType(String.class))
                .flatMap(fromOptional(frame.context()::lookupTerm))
                .forEach(label -> ensureVertexLabelExist(topology, label, columns, nonUniqueIndexNames));
    }

    protected void dataProperties(Topology topology, BlackDuckIoOptions options, BdioContext context) {
        for (Bdio.Class bdioClass : Bdio.Class.values()) {
            String label = context.lookupTerm(bdioClass.toString()).orElse(null);
            if (label != null) {
                // Compute the list of data properties for each built-in class
                Map<String, PropertyType> columns = new TreeMap<>();
                List<String> nonUniqueIndexNames = new ArrayList<>();

                for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
                    String term = context.lookupTerm(dataProperty.toString()).orElse(null);
                    if (term != null && domain(dataProperty).flatMap(d -> Arrays.stream(d.value())).anyMatch(Predicate.isEqual(bdioClass))) {
                        Bdio.Datatype datatype = dataRange(dataProperty).map(Bdio.DataPropertyRange::value).orElse(Bdio.Datatype.Default);
                        Bdio.Container container = dataProperty.container();
                        columns.put(term, toPropertyType(datatype, container));

                        // Index the BDIO File path
                        if (bdioClass == Bdio.Class.File && dataProperty == Bdio.DataProperty.path) {
                            // TODO Instead of non-unique, we need a unique on _partition/path (or just path)
                            nonUniqueIndexNames.add(term);
                        }
                    }
                }

                // Add a special "parent path" column to the file
                if (bdioClass == Bdio.Class.File) {
                    options.fileParentKey().ifPresent(fileParentKey -> {
                        columns.put(fileParentKey, PropertyType.STRING);
                        nonUniqueIndexNames.add(fileParentKey);
                    });
                }

                // Add the properties and indices
                ensureVertexLabelExist(topology, label, columns, nonUniqueIndexNames);
            }
        }
    }

    protected void objectProperties(Topology topology, BdioContext context, List<TraversalStrategy<?>> strategies) {
        // Unlike vertices, there is only one set of properties for edges
        Map<String, PropertyType> columns = new TreeMap<>();
        List<String> nonUniqueIndexNames = new ArrayList<>();
        computeColumns(columns, nonUniqueIndexNames, null, strategies);

        for (Bdio.ObjectProperty objectProperty : Bdio.ObjectProperty.values()) {
            String label = context.lookupTerm(objectProperty.toString()).orElse(null);
            if (label != null) {
                List<VertexLabel> domain = domain(objectProperty)
                        .flatMap(d -> Arrays.stream(d.value()))
                        .flatMap(fromOptional(c -> context.lookupTerm(c.toString())))
                        .map(topology::ensureVertexLabelExist)
                        .collect(toList());
                List<VertexLabel> range = objectRange(objectProperty)
                        .flatMap(p -> p.value().length > 0 ? Arrays.stream(p.value()) : ExtraStreams.stream(Bdio.Class.class))
                        .flatMap(fromOptional(c -> context.lookupTerm(c.toString())))
                        .map(topology::ensureVertexLabelExist)
                        .collect(toList());

                // TODO There is no efficient way to bulk add multiple in/out edges, relative to the rest of the
                // migration, this is the only noticeable part
                for (VertexLabel d : domain) {
                    for (VertexLabel r : range) {
                        ensureEdgeLabelExist(topology, label, d, r, columns, nonUniqueIndexNames);
                    }
                }
            }
        }
    }

    protected void metadata(Topology topology, BlackDuckIoOptions options, BdioContext context, List<TraversalStrategy<?>> strategies) {
        String label = options.metadataLabel().orElse(null);
        if (label == null) {
            return;
        }

        Map<String, PropertyType> columns = new TreeMap<>();
        List<String> nonUniqueIndexNames = new ArrayList<>();
        computeColumns(columns, nonUniqueIndexNames, options, strategies);

        for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
            String term = context.lookupTerm(dataProperty.toString()).orElse(null);
            if (term != null && domain(dataProperty).anyMatch(Bdio.Domain::metadata)) {
                Bdio.Datatype datatype = dataRange(dataProperty).map(Bdio.DataPropertyRange::value).orElse(Bdio.Datatype.Default);
                Bdio.Container container = dataProperty.container();
                columns.put(term, toPropertyType(datatype, container));
            }
        }
        VertexLabel metadataLabel = ensureVertexLabelExist(topology, label, columns, nonUniqueIndexNames);

        String rootLabel = options.rootLabel().orElse(null);
        if (rootLabel != null) {
            // Recompute the columns for the edge
            columns.clear();
            nonUniqueIndexNames.clear();
            computeColumns(columns, nonUniqueIndexNames, null, strategies);

            ExtraStreams.stream(Bdio.Class.class)
                    .filter(Bdio.Class::root)
                    .flatMap(fromOptional(c -> context.lookupTerm(c.toString())))
                    .map(topology::ensureVertexLabelExist)
                    .forEach(r -> ensureEdgeLabelExist(topology, rootLabel, metadataLabel, r, columns, nonUniqueIndexNames));
        }
    }

    private static void computeColumns(Map<String, PropertyType> columns, List<String> nonUniqueIndexNames,
            @Nullable BlackDuckIoOptions options, List<TraversalStrategy<?>> strategies) {
        // Partition strategies that contain a write value should be indexed
        strategies.stream()
                .flatMap(ofType(PartitionStrategy.class))
                .forEach(ps -> {
                    columns.put(ps.getPartitionKey(), STRING);
                    if (ps.getWritePartition() != null) {
                        nonUniqueIndexNames.add(ps.getPartitionKey());
                    }
                });

        if (options != null) {
            // Preservation of unrecognized values
            options.unknownKey().ifPresent(c -> columns.put(c, JSON));

            // Preservation of JSON-LD identifiers
            options.identifierKey().ifPresent(c -> {
                columns.put(c, STRING);
                // TODO Instead of non-unique, we need a unique on _partition/_id (or just _id)
                nonUniqueIndexNames.add(c);
            });
        }

        // Property constants
        strategies.stream()
                .flatMap(ofType(PropertyConstantStrategy.class))
                .forEach(psc -> psc.getPropertyMap().forEach((k, v) -> columns.put(k.toString(), PropertyType.from(v))));
    }

    private static VertexLabel ensureVertexLabelExist(Topology topology, String label,
            Map<String, PropertyType> columns, List<String> nonUniqueIndexNames) {
        VertexLabel vertexLabel = topology.ensureVertexLabelExist(label, columns);
        nonUniqueIndexNames.stream()
                .flatMap(fromOptional(vertexLabel::getProperty))
                .map(Collections::singletonList)
                .forEach(properties -> vertexLabel.ensureIndexExists(NON_UNIQUE, properties));
        return vertexLabel;
    }

    private static EdgeLabel ensureEdgeLabelExist(Topology topology, String label, VertexLabel out, VertexLabel in,
            Map<String, PropertyType> columns, List<String> nonUniqueIndexNames) {
        EdgeLabel edgeLabel = topology.ensureEdgeLabelExist(label, out, in, columns);
        nonUniqueIndexNames.stream()
                .flatMap(fromOptional(edgeLabel::getProperty))
                .map(Collections::singletonList)
                .forEach(properties -> edgeLabel.ensureIndexExists(NON_UNIQUE, properties));
        return edgeLabel;
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
