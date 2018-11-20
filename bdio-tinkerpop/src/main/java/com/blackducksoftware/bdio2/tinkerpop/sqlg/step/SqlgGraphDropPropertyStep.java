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

import static com.blackducksoftware.common.base.ExtraOptionals.ofType;
import static com.blackducksoftware.common.base.ExtraThrowables.illegalState;
import static java.util.stream.Collectors.joining;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.PropertiesStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.AbstractStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.ProfileStep;
import org.apache.tinkerpop.gremlin.process.traversal.util.FastNoSuchElementException;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalMetrics;
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
import org.umlg.sqlg.util.SqlgUtil;

/**
 * This is supposed to prevent a simple {@code g.V().properties("foo").drop().iterate()} query from actually
 * transferring all of the vertices.
 * <p>
 * <em>WARNING!</em> This was written with exactly zero knowledge of how TinkerPop or Sqlg internals work. There is a
 * good chance that it is not safe for general use!
 *
 * @author jgustie
 */
public class SqlgGraphDropPropertyStep<S> extends AbstractStep<S, S> {
    private static final long serialVersionUID = 1L;

    private final SqlgGraph sqlgGraph;

    private final List<String> propertyKeys;

    private final Set<SchemaTableTree> rootSchemaTableTrees;

    public SqlgGraphDropPropertyStep(Traversal.Admin<?, ?> traversal, SqlgGraphStep<?, ?> replacedStep) {
        super(traversal);
        sqlgGraph = traversal.getGraph().flatMap(ofType(SqlgGraph.class))
                .orElseThrow(illegalState("expected SqlgGraph"));

        // Get the schema table tree for generating a WHERE clause
        rootSchemaTableTrees = replacedStep.parseForStrategy();

        // Store the properties being dropped
        propertyKeys = new ArrayList<>();
        Step<?, ?> step = replacedStep.getNextStep();
        while (step instanceof PropertiesStep<?>) {
            propertyKeys.addAll(Arrays.asList(((PropertiesStep<?>) step).getPropertyKeys()));
            step = step.getNextStep();
        }
    }

    @Override
    protected Traverser.Admin<S> processNextStart() throws NoSuchElementException {
        if (!rootSchemaTableTrees.isEmpty()) {
            rootSchemaTableTrees.forEach(this::drop);
            rootSchemaTableTrees.clear();
            if (getNextStep() instanceof ProfileStep<?>) {
                // TODO Should we return a traverser with the count of updated rows so it looks like we did something?
                ((ProfileStep<?>) getNextStep()).getMetrics().incrementCount(TraversalMetrics.TRAVERSER_COUNT_ID, 1);
                ((ProfileStep<?>) getNextStep()).getMetrics().incrementCount(TraversalMetrics.ELEMENT_COUNT_ID, 1);
            }
        }

        // Basically we flat map to nothing every time
        throw FastNoSuchElementException.instance();
    }

    @Override
    public String toString() {
        return StringFactory.stepString(this, propertyKeys);
    }

    private void drop(SchemaTableTree rootSchemaTableTree) {
        @SuppressWarnings("JdkObsolete") // This is the type required by the APIs being invoked
        LinkedList<SchemaTableTree> distinctQueryStack = new LinkedList<>();
        distinctQueryStack.add(rootSchemaTableTree);

        SchemaTable table = rootSchemaTableTree.getSchemaTable();

        Optional<? extends AbstractLabel> label;
        if (table.isVertexTable()) {
            label = sqlgGraph.getTopology().getVertexLabel(table.getSchema(), table.withOutPrefix().getTable());
        } else if (table.isEdgeTable()) {
            label = sqlgGraph.getTopology().getEdgeLabel(table.getSchema(), table.withOutPrefix().getTable());
        } else {
            throw new AssertionError("impossible table type");
        }
        if (!label.isPresent()) {
            // There is nothing to drop, the label does not exist
            return;
        }

        Map<String, PropertyColumn> properties = label.get().getProperties();
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

        String[] whereParts = rootSchemaTableTree.constructSql(distinctQueryStack).split("\\bWHERE\\b", 2);
        if (whereParts.length == 2) {
            sql.append("WHERE\n").append(whereParts[1]);
        }

        LoggerFactory.getLogger(SqlgVertex.class).debug("{}", sql);

        Connection conn = sqlgGraph.tx().getConnection();
        try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
            SqlgUtil.setParametersOnStatement(sqlgGraph, distinctQueryStack, preparedStatement, 1);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
