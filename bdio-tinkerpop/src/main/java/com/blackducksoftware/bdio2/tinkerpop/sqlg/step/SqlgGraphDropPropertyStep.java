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
package com.blackducksoftware.bdio2.tinkerpop.sqlg.step;

import static java.util.stream.Collectors.joining;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.PropertiesStep;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.sql.parse.SchemaTableTree;
import org.umlg.sqlg.step.SqlgGraphStep;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.SqlgVertex;
import org.umlg.sqlg.structure.topology.AbstractLabel;
import org.umlg.sqlg.structure.topology.PropertyColumn;

import com.google.common.collect.Maps;

/**
 * This is supposed to prevent a simple {@code g.V().properties("foo").drop().iterate()} query from actually
 * transferring all of the vertices.
 * <p>
 * <em>WARNING!</em> This was written with exactly zero knowledge of how TinkerPop or Sqlg internals work. There is a
 * good chance that it is not safe for general use!
 *
 * @author jgustie
 */
public class SqlgGraphDropPropertyStep<S> extends AbstractSimpleSqlgOptimizationStep<S> {
    private static final long serialVersionUID = 1L;

    private final List<String> propertyKeys;

    public SqlgGraphDropPropertyStep(Traversal.Admin<?, ?> traversal, SqlgGraphStep<?, ?> replacedStep) {
        super(traversal, replacedStep);

        // Store the properties being dropped
        propertyKeys = new ArrayList<>();
        Step<?, ?> step = replacedStep.getNextStep();
        while (step instanceof PropertiesStep<?>) {
            propertyKeys.addAll(Arrays.asList(((PropertiesStep<?>) step).getPropertyKeys()));
            step = step.getNextStep();
        }
    }

    @Override
    public String toString() {
        return StringFactory.stepString(this, propertyKeys);
    }

    @Override
    protected void process(SqlgGraph sqlgGraph, SchemaTableTree rootSchemaTableTree, AbstractLabel label) {
        SchemaTable table = rootSchemaTableTree.getSchemaTable();

        Map<String, PropertyColumn> properties = label.getProperties();
        properties.keySet().retainAll(propertyKeys);

        StringBuilder sql = new StringBuilder().append("\n")
                .append("UPDATE\n\t")
                .append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(table.getSchema()))
                .append(".")
                .append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(table.getTable()))
                .append("\nSET\n")
                .append(properties.entrySet().stream()
                        .flatMap(e -> {
                            Stream.Builder<String> keys = Stream.<String> builder().add(e.getKey());
                            PropertyType pt = e.getValue().getPropertyType();
                            String[] postfixes = sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(pt);
                            for (int i = 1; i < postfixes.length; ++i) {
                                keys.add(e.getKey() + pt.getPostFixes()[i - 1]);
                            }
                            return keys.build();
                        })
                        .map(sqlgGraph.getSqlDialect()::maybeWrapInQoutes)
                        .map(k -> k + " = NULL")
                        .collect(joining(",\n\t", "\t", "\n")));

        Map<String, PropertyType> propertyTypes = Maps.transformValues(label.getProperties(), PropertyColumn::getPropertyType);
        appendWhereClause(sql, propertyTypes, rootSchemaTableTree.getHasContainers());

        LoggerFactory.getLogger(SqlgVertex.class).debug("{}", sql);
        try (Statement statement = sqlgGraph.tx().getConnection().createStatement()) {
            statement.executeUpdate(sql.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
