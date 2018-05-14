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
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.collect.MoreCollectors.toOptional;
import static java.util.stream.Collectors.joining;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.sideEffect.AddPropertyStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.AbstractStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.Parameters;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.ProfileStep;
import org.apache.tinkerpop.gremlin.process.traversal.util.FastNoSuchElementException;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalMetrics;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.sql.parse.SchemaTableTree;
import org.umlg.sqlg.step.SqlgGraphStep;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.SqlgVertex;
import org.umlg.sqlg.util.SqlgUtil;

import com.google.common.collect.Maps;

/**
 * This is supposed to prevent a simple {@code g.V().property("foo", "1").iterate()} query from actually transferring
 * all of the vertices.
 * <p>
 * <em>WARNING!</em> This was written with exactly zero knowledge of how TinkerPop or Sqlg internals work. There is a
 * good chance that it is not safe for general use!
 *
 * @author jgustie
 */
public class SqlgGraphAddPropertyStep<S> extends AbstractStep<S, S> {
    private static final long serialVersionUID = 1L;

    private final SqlgGraph sqlgGraph;

    private final LinkedList<SchemaTableTree> distinctQueryStack = new LinkedList<>();

    private final LinkedHashMap<String, Object> properties;

    public SqlgGraphAddPropertyStep(Traversal.Admin<?, ?> traversal, SqlgGraphStep<?, ?> replacedStep) {
        super(traversal);
        sqlgGraph = traversal.getGraph().flatMap(ofType(SqlgGraph.class))
                .orElseThrow(illegalState("expected SqlgGraph"));

        // Get the schema table tree for generating a WHERE clause
        distinctQueryStack.add(replacedStep.parseForStrategy().stream().collect(onlyElement()));

        // Store the properties being updated
        properties = new LinkedHashMap<>();
        Step<?, ?> step = replacedStep.getNextStep();
        while (step instanceof AddPropertyStep<?>) {
            Parameters parameters = ((AddPropertyStep<?>) step).getParameters();
            String key = parameters.<String> get(T.key, () -> null).stream().filter(s -> s != null).collect(toOptional()).orElseThrow(illegalState(""));
            Object value = parameters.get(T.value, () -> {
                throw new IllegalStateException("");
            }).get(0);
            properties.put(key, value); // TODO Verify it was unique? Multimap?
            step = step.getNextStep();
        }
    }

    @Override
    protected Traverser.Admin<S> processNextStart() throws NoSuchElementException {
        if (!distinctQueryStack.isEmpty()) {
            SchemaTable table = distinctQueryStack.getFirst().getSchemaTable();

            sqlgGraph.getTopology().ensureVertexLabelPropertiesExist(table.getSchema(), table.withOutPrefix().getTable(),
                    Maps.transformValues(properties, PropertyType::from));

            StringBuilder sql = new StringBuilder().append("\n")
                    .append("UPDATE\n\t")
                    .append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(table.getSchema()))
                    .append(".")
                    .append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(table.getTable()))
                    .append("\nSET\n")
                    .append(properties.entrySet().stream()
                            .flatMap(e -> {
                                Stream.Builder<String> keys = Stream.<String> builder().add(e.getKey());
                                PropertyType pt = PropertyType.from(e.getValue());
                                String[] postfixes = sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(pt);
                                for (int i = 1; i < postfixes.length; ++i) {
                                    keys.add(e.getKey() + pt.getPostFixes()[i - 1]);
                                }
                                return keys.build();
                            })
                            .map(sqlgGraph.getSqlDialect()::maybeWrapInQoutes)
                            .map(k -> k + " = ?")
                            .collect(joining(",\n\t", "\t", "\n")))
                    .append("WHERE\n")
                    .append(distinctQueryStack.getFirst().constructSql(distinctQueryStack).split("\\bWHERE\\b", 2)[1]);

            LoggerFactory.getLogger(SqlgVertex.class).debug("{}", sql);

            Connection conn = sqlgGraph.tx().getConnection();
            try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                List<ImmutablePair<PropertyType, Object>> typeAndValues = SqlgUtil.transformToTypeAndValue(properties);
                int idx = SqlgUtil.setKeyValuesAsParameter(sqlgGraph, true, 1, preparedStatement, typeAndValues);
                SqlgUtil.setParametersOnStatement(sqlgGraph, distinctQueryStack, preparedStatement, idx);
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            distinctQueryStack.clear();
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
        return StringFactory.stepString(this, properties);
    }

}
