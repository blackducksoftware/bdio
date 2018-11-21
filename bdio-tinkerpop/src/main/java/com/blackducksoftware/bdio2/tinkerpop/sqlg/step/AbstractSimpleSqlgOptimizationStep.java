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

import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.tinkerpop.gremlin.process.traversal.Compare;
import org.apache.tinkerpop.gremlin.process.traversal.Contains;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.AbstractStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.ProfileStep;
import org.apache.tinkerpop.gremlin.process.traversal.util.FastNoSuchElementException;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalMetrics;
import org.umlg.sqlg.predicate.Existence;
import org.umlg.sqlg.sql.parse.SchemaTableTree;
import org.umlg.sqlg.step.SqlgGraphStep;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.AbstractLabel;

import com.blackducksoftware.common.base.ExtraThrowables;

/**
 * Base class for optimizations on very simple queries.
 * <p>
 * <em>WARNING!</em> This was written with exactly zero knowledge of how TinkerPop or Sqlg internals work. There is a
 * good chance that it is not safe for general use!
 *
 * @author jgustie
 */
public abstract class AbstractSimpleSqlgOptimizationStep<S> extends AbstractStep<S, S> {
    private static final long serialVersionUID = 1L;

    private final SqlgGraph sqlgGraph;

    private final Set<SchemaTableTree> rootSchemaTableTrees;

    protected AbstractSimpleSqlgOptimizationStep(Traversal.Admin<?, ?> traversal, SqlgGraphStep<?, ?> replacedStep) {
        super(traversal);
        sqlgGraph = traversal.getGraph().flatMap(ofType(SqlgGraph.class)).orElseThrow(illegalState("expected SqlgGraph"));
        rootSchemaTableTrees = replacedStep.parseForStrategy();
    }

    @Override
    protected Traverser.Admin<S> processNextStart() throws NoSuchElementException {
        if (!rootSchemaTableTrees.isEmpty()) {
            rootSchemaTableTrees.forEach(this::process);
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

    private void process(SchemaTableTree rootSchemaTableTree) {
        SchemaTable table = rootSchemaTableTree.getSchemaTable();
        Optional<? extends AbstractLabel> label;
        if (table.isVertexTable()) {
            label = sqlgGraph.getTopology().getVertexLabel(table.getSchema(), table.withOutPrefix().getTable());
        } else if (table.isEdgeTable()) {
            label = sqlgGraph.getTopology().getEdgeLabel(table.getSchema(), table.withOutPrefix().getTable());
        } else {
            label = Optional.empty();
        }
        process(sqlgGraph, rootSchemaTableTree, label.orElseThrow(illegalState("unknown table: %s", table)));
    }

    protected abstract void process(SqlgGraph sqlgGraph, SchemaTableTree rootSchemaTableTree, AbstractLabel label);

    /**
     * Generate a limited WHERE clause.
     */
    protected void appendWhereClause(StringBuilder sql, Map<String, PropertyType> propertyTypes, Iterable<HasContainer> hasContainers) {
        String prefix = "WHERE\n\t";
        for (HasContainer hasContainer : hasContainers) {
            sql.append(prefix);
            prefix = "\n\tAND ";
            PropertyType propertyType = Optional.ofNullable(propertyTypes.get(hasContainer.getKey()))
                    .orElseThrow(ExtraThrowables.illegalState("unable to determine property type for: ", hasContainer.getKey()));
            if (hasContainer.getBiPredicate() == Compare.eq) {
                sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(hasContainer.getKey()))
                        .append(" = ")
                        .append(sqlgGraph.getSqlDialect().valueToValuesString(propertyType, hasContainer.getValue()));
            } else if (hasContainer.getBiPredicate() == Compare.neq) {
                // NOTE: Sqlg probably uses "<>"
                sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(hasContainer.getKey()))
                        .append(" IS DISTINCT FROM ")
                        .append(sqlgGraph.getSqlDialect().valueToValuesString(propertyType, hasContainer.getValue()));
            } else if (hasContainer.getBiPredicate() == Existence.NULL) {
                sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(hasContainer.getKey()))
                        .append(" IS NULL");
            } else if (hasContainer.getBiPredicate() == Existence.NOTNULL) {
                sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(hasContainer.getKey()))
                        .append(" IS NOT NULL");
            } else if (hasContainer.getBiPredicate() == Contains.within) {
                sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(hasContainer.getKey()));
                Collection<?> values = (Collection<?>) hasContainer.getValue();
                if (values.size() == 1) {
                    sql.append(" = ")
                            .append(sqlgGraph.getSqlDialect().valueToValuesString(propertyType, values.iterator().next()));
                } else {
                    // NOTE: Sqlg probably generates a VALUES table to join against
                    sql.append(" IN ").append(values.stream()
                            .map(v -> sqlgGraph.getSqlDialect().valueToValuesString(propertyType, v))
                            .collect(Collectors.joining(", ", "(", ")")));
                }
            } else {
                throw new IllegalStateException("unsupported hasContainer: " + hasContainer);
            }
        }
    }

}
