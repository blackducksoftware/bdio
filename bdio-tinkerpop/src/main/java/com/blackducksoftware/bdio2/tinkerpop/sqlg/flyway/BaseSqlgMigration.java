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

import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static com.blackducksoftware.common.base.ExtraThrowables.illegalState;
import static com.google.common.collect.MoreCollectors.toOptional;

import java.util.Arrays;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.umlg.sqlg.structure.SqlgGraph;

/**
 * A base class for implementing Flyway migrations against a Sqlg graph.
 *
 * @author jgustie
 */
public abstract class BaseSqlgMigration extends BaseJavaMigration {

    @Override
    public final boolean canExecuteInTransaction() {
        // Need to rely on Sqlg transactions, not the Flyway managed transaction
        return false;
    }

    @Override
    public final void migrate(Context context) throws Exception {
        Configuration configuration = context.getConfiguration();

        // Get the required SqlgCallbacks associated with the Flyway context
        SqlgCallback sqlgCallbacks = Arrays.stream(configuration.getCallbacks()).flatMap(ofType(SqlgCallback.class)).collect(toOptional())
                .orElseThrow(illegalState("Sqlg migrations require a registered instance of SqlgCallbacks"));

        // Open a graph and run the migration inside a transaction
        SqlgGraph sqlgGraph = sqlgCallbacks.open(configuration);
        try {
            sqlgGraph.tx().open();
            migrate(sqlgGraph, sqlgCallbacks);
            sqlgGraph.tx().commit();
        } catch (RuntimeException | Error e) {
            sqlgGraph.tx().rollback();
        } finally {
            sqlgGraph.close();
        }
    }

    /**
     * Execute a migration within a transaction using the Sqlg graph.
     */
    protected abstract void migrate(SqlgGraph sqlgGraph, SqlgCallback sqlgCallbacks);

}
