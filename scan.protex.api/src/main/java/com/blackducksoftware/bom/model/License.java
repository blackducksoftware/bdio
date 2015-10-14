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
import com.blackducksoftware.bom.SpdxTerm;

/**
 * A license in a Bill of Materials.
 *
 * @author jgustie
 */
public class License extends AbstractTopLevelModel<License> {

    /**
     * The name of the license.
     */
    @Nullable
    private String name;

    private static final ModelField<License, String> NAME = new ModelField<License, String>(SpdxTerm.NAME) {
        @Override
        protected String get(License license) {
            return license.getName();
        }

        @Override
        protected void set(License license, Object value) {
            license.setName(valueToString(value));
        }
    };

    public License() {
        super(BlackDuckType.LICENSE,
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
