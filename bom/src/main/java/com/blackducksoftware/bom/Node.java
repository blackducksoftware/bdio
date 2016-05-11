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
package com.blackducksoftware.bom;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Primary unit of output in a serialized Bill of Materials.
 *
 * @author jgustie
 */
public interface Node {

    /**
     * The unique node identifier. Must be a valid IRI.
     */
    @Nullable
    String id();

    /**
     * The set of types this node conforms to. May be empty.
     */
    @Nonnull
    Set<Type> types();

    /**
     * The data payload of this node, expressed as a mapping of terms to unspecified object values. May be empty.
     */
    @Nonnull
    Map<Term, Object> data();

}
