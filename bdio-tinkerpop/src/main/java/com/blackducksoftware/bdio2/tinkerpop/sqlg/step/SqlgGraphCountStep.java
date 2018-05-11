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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.AbstractStep;
import org.apache.tinkerpop.gremlin.process.traversal.util.FastNoSuchElementException;
import org.umlg.sqlg.sql.parse.SchemaTableTree;
import org.umlg.sqlg.step.SqlgGraphStep;
import org.umlg.sqlg.strategy.SqlgSqlExecutor;
import org.umlg.sqlg.structure.SqlgGraph;

import com.google.common.base.Throwables;

/**
 * This is supposed to prevent a simple {@code g.V().hasLabel("foo").count().next()} query from actually transferring
 * all of the vertices.
 * <p>
 * <em>WARNING!</em> This was written with exactly zero knowledge of how TinkerPop or Sqlg internals work. There is a
 * good chance that it is not safe for general use!
 *
 * @author jgustie
 */
public class SqlgGraphCountStep extends AbstractStep<Long, Long> {
    private static final long serialVersionUID = 1L;

    private final SqlgGraph sqlgGraph;

    private final LinkedList<SchemaTableTree> distinctQueryStack = new LinkedList<>();

    public SqlgGraphCountStep(Traversal.Admin<?, ?> traversal, SqlgGraphStep<?, ?> replacedStep) {
        super(traversal);
        sqlgGraph = traversal.getGraph().flatMap(ofType(SqlgGraph.class)).orElseThrow(illegalState("expected SqlgGraph"));
        distinctQueryStack.add(replacedStep.parseForStrategy().stream().collect(onlyElement()));
    }

    @Override
    protected Traverser.Admin<Long> processNextStart() {
        if (!distinctQueryStack.isEmpty()) {
            try {
                String sql = "\nSELECT\n\tcount(1)\nFROM" + distinctQueryStack.getFirst().constructSql(distinctQueryStack).split("\\bFROM\\b", 2)[1];
                ResultSet resultSet = executeQuery(sqlgGraph, sql, distinctQueryStack).getLeft();
                if (resultSet.next()) {
                    distinctQueryStack.clear();
                    Long count = resultSet.getLong(1);
                    starts.add(getTraversal().getTraverserGenerator().generate(count, this, 1L));
                    return starts.next();
                }
            } catch (SQLException e) {
                throw (NoSuchElementException) new NoSuchElementException("optimized count failed").initCause(e);
            }
        }
        throw FastNoSuchElementException.instance();
    }

    // Reflective hack to invoke Sqlg's {@code executeQuery} method with our own SQL
    @SuppressWarnings("unchecked")
    private static Triple<ResultSet, ResultSetMetaData, PreparedStatement> executeQuery(SqlgGraph sqlgGraph, String sql,
            LinkedList<SchemaTableTree> distinctQueryStack) {
        try {
            Method m = SqlgSqlExecutor.class.getDeclaredMethod("executeQuery", SqlgGraph.class, String.class, LinkedList.class);
            m.setAccessible(true);
            return (Triple<ResultSet, ResultSetMetaData, PreparedStatement>) m.invoke(null, sqlgGraph, sql, distinctQueryStack);
        } catch (InvocationTargetException e) {
            Throwables.throwIfUnchecked(e.getCause());
            throw new RuntimeException("failed to execute query", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("failed to execute query", e);
        }
    }
}
