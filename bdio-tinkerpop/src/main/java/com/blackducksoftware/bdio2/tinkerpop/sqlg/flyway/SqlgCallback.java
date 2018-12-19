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

import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.sql.DataSource;

import org.apache.commons.configuration.MapConfiguration;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.jdbc.DriverDataSource;
import org.umlg.sqlg.SqlgPlugin;
import org.umlg.sqlg.sql.dialect.SqlDialect;
import org.umlg.sqlg.structure.SqlgDataSourceFactory;
import org.umlg.sqlg.structure.SqlgDataSourceFactory.SqlgDataSource;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.util.SqlgUtil;

import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIo;
import com.mchange.v2.c3p0.impl.DriverManagerDataSourceBase;

public class SqlgCallback implements Callback {

    /**
     * Extracts the JDBC URL from a data source.
     *
     * @throws IllegalArgumentException
     *             if the data source is not recognized
     * @see #getDefaultInstance()
     */
    public static String getJdbcUrl(DataSource dataSource) {
        try {
            // The Flyway data source isn't a wrapper, just access the URL directly
            if (dataSource instanceof DriverDataSource) {
                return ((DriverDataSource) dataSource).getUrl();
            }

            // Sqlg uses c3p0 as it's default data source implementation
            if (dataSource.isWrapperFor(DriverManagerDataSourceBase.class)) {
                return dataSource.unwrap(DriverManagerDataSourceBase.class).getJdbcUrl();
            }
        } catch (SQLException e) {
            // Ignore it, just take the same failure as if we didn't find anything
        }
        throw new IllegalArgumentException("unable to get JDBC URL from data source of type: " + dataSource.getClass().getName());
    }

    private final Function<DataSource, String> getJdbcUrl;

    private final Consumer<BlackDuckIo.Builder> configureBdio;

    private final List<TraversalStrategy<?>> strategies;

    public static SqlgCallback create(Consumer<BlackDuckIo.Builder> configureBdio, TraversalStrategy<?>... strategies) {
        return new SqlgCallback(SqlgCallback::getJdbcUrl, configureBdio, Arrays.asList(strategies));
    }

    protected SqlgCallback(Function<DataSource, String> getJdbcUrl, Consumer<BlackDuckIo.Builder> configureBdio, List<TraversalStrategy<?>> strategies) {
        this.getJdbcUrl = Objects.requireNonNull(getJdbcUrl);
        this.configureBdio = Objects.requireNonNull(configureBdio);
        this.strategies = Objects.requireNonNull(strategies);
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_CLEAN;
    }

    @Override
    public void handle(Event event, Context context) {
        switch (event) {
        case BEFORE_CLEAN:
            dropDb(context);
            break;
        default:
            break;
        }
    }

    /**
     * Returns a Sqlg graph based on the supplied Flyway configuration.
     */
    public SqlgGraph open(Configuration config) {
        return SqlgGraph.open(sqlgConfiguration(config), sqlgDataSourceFactory(config));
    }

    /**
     * Returns the BDIO configuration to use during migrations.
     */
    public BlackDuckIo.Builder bdio() {
        BlackDuckIo.Builder builder = BlackDuckIo.build();
        configureBdio.accept(builder);
        return builder;
    }

    /**
     * Returns all of the traversal strategies used to evaluate queries against the specified graph.
     */
    public List<TraversalStrategy<?>> getStrategies(SqlgGraph sqlgGraph) {
        return sqlgGraph.traversal()
                .withStrategies(strategies.toArray(new TraversalStrategy<?>[strategies.size()]))
                .getStrategies().toList();
    }

    /**
     * Drops the Sqlg schema and data.
     */
    protected void dropDb(Context context) {
        SqlDialect sqlDialect = sqlDialect(context.getConfiguration());
        SqlgUtil.dropDb(sqlDialect, context.getConnection());
    }

    /**
     * Returns a Sqlg configuration (Apache Commons) based on the supplied Flyway configuration.
     */
    protected org.apache.commons.configuration.Configuration sqlgConfiguration(Configuration configuration) {
        Map<String, Object> result = new LinkedHashMap<>();

        // The most important part is getting the JDBC URL since that is required by Sqlg
        result.put(SqlgGraph.JDBC_URL, getJdbcUrl.apply(configuration.getDataSource()));

        // Abuse the Flyway place holders as extra configuration parameters for Sqlg (and BDIO!)
        result.putAll(configuration.getPlaceholders());

        // Use the real type so we don't have to use fully qualified class names
        return new MapConfiguration(result);
    }

    /**
     * Return a Sqlg DataSource factory based on the supplied Flyway configuration.
     */
    protected SqlgDataSourceFactory sqlgDataSourceFactory(Configuration configuration) {
        class FlywayDataSource implements SqlgDataSource {
            @Override
            public DataSource getDatasource() {
                return configuration.getDataSource();
            }

            @Override
            public String getPoolStatsAsJson() {
                return "[{\"jdbcUrl\":\"" + getJdbcUrl.apply(getDatasource()) + "\",\"jndi\":false}]";
            }

            @Override
            public void close() {
                // Do not allow Sqlg to close the actual Flyway data source
            }
        }
        return (driver, config) -> new FlywayDataSource();
    }

    /**
     * Return a Sqlg dialect based on the supplied Flyway configuration.
     */
    protected SqlDialect sqlDialect(Configuration configuration) {
        // Mimic what Sqlg does to load a dialect
        String connectionUri = getJdbcUrl.apply(configuration.getDataSource());
        for (SqlgPlugin p : ServiceLoader.load(SqlgPlugin.class, SqlgGraph.class.getClassLoader())) {
            if (p.getDriverFor(connectionUri) != null) {
                return p.instantiateDialect();
            }
        }
        throw new IllegalStateException("Unable to determine Sqlg dialect to use for JDBC URL: " + connectionUri);
    }

}
