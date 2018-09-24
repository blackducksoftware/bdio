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
import static org.umlg.sqlg.structure.PropertyType.JSON;
import static org.umlg.sqlg.structure.PropertyType.STRING;
import static org.umlg.sqlg.structure.topology.IndexType.NON_UNIQUE;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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

import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.topology.EdgeLabel;
import org.umlg.sqlg.structure.topology.PropertyColumn;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOperations.AdminGraphInitializer;
import com.blackducksoftware.common.base.ExtraStreams;
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
class SqlgGraphInitializer {

    // TODO Is there a good way to introduce a Sqlg property type mapping through an IoRegistry?
    // If we could get a Bdio.Datatype and Bdio.Container for every property that would work also...

    public SqlgGraphInitializer() {
    }

    public Stream<GraphInitializer> stream() {
        return Stream.<GraphInitializer> builder()
                .add(new BdioSqlFunctionGraphInitializer())
                .add(new BdioMetadataGraphInitializer())
                .add(new BdioVertexGraphInitializer())
                .add(new BdioEdgeGraphInitializer())
                .build();
    }

    /**
     * Base class for Sqlg graph initializations.
     */
    private static abstract class AbstractSqlgGraphInitializer implements AdminGraphInitializer {
        @Override
        public final void initialize(GraphReaderWrapper wrapper) {
            if (wrapper instanceof SqlgGraphReaderWrapper) {
                initialize((SqlgGraphReaderWrapper) wrapper);
            }
        }

        protected abstract void initialize(SqlgGraphReaderWrapper wrapper);
    }

    private static class BdioSqlFunctionGraphInitializer extends AbstractSqlgGraphInitializer {
        @Override
        public Step initializationStep() {
            // Since we are not calling through the `Topology`, we need to invoke this last so someone else can acquire
            // the topology write lock for us (`Topology.lock` isn't visible).
            return Step.FINISH;
        }

