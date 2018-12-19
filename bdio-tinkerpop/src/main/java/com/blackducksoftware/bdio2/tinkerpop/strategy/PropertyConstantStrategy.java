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
package com.blackducksoftware.bdio2.tinkerpop.strategy;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal.Admin;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.step.Mutating;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.AddEdgeStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.AddVertexStartStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.AddVertexStep;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;

/**
 * A traversal strategy that applies adds constant values to vertices and edges. Basically a
 * {@code PartitionStrategy} without the read partitions.
 *
 * @author jgustie
 */
@SuppressWarnings("ComparableType") // The super breaks the contract of Comparable
public final class PropertyConstantStrategy
        extends AbstractTraversalStrategy<TraversalStrategy.DecorationStrategy>
        implements TraversalStrategy.DecorationStrategy {

    private static final long serialVersionUID = 1L;

    private final Object[] keyValues;

    private PropertyConstantStrategy(Builder builder) {
        this.keyValues = builder.keyValues.toArray();
    }

    public Map<Object, Object> getPropertyMap() {
        Map<Object, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; ++i) {
            result.put(keyValues[i], keyValues[++i]);
        }
        return result;
    }

    @Override
    public void apply(Admin<?, ?> traversal) {
        checkState(traversal.getGraph().isPresent(), "PropertyConstantStrategy does not work with anonymous Traversals");
        traversal.getSteps().stream()
                .filter(step -> step instanceof AddEdgeStep
                        || step instanceof AddVertexStep
                        || step instanceof AddVertexStartStep)
                .forEach(step -> ((Mutating<?>) step).addPropertyMutations(keyValues));
    }

    @Override
    public Configuration getConfiguration() {
        final Map<String, Object> map = new HashMap<>();
        map.put(STRATEGY, PropertyConstantStrategy.class.getCanonicalName());
        map.put(PROPERTIES, Collections.unmodifiableList(Arrays.asList(keyValues)));
        return new MapConfiguration(map);
    }

    public static final String PROPERTIES = "properties";

    public static PropertyConstantStrategy create(Configuration configuration) {
        final PropertyConstantStrategy.Builder builder = PropertyConstantStrategy.build();
        List<Object> properties = configuration.getList(PROPERTIES);
        for (int i = 0; i < properties.size(); ++i) {
            builder.addProperty((String) properties.get(i), properties.get(++i));
        }
        return builder.create();
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {
        private List<Object> keyValues = new ArrayList<>();

        private Builder() {
        }

        /**
         * Adds the specified key/value pair to every created edge or vertex the resulting strategy
         * is applied to. Ignored if {@code value} is {@literal null}.
         */
        public Builder addProperty(String key, @Nullable Object value) {
            if (value != null) {
                keyValues.add(Objects.requireNonNull(key));
                keyValues.add(value);
            }
            return this;
        }

        /**
         * Creates the {@code PropertyConstantStrategy}.
         */
        public PropertyConstantStrategy create() {
            checkState(!keyValues.isEmpty(), "The property list cannot be empty");
            return new PropertyConstantStrategy(this);
        }
    }

}
