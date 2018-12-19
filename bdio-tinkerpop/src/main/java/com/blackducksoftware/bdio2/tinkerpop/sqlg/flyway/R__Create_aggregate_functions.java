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

import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * One of the optimizations groups rows by their "@id" value, then takes the first value from each group using custom
 * aggregate functions. This migration creates those functions.
 *
 * @author jgustie
 */
public class R__Create_aggregate_functions extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.executeUpdate("CREATE OR REPLACE FUNCTION public.first_agg ( anyelement, anyelement )"
                    + "\nRETURNS anyelement LANGUAGE SQL IMMUTABLE STRICT AS $$"
                    + "\n\tSELECT $1;"
                    + "\n$$;");
            statement.executeUpdate("DROP AGGREGATE IF EXISTS public.first ( anyelement );");
            statement.executeUpdate("CREATE AGGREGATE public.first( sfunc = first_agg, stype = anyelement, basetype = anyelement );");
        }
    }

}
