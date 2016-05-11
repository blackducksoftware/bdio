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
package com.blackducksoftware.bom.model;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.SpdxTerm;
import com.google.common.collect.ImmutableSet;

/**
 * A license in a Bill of Materials.
 *
 * @author jgustie
 */
public class License extends AbstractTopLevelModel<License> {
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

    /**
     * The name of the license.
     */
    @Nullable
    private String name;

    public License() {
        super(BlackDuckType.LICENSE,
                ImmutableSet.<ModelField<License, ?>> of(NAME));
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }
}
