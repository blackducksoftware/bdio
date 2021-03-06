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

import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.tinkerpop.sqlg.flyway.FlywayBackport.BaseJavaMigration;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.flyway.FlywayBackport.Context;

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
        new SqlgFlywayExecutor(context.getConfiguration()).execute(this::migrate);
    }

    /**
     * Execute a migration within a transaction using the Sqlg graph.
     */
    protected abstract void migrate(SqlgGraph sqlgGraph) throws Exception;

}
