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
                ImmutableSet.<ModelField<Component, ?>> of(NAME, VERSION, HOMEPAGE, LICENSE));
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
