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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.sql.Connection;
import java.util.ServiceLoader;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.BaseFlywayCallback;
import org.flywaydb.core.api.configuration.FlywayConfiguration;
import org.flywaydb.core.internal.util.TimeFormat;
import org.flywaydb.core.internal.util.logging.Log;
import org.flywaydb.core.internal.util.logging.LogFactory;
import org.umlg.sqlg.SqlgPlugin;
import org.umlg.sqlg.sql.dialect.SqlDialect;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.util.SqlgUtil;

import com.blackducksoftware.bdio2.tinkerpop.sqlg.flyway.FlywayBackport.Context;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.flyway.FlywayBackport.Event;
import com.google.common.base.Stopwatch;

/**
 * Sqlg specific behavior to include with Flyway.
 *
 * @author jgustie
 */
public class SqlgCallback extends BaseFlywayCallback {

    // Helper to backport Flyway 5.x code
    private class BackportContext extends Context {
        private BackportContext(Connection connection) {
            super(connection);
        }

        @Override
        public FlywayConfiguration getConfiguration() {
            return flywayConfiguration;
        }
    }

    private static final Log LOG = LogFactory.getLog(SqlgCallback.class);

    @Override
    public void beforeClean(Connection connection) {
        handle(Event.BEFORE_CLEAN, new BackportContext(connection));
    }

    @Override
    public void beforeMigrate(Connection connection) {
        handle(Event.BEFORE_MIGRATE, new BackportContext(connection));
    }

    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    public boolean supports(Event event, Context context) {
        // TODO Support verify/validation as well?
        return event == Event.BEFORE_CLEAN || event == Event.BEFORE_MIGRATE;
    }

    public void handle(Event event, Context context) {
        Stopwatch timer = Stopwatch.createStarted();
        if (event == Event.BEFORE_CLEAN) {
            // Use Sqlg to remove the graph before Flyway wipes out everything else
            dropDb(context);
            LOG.info("Successfully cleaned Sqlg topology (execution time " + TimeFormat.format(timer.stop().elapsed(MILLISECONDS)) + ")");
        } else if (event == Event.BEFORE_MIGRATE) {
            // Creates a new Sqlg graph to eagerly create a schema
            // TODO Can we skip opening the graph is there is no schema to create?
            try {
                executor(context.getConfiguration()).execute(sqlgGraph -> {
                    topologyEagerCreation(sqlgGraph.getTopology());
                    sqlgGraph.tx().commit();
                });
                LOG.info("Successfully created Sqlg topology (execution time " + TimeFormat.format(timer.stop().elapsed(MILLISECONDS)) + ")");
            } catch (Exception e) {
                throw new FlywayException("Sqlg topology creation failed", e);
            }
        }
    }

    /**
     * Drops the Sqlg schema and data.
     */
    protected void dropDb(Context context) {
        SqlgUtil.dropDb(sqlDialect(context.getConfiguration()), context.getConnection());
    }

    /**
     * The Sqlg schema is dynamic but can be eagerly created. In the context of Flyway migration the eager creation
     * should happen before migration starts to ensure database entities referenced by the migration scripts exist.
     */
    protected void topologyEagerCreation(Topology topology) {
        // Default is to do nothing
    }

    /**
     * Return a Sqlg dialect based on the supplied Flyway configuration.
     */
    protected SqlDialect sqlDialect(FlywayConfiguration configuration) {
        // Mimic what Sqlg does to load a dialect
        String connectionUri = JdbcUrlDataSource.unwrap(configuration.getDataSource()).getJdbcUrl();
        for (SqlgPlugin p : ServiceLoader.load(SqlgPlugin.class, SqlgGraph.class.getClassLoader())) {
            if (p.getDriverFor(connectionUri) != null) {
                return p.instantiateDialect();
            }
        }
        throw new IllegalStateException("Unable to determine Sqlg dialect to use for JDBC URL: " + connectionUri);
    }

    /**
     * Create a new Sqlg graph executor for the supplied Flyway configuration.
     */
    protected SqlgFlywayExecutor executor(FlywayConfiguration configuration) {
        return new SqlgFlywayExecutor(configuration);
    }

}