        @Override
        protected void initialize(SqlgGraphReaderWrapper wrapper) {
            // Used for flattening split nodes, from https://wiki.postgresql.org/wiki/First/last_(aggregate)
            List<String> queries = new ArrayList<>();
            queries.add("CREATE OR REPLACE FUNCTION public.first_agg ( anyelement, anyelement )"
                    + "\nRETURNS anyelement LANGUAGE SQL IMMUTABLE STRICT AS $$"
                    + "\n\tSELECT $1;"
                    + "\n$$;");
            queries.add("DROP AGGREGATE IF EXISTS public.first ( anyelement );");
            queries.add("CREATE AGGREGATE public.first( sfunc = first_agg, stype = anyelement, basetype = anyelement );");

            Connection conn = wrapper.graph().tx().getConnection();
            try (Statement statement = conn.createStatement()) {
                for (String query : queries) {
                    statement.executeUpdate(query);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class BdioMetadataGraphInitializer extends AbstractSqlgGraphInitializer {
        @Override
        public Step initializationStep() {
            return Step.METADATA;
        }

        @Override
        protected void initialize(SqlgGraphReaderWrapper wrapper) {
            wrapper.mapper().metadataLabel().ifPresent(label -> {
                Map<String, PropertyType> columns = new HashMap<>();
                for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
                    if (domain(dataProperty).anyMatch(Bdio.Domain::metadata)) {
                        Bdio.Datatype datatype = dataRange(dataProperty).map(Bdio.DataPropertyRange::value).orElse(Bdio.Datatype.Default);
                        Bdio.Container container = dataProperty.container();
                        columns.put(dataProperty.name(), toPropertyType(datatype, container));
                    }
                }
                ensureVertexLabelExist(label, columns, Collections.emptyList(), wrapper);
            });
        }
    }

    private static class BdioVertexGraphInitializer extends AbstractSqlgGraphInitializer {
        @Override
        public Step initializationStep() {
            return Step.VERTEX;
        }

        @Override
        protected void initialize(SqlgGraphReaderWrapper wrapper) {
            for (Bdio.Class bdioClass : Bdio.Class.values()) {
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

                    columns.put(GraphMapper.FILE_PARENT_KEY, PropertyType.STRING);
                    nonUniqueIndexNames.add(GraphMapper.FILE_PARENT_KEY);
                }
                ensureVertexLabelExist(bdioClass.name(), columns, nonUniqueIndexNames, wrapper);
            }
        }
    }

    private static class BdioEdgeGraphInitializer extends AbstractSqlgGraphInitializer {
        @Override
        public Step initializationStep() {
            return Step.EDGE;
        }

        @Override
        protected void initialize(SqlgGraphReaderWrapper wrapper) {
            Topology topology = wrapper.graph().getTopology();
            for (Bdio.ObjectProperty objectProperty : Bdio.ObjectProperty.values()) {
                List<VertexLabel> range = objectRange(objectProperty)
                        .flatMap(p -> p.value().length > 0 ? Arrays.stream(p.value()) : ExtraStreams.stream(Bdio.Class.class))
                        .map(Bdio.Class::name)
                        .map(topology::ensureVertexLabelExist)
                        .collect(Collectors.toList());

                domain(objectProperty)
                        .flatMap(d -> Arrays.stream(d.value()))
                        .map(Bdio.Class::name)
                        .map(topology::ensureVertexLabelExist)
                        .forEach(d -> range.forEach(r -> ensureEdgeLabelExist(objectProperty.name(), d, r, wrapper)));
            }

            wrapper.mapper().rootLabel().ifPresent(l -> {
                wrapper.mapper().metadataLabel()
                        .map(topology::ensureVertexLabelExist)
                        .ifPresent(d -> {
                            ExtraStreams.stream(Bdio.Class.class)
                                    .filter(Bdio.Class::root)
                                    .map(Bdio.Class::name)
                                    .map(topology::ensureVertexLabelExist)
                                    .forEach(r -> ensureEdgeLabelExist(l, d, r, wrapper));
                        });
            });
        }
    }

    private static VertexLabel ensureVertexLabelExist(String label, Map<String, PropertyType> labelColumns, List<String> labelNonUniqueIndexColumns,
            SqlgGraphReaderWrapper wrapper) {
        // Build up an effective columns with additional topology specific columns
        Map<String, PropertyType> columns = new TreeMap<>();
        List<String> nonUniqueIndexNames = new ArrayList<>();

        wrapper.forEachPartition((c, p) -> {
            columns.put(c, STRING);
            nonUniqueIndexNames.add(c);
        });
        wrapper.mapper().unknownKey().ifPresent(c -> {
            columns.put(c, JSON);
        });
        wrapper.mapper().identifierKey().ifPresent(c -> {
            columns.put(c, STRING);
            // TODO Instead of non-unique, we need a unique on _partition/_id (or just _id)
            nonUniqueIndexNames.add(c);
        });
        wrapper.mapper().implicitKey().ifPresent(c -> {
            columns.put(c, BOOLEAN);
        });

        // Create the "special" columns first
        if (!columns.isEmpty()) {
            wrapper.graph().getTopology().ensureVertexLabelExist(label, columns);
            columns.clear();
        }

        columns.putAll(labelColumns);
        nonUniqueIndexNames.addAll(labelNonUniqueIndexColumns);

        // Create the table and indexes
        VertexLabel vertexLabel = wrapper.graph().getTopology().ensureVertexLabelExist(label, columns);
        nonUniqueIndexNames.stream()
                .flatMap(((Function<String, Optional<PropertyColumn>>) vertexLabel::getProperty).andThen(Streams::stream))
                .map(Collections::singletonList)
                .forEach(properties -> vertexLabel.ensureIndexExists(NON_UNIQUE, properties));

        return vertexLabel;
    }

    private static EdgeLabel ensureEdgeLabelExist(String label, VertexLabel out, VertexLabel in, SqlgGraphReaderWrapper wrapper) {
        // Unlike vertices, there is only one set of properties for edges
        Map<String, PropertyType> columns = new TreeMap<>();
        List<String> nonUniqueIndexNames = new ArrayList<>();

        wrapper.forEachPartition((c, p) -> {
            columns.put(c, STRING);
            nonUniqueIndexNames.add(c);
        });
        wrapper.mapper().implicitKey().ifPresent(c -> {
            columns.put(c, BOOLEAN);
        });

        EdgeLabel edgeLabel = wrapper.graph().getTopology().ensureEdgeLabelExist(label, out, in, columns);
        nonUniqueIndexNames.stream()
                .flatMap(((Function<String, Optional<PropertyColumn>>) edgeLabel::getProperty).andThen(Streams::stream))
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
