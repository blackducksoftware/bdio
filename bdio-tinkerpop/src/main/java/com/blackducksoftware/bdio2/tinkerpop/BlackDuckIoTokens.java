/*
 * Copyright 2018 Synopsys, Inc.
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
package com.blackducksoftware.bdio2.tinkerpop;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Definition of customizable tokens used throughout BDIO graph processing.
 *
 * @author jgustie
 */
public interface BlackDuckIoTokens {

    /**
     * The mapping of vertex labels to {@code @type} IRIs. Does not include the metadata vertex label.
     */
    default Map<String, String> classes() {
        return Collections.emptyMap();
    }

    /**
     * The vertex labels for classes that should be embedded.
     */
    default Set<String> embeddedClasses() {
        return Collections.emptySet();
    }

    /**
     * The mapping of vertex property names or terms to IRIs.
     */
    default Map<String, String> dataProperties() {
        return Collections.emptyMap();
    }

    /**
     * The property keys for terms that are multi-valued.
     */
    default Set<String> multiDataValueProperties() {
        return Collections.emptySet();
    }

    /**
     * The mapping of vertex property names or terms to IRIs.
     */
    default Map<String, String> objectProperties() {
        return Collections.emptyMap();
    }

    /**
     * The vertex label used to persist JSON-LD named graph metadata.
     */
    @Nullable
    default String metadataLabel() {
        return null;
    }

    /**
     * The edge label used to connect metadata to a root vertex.
     */
    @Nullable
    default String rootLabel() {
        return null;
    }

    /**
     * The property key used to persist JSON-LD node identifiers.
     */
    // TODO Remove this in favor of an ElementIdStrategy that generates random "urn:uuid:..." URIs
    @Nullable
    default String identifierKey() {
        return null;
    }

    /**
     * The property key used to persist "unknown" JSON-LD node properties.
     */
    @Nullable
    default String unknownKey() {
        return null;
    }

    /**
     * The property key used to persist a flag indicating implicit creation of missing BDIO data.
     */
    @Nullable
    default String implicitKey() {
        return null;
    }

}
