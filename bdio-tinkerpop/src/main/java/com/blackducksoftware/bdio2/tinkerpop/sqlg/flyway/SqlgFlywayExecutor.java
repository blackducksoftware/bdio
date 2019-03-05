/*
 * Copyright 2019 Synopsys, Inc.
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

import static com.blackducksoftware.common.base.ExtraObjects.cast;
import static com.blackducksoftware.common.base.ExtraThrowables.illegalState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.flywaydb.core.api.configuration.FlywayConfiguration;
import org.umlg.sqlg.structure.SqlgDataSourceFactory;
import org.umlg.sqlg.structure.SqlgGraph;

/**
 * An executor for running simple tasks in the context of a Sqlg graph.
 *
 * @author jgustie
 */
public class SqlgFlywayExecutor {

    /**
     * A task to run using a supplied Sqlg graph.
     */
    public interface SqlgTask {
        void run(SqlgGraph sqlgGraph) throws Exception;
    }

    /**
     * The Flyway configuration used to create the new graph.
     */
    private final FlywayConfiguration configuration;

    public SqlgFlywayExecutor(FlywayConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }

    /**
     * Executes the supplied task with a new Sqlg graph.
     */
    public void execute(SqlgTask task) throws Exception {
        SqlgGraph sqlgGraph = SqlgGraph.open(configure(configuration));
        sqlgGraph.tx().onClose(Transaction.CLOSE_BEHAVIOR.ROLLBACK);
        try {
            task.run(sqlgGraph);
        } finally {
            sqlgGraph.close();
        }
    }

    /**
     * Returns a Sqlg configuration based on the supplied Flyway configuration.
     */
    // Accept the private field as a parameter for better sub-classing
    protected Configuration configure(FlywayConfiguration flyway) {
        Map<String, Object> result = new LinkedHashMap<>();

        // The most important part is getting the JDBC URL since that is required by Sqlg
        result.put(SqlgGraph.JDBC_URL, JdbcUrlDataSource.unwrap(flyway.getDataSource()).getJdbcUrl());

        // Abuse the Flyway place holders as extra configuration parameters for Sqlg
        result.putAll(flyway.getPlaceholders());

        // WARNING: Sqlg does not support the "jdbc.factory" in 2.x (use "sqlg.dataSource" instead)
        result.put("jdbc.factory", FlywaySqlgDataSourceFactory.class.getName());
        result.put("flyway.dataSource", flyway.getDataSource());

        return new MapConfiguration(result);
    }

    /**
     * A Sqlg data source factory used to expose the Flyway data source.
     */
    public static final class FlywaySqlgDataSourceFactory implements SqlgDataSourceFactory {
        @Override
        public SqlgDataSource setup(String driver, Configuration configuration) throws Exception {
            return new SqlgDataSource() {
                @Override
                public DataSource getDatasource() {
                    return Optional.ofNullable(configuration.getProperty("flyway.dataSource"))
                            .map(cast(DataSource.class))
                            .orElseThrow(illegalState("unable to restore the data source"));
                }

                @Override
                public String getPoolStatsAsJson() {
                    return "[{\"jdbcUrl\":\"" + configuration.getString(SqlgGraph.JDBC_URL) + "\",\"jndi\":false}]";
                }

                @Override
                public void close() {
                    // Do not allow Sqlg to close the Flyway managed data source
                }
            };
        }
    }

}
