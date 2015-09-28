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
public class Project extends AbstractModel<Project> {

    /**
     * The name of this project.
     */
    @Nullable
    private String name;

    private static final ModelField<Project> NAME = new ModelField<Project>(DoapTerm.NAME) {
        @Override
        protected Object get(Project project) {
            return project.getName();
        }

        @Override
        protected void set(Project project, Object value) {
            project.setName(valueToString(value));
        }
    };

    public Project() {
        super(BlackDuckType.PROJECT,
                NAME);
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

}
