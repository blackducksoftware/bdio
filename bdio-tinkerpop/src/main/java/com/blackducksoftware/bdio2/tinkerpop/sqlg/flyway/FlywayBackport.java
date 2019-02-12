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

import java.sql.Connection;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.FlywayCallback;
import org.flywaydb.core.api.configuration.FlywayConfiguration;

/**
 * Helper code to backport code written against the Flyway 5.x API.
 *
 * @author jgustie
 */
public class FlywayBackport {

    /**
     * Helper to backport Flyway callbacks.
     */
    public enum Event {
        BEFORE_CLEAN, BEFORE_MIGRATE
    }

    /**
     * Helper to backport code that calls {@code Flyway.configure()} against the 5.x API.
     */
    public static FlywayConfigure configure() {
        return new FlywayConfigure();
    }

    public static class FlywayConfigure {
        private final Flyway flyway = new Flyway();

        private FlywayConfigure() {
        }

        public FlywayConfigure dataSource(DataSource dataSource) {
            flyway.setDataSource(dataSource);
            return this;
        }

        public FlywayConfigure callbacks(FlywayCallback... callbacks) {
            flyway.setCallbacks(callbacks);
            return this;
        }

        public FlywayConfigure installedBy(String installedBy) {
            flyway.setInstalledBy(installedBy);
            return this;
        }

        public FlywayConfigure locations(String... locations) {
            flyway.setLocations(locations);
            return this;
        }

        public FlywayConfigure placeholders(Map<String, String> placeholders) {
            flyway.setPlaceholders(placeholders);
            return this;
        }

        public FlywayConfigure baselineVersion(String baselineVersion) {
            flyway.setBaselineVersionAsString(baselineVersion);
            return this;
        }

        public FlywayConfigure baselineOnMigrate(boolean baselineOnMigrate) {
            flyway.setBaselineOnMigrate(baselineOnMigrate);
            return this;
        }

        public FlywayConfigure validateOnMigrate(boolean validateOnMigrate) {
            flyway.setValidateOnMigrate(validateOnMigrate);
            return this;
        }

        public Flyway load() {
            return flyway;
        }
    }

    /**
     * A context object for backporting the deprecation of "configuration aware".
     */
    public static abstract class Context {
        private final Connection connection;

        public Context(Connection connection) {
            this.connection = Objects.requireNonNull(connection);
        }

        public abstract FlywayConfiguration getConfiguration();

        public Connection getConnection() {
            return connection;
        }
    }

    /**
     * Non-functional backport of a class from the Flyway 5.x API.
     */
    public static abstract class BaseJavaMigration {

        public abstract boolean canExecuteInTransaction();

        public abstract void migrate(Context context) throws Exception;
    }

}
