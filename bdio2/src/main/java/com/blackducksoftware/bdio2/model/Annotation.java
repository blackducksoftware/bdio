/*
 * Copyright 2018 Black Duck Software, Inc.
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

import java.time.ZonedDateTime;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioObject;

public class Annotation extends BdioObject {

    public Annotation() {
        super(Bdio.Class.Annotation);
    }

    public Annotation comment(@Nullable String comment) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.comment, comment);
        return this;
    }

    public Annotation creator(@Nullable String creator) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.creator, creator);
        return this;
    }

    public Annotation creationDateTime(@Nullable ZonedDateTime creationDateTime) {
        BdioContext.getActive().putFieldValue(this, Bdio.DataProperty.creationDateTime, creationDateTime);
        return this;
    }

}
