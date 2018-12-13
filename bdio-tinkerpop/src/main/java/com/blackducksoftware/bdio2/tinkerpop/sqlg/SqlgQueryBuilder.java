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
package com.blackducksoftware.bdio2.tinkerpop.sqlg;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.umlg.sqlg.sql.dialect.PostgresDialect;
import org.umlg.sqlg.sql.dialect.SqlDialect;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;

/**
 * Helper for building Sqlg queries using the Sqlg graph provided SQL dialect.
 * 
 * @author jgustie
 */
public class SqlgQueryBuilder {

    private final SqlDialect dialect;

    private final StringBuilder sql = new StringBuilder();

    public SqlgQueryBuilder(SqlDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect);
    }

    public SqlgQueryBuilder append(CharSequence cs) {
        sql.append(cs);
        return this;
    }

    public SqlgQueryBuilder append(char c) {
        sql.append(c);
        return this;
    }

    public SqlgQueryBuilder maybeWrapInQuotes(CharSequence cs) {
        sql.append(dialect.maybeWrapInQoutes(cs.toString()));
        return this;
    }

    public SqlgQueryBuilder qualify(CharSequence a, CharSequence b) {
        return maybeWrapInQuotes(a).append('.').maybeWrapInQuotes(b);
    }

    public SqlgQueryBuilder schemaTable(SchemaTable table) {
        return qualify(table.getSchema(), table.getTable());
    }

    public SqlgQueryBuilder semicolon() {
        if (dialect.needsSemicolon()) {
            sql.append(';');
        }
        return this;
    }

    public SqlgQueryBuilder valueToValuesString(PropertyType propertyType, Object value) {
        sql.append(dialect.valueToValuesString(propertyType, value));
        return this;
    }

    public SqlgQueryBuilder valueToValuesString(Object value) {
        return valueToValuesString(PropertyType.from(value), value);
    }

    public static void main(String[] args) {
        System.out.println(new SqlgQueryBuilder(new PostgresDialect()).in("foo", Arrays.asList("a", "b"), PropertyType.STRING));
    }

    public SqlgQueryBuilder in(String column, Iterable<? extends Object> values, PropertyType propertyType) {
        Iterator<? extends Object> v = values.iterator();
        if (v.hasNext()) {
            maybeWrapInQuotes(column);
            Object value = v.next();
            if (v.hasNext()) {
                append(" IN (").valueToValuesString(propertyType, value);
                while (v.hasNext()) {
                    append(", ").valueToValuesString(propertyType, v.next());
                }
                append(")");
            } else {
                append(" = ").valueToValuesString(propertyType, value);
            }
        }
        return this;
    }

    public <T> SqlgQueryBuilder forEachAppend(Stream<T> elements, BiConsumer<T, SqlgQueryBuilder> action, String delimiter, String prefix, String suffix) {
        AtomicBoolean first = new AtomicBoolean(true);
        elements.forEach(t -> {
            if (first.getAndSet(false)) {
                append(prefix);
            } else {
                append(delimiter);
            }
            action.accept(t, this);
        });
        if (!first.get()) {
            append(suffix);
        }
        return this;
    }

    @Override
    public String toString() {
        return sql.toString();
    }

}
