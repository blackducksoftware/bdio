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
package com.blackducksoftware.bdio2.tinkerpop.sqlg;

import java.sql.Connection;

import org.umlg.sqlg.sql.dialect.PostgresDialect;
import org.umlg.sqlg.sql.dialect.SqlDialect;

/**
 * A workaround for Sqlg #251 for versions prior to 2.0.0.
 * <p>
 * Note that this workaround is at the mercy of the {@code ServiceLoader}; more specifically, in order for this to work,
 * the {@code META-INF/services/org.umlg.sqlg.SqlgPlugin} resource from this JAR must be found by the class loader
 * of the {@code SqlgGraph} class before the real resource in the Sqlg Postgres dialect JAR.
 *
 * @author jgustie
 */
public class PostgresPlugin extends org.umlg.sqlg.PostgresPlugin {
    @Override
    public SqlDialect instantiateDialect() {
        class PostgresDialect251 extends PostgresDialect {
            @Override
            public void prepareDB(Connection conn) {
                try {
                    super.prepareDB(conn);
                } catch (Exception e) {
                    // This is the behavior used in Sqlg 2.x
                }
            }
        }
        return new PostgresDialect251();
    }
}
