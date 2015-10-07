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
 * A component in a Bill of Materials.
 *
 * @author jgustie
 */
public class Component extends AbstractModel<Component> {

    @Nullable
    private String name;

    private static final ModelField<Component> NAME = new ModelField<Component>(DoapTerm.NAME) {
        @Override
        protected Object get(Component component) {
            return component.getName();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setName(valueToString(value));
        }
    };

    @Nullable
    private String version;

    private static final ModelField<Component> VERSION = new ModelField<Component>(DoapTerm.REVISION) {
        @Override
        protected Object get(Component component) {
            return component.getVersion();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setVersion(valueToString(value));
        }
    };

    @Nullable
    private String homepage;

    private static final ModelField<Component> HOMEPAGE = new ModelField<Component>(DoapTerm.HOMEPAGE) {
        @Override
        protected Object get(Component component) {
            return component.getHomepage();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setHomepage(valueToString(value));
        }
    };

    @Nullable
    private String license;

    private static final ModelField<Component> LICENSE = new ModelField<Component>(DoapTerm.LICENSE) {
        @Override
        protected Object get(Component component) {
            return component.getLicense();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setLicense(valueToString(value));
        }
    };

    public Component() {
        super(BlackDuckType.COMPONENT,
                NAME, VERSION, HOMEPAGE, LICENSE);
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

    @Nullable
    public String getHomepage() {
        return homepage;
    }

    public void setHomepage(@Nullable String homepage) {
        this.homepage = homepage;
    }

    @Nullable
    public String getLicense() {
        return license;
    }

    public void setLicense(@Nullable String license) {
        this.license = license;
    }

}
