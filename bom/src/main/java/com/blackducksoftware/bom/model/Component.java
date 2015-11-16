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
public class Component extends AbstractTopLevelModel<Component> {
    private static final ModelField<Component, String> NAME = new ModelField<Component, String>(DoapTerm.NAME) {
        @Override
        protected String get(Component component) {
            return component.getName();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setName(valueToString(value));
        }
    };

    private static final ModelField<Component, String> VERSION = new ModelField<Component, String>(DoapTerm.REVISION) {
        @Override
        protected String get(Component component) {
            return component.getVersion();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setVersion(valueToString(value));
        }
    };

    private static final ModelField<Component, String> HOMEPAGE = new ModelField<Component, String>(DoapTerm.HOMEPAGE) {
        @Override
        protected String get(Component component) {
            return component.getHomepage();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setHomepage(valueToString(value));
        }
    };

    private static final ModelField<Component, String> LICENSE = new ModelField<Component, String>(DoapTerm.LICENSE) {
        @Override
        protected String get(Component component) {
            return component.getLicense();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setLicense(valueToString(value));
        }
    };

    @Nullable
    private String name;

    @Nullable
    private String version;

    @Nullable
    private String homepage;

    @Nullable
    private String license;

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

    public boolean isVersion() {
        return ((name != null) && (version != null));
    }
}
