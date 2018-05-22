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
package com.blackducksoftware.bdio2.tinkerpop.test;

import org.apache.commons.configuration.Configuration;
import org.umlg.sqlg.structure.ds.C3P0DataSource;
import org.umlg.sqlg.structure.ds.C3p0DataSourceFactory;

import com.mchange.v2.c3p0.ComboPooledDataSource;

/**
 * Apply some additional configuration to the data source for testing.
 *
 * @author jgustie
 */
public class TestSqlgDataSourceFactory extends C3p0DataSourceFactory {

    @Override
    public C3P0DataSource setup(String driver, Configuration configuration) throws Exception {
        C3P0DataSource result = (C3P0DataSource) super.setup(driver, configuration);
        ComboPooledDataSource dss = (ComboPooledDataSource) result.getDatasource();

        dss.setAcquireRetryAttempts(1);

        return result;
    }

}
