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

import com.blackducksoftware.bdio.Type;

/**
 * Base class for embedded models. Embedded models are used as values for properties on other models.
 *
 * @author jgustie
 */
public abstract class AbstractEmbeddedModel<M extends AbstractEmbeddedModel<M>> extends AbstractModel<M> {

    protected AbstractEmbeddedModel(Type type, Iterable<ModelField<M, ?>> fields) {
        super(type, fields);
    }

}