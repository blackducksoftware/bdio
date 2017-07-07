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

public final class Note extends BdioObject {

    public Note() {
        super(Bdio.Class.Note);
    }

    public Note range(@Nullable String range) {
        putData(Bdio.DataProperty.range, range);
        return this;
    }

    public Note copyrightYear(@Nullable String copyrightYear) {
        putData(Bdio.DataProperty.copyrightYear, copyrightYear);
        return this;
    }

    public Note rightsHolder(@Nullable String rightsHolder) {
        putData(Bdio.DataProperty.rightsHolder, rightsHolder);
        return this;
    }

}
