/*
 * Copyright 2017 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2.model;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;

public class LicenseGroup extends BdioObject {

    public LicenseGroup() {
        super(Bdio.Class.LicenseGroup);
    }

    public LicenseGroup license(@Nullable License license) {
        putObject(Bdio.ObjectProperty.license, license);
        return this;
    }

    public LicenseGroup licenseConjunctive(@Nullable License license) {
        putObject(Bdio.ObjectProperty.licenseConjunctive, license);
        return this;
    }

    public LicenseGroup licenseDisjunctive(@Nullable License license) {
        putObject(Bdio.ObjectProperty.licenseDisjunctive, license);
        return this;
    }

    public LicenseGroup licenseOrLater(@Nullable License license) {
        putObject(Bdio.ObjectProperty.licenseOrLater, license);
        return this;
    }

}
