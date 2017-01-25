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
package com.blackducksoftware.bdio2.tinkerpop;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.blackducksoftware.bdio2.Bdio;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableSet;

/**
 * Representation of a JSON-LD frame used to convert linked data into graph data.
 *
 * @author jgustie
 */
public final class BdioFrame extends AbstractMap<String, Object> implements Cloneable {

    /**
     * The actual JSON-LD frame. This value is used to back the map implementation.
     */
    private final Map<String, Object> frame;

    /**
     * The list of known type names.
     */
    private final Set<String> typeNames;

    /**
     * The list of known data property names.
     */
    private final Set<String> dataPropertyNames;

    /**
     * The list of known object property names.
     */
    private final Set<String> objectPropertyNames;

    private BdioFrame(Map<String, Object> frame, Set<String> typeNames, Set<String> dataPropertyNames, Set<String> objectPropertyNames) {
        this.frame = Objects.requireNonNull(frame);
        this.typeNames = ImmutableSet.copyOf(typeNames);
        this.dataPropertyNames = ImmutableSet.copyOf(dataPropertyNames);
        this.objectPropertyNames = ImmutableSet.copyOf(objectPropertyNames);
    }

    /**
     * Creates a new JSON-LD frame for converting BDIO data into graph data.
     */
    public static BdioFrame create(Map<String, Object> initialContext) {
        // This seems like it should be a builder...
        Map<String, Object> context = new LinkedHashMap<>();
        List<String> type = new ArrayList<>();
        Set<String> typeNames = new LinkedHashSet<>();
        Set<String> dataPropertyNames = new LinkedHashSet<>();
        Set<String> objectPropertyNames = new LinkedHashSet<>();

        // Application specific entries to the context
        for (Map.Entry<String, Object> entry : initialContext.entrySet()) {
            if (entry.getValue() instanceof String) {
                context.put(entry.getKey(), entry.getValue());
            } else if (entry.getValue() instanceof Map<?, ?>) {
                Map<?, ?> definition = (Map<?, ?>) entry.getValue();
                Object id = definition.get(JsonLdConsts.ID);
                if (id != null) {
                    context.put(entry.getKey(), id);
                }
                if (Objects.equals(definition.get(JsonLdConsts.TYPE), JsonLdConsts.ID)) {
                    objectPropertyNames.add(entry.getKey());
                } else {
                    dataPropertyNames.add(entry.getKey());
                }
            }
        }

        // Standard BDIO
        for (Bdio.Class bdioClass : Bdio.Class.values()) {
            context.put(bdioClass.name(), bdioClass.toString());
            type.add(bdioClass.toString());
            typeNames.add(bdioClass.name());
        }
        for (Bdio.DataProperty bdioDataProperty : Bdio.DataProperty.values()) {
            context.put(bdioDataProperty.name(), bdioDataProperty.toString());
            dataPropertyNames.add(bdioDataProperty.name());
        }
        for (Bdio.ObjectProperty bdioObjectProperty : Bdio.ObjectProperty.values()) {
            context.put(bdioObjectProperty.name(), bdioObjectProperty.toString());
            objectPropertyNames.add(bdioObjectProperty.name());
        }

        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put(JsonLdConsts.CONTEXT, context);
        frame.put(JsonLdConsts.TYPE, type);
        return new BdioFrame(frame, typeNames, dataPropertyNames, objectPropertyNames);
    }

    /**
     * Checks to see if the specified key represents a data property.
     */
    public boolean isDataPropertyKey(String key) {
        return dataPropertyNames.contains(key);
    }

    /**
     * Checks to see if the specified key represents an object property.
     */
    public boolean isObjectPropertyKey(String key) {
        return objectPropertyNames.contains(key);
    }

    /**
     * Iterates over the known type names.
     */
    public void forEachTypeName(Consumer<String> typeNameConsumer) {
        typeNames.forEach(typeNameConsumer);
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        // Make this instance look like the `frame` field
        return frame.entrySet();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        // Just accept the shallow clone from the super; this is just here to make the JSON-LD API happy
        return super.clone();
    }

}
