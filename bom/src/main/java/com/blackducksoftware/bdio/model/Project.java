/*
 * Copyright 2015 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio.model;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio.BlackDuckType;
import com.blackducksoftware.bdio.DoapTerm;
import com.google.common.collect.ImmutableSet;

/**
 * A project in a Bill of Materials.
 *
 * @author jgustie
 */
public class Project extends AbstractTopLevelModel<Project> {
    private static final ModelField<Project, String> NAME = new ModelField<Project, String>(DoapTerm.NAME) {
        @Override
        protected String get(Project project) {
            return project.getName();
        }

        @Override
        protected void set(Project project, Object value) {
            project.setName(valueToString(value));
        }
    };

    private static final ModelField<Project, String> VERSION = new ModelField<Project, String>(DoapTerm.REVISION) {
        @Override
        protected String get(Project project) {
            return project.getVersion();
        }

        @Override
        protected void set(Project project, Object value) {
            project.setVersion(valueToString(value));
        }
    };

    /**
     * The name of this project.
     */
    @Nullable
    private String name;

    /**
     * The version name of this project.
     */
    @Nullable
    private String version;

    public Project() {
        super(BlackDuckType.PROJECT,
                ImmutableSet.<ModelField<Project, ?>> of(NAME, VERSION));
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Nullable
    public String getVersion() {
        return version;
    }

    public void setVersion(@Nullable String version) {
        this.version = version;
    }

}
