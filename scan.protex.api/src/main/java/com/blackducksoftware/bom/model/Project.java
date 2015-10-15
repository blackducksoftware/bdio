/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bom.model;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.DoapTerm;

/**
 * A project in a Bill of Materials.
 *
 * @author jgustie
 */
public class Project extends AbstractTopLevelModel<Project> {

    /**
     * The name of this project.
     */
    @Nullable
    private String name;

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

    /**
     * The version name of this project.
     */
    @Nullable
    private String version;

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

    public Project() {
        super(BlackDuckType.PROJECT,
                NAME, VERSION);
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
