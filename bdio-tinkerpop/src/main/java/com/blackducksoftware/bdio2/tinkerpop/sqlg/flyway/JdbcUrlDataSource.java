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

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Wrapper;
import java.util.Objects;
import java.util.function.Function;

import javax.sql.DataSource;

import org.flywaydb.core.internal.jdbc.DriverDataSource;

import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Reflection;
import com.mchange.v2.c3p0.impl.DriverManagerDataSourceBase;

/**
 * An interface for exposing the JDBC URL of a data source. Most data source implementations won't implement this
 * directly so it is necessary to use the {@link JdbcUrlDataSource#wrapWithJdbcUrl(DataSource, Function)} method to wrap
 * the actual data source.
 *
 * @author jgustie
 */
public interface JdbcUrlDataSource {

    /**
     * Returns the JDBC URL for this data source.
     */
    String getJdbcUrl();

    /**
     * Wraps the supplied data source to allow the JDBC URL to be extracted.
     */
    public static <D extends DataSource> DataSource wrapWithJdbcUrl(D dataSource, Function<D, String> jdbcUrl) {
        JdbcUrlDataSource jdbcUrlDataSource = () -> jdbcUrl.apply(dataSource);
        return Reflection.newProxy(DataSource.class, new AbstractInvocationHandler() {
            @Override
            protected Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getDeclaringClass().equals(Wrapper.class)) {
                    Class<?> iface = (Class<?>) args[0];
                    if (Objects.equals(iface, JdbcUrlDataSource.class)) {
                        return method.getReturnType().equals(Boolean.TYPE) ? true : jdbcUrlDataSource;
                    } else if (iface.isInstance(dataSource)) {
                        return method.getReturnType().equals(Boolean.TYPE) ? true : dataSource;
                    }
                }
                return method.invoke(dataSource, args);
            }
        });
    }

    /**
     * Extracts the JDBC URL from a data source.
     *
     * @throws IllegalArgumentException
     *             if the data source is not recognized
     */
    public static JdbcUrlDataSource unwrap(DataSource dataSource) {
        try {
            if (dataSource instanceof DriverDataSource) {
                // Additional support for the built-in Flyway data source
                return ((DriverDataSource) dataSource)::getUrl;
            } else if (dataSource.isWrapperFor(DriverManagerDataSourceBase.class)) {
                // Sqlg uses c3p0 as it's default data source implementation
                return dataSource.unwrap(DriverManagerDataSourceBase.class)::getJdbcUrl;
            } else {
                // Do an actual unwrap
                return dataSource.unwrap(JdbcUrlDataSource.class);
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException("unable to get JDBC URL from data source of type: " + dataSource.getClass().getName());
        }
    }

}
