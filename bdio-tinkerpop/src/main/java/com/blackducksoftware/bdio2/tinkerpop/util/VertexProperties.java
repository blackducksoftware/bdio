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
package com.blackducksoftware.bdio2.tinkerpop.util;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;

/**
 * Static utility methods to simplify working with vertex properties.
 *
 * @author jgustie
 */
public class VertexProperties {

    /**
     * @see VertexProperties#presentFirst(Comparator)
     * @see VertexProperties#presentLast(Comparator)
     */
    private static final class PresentComparator<T> implements Comparator<VertexProperty<T>>, Serializable {
        private static final long serialVersionUID = 2383921724074331436L;

        private final Comparator<T> delegate;

        private final boolean presentFirst;

        private PresentComparator(Comparator<T> delegate, boolean presentFirst) {
            this.delegate = delegate;
            this.presentFirst = presentFirst;
        }

        @Override
        public int compare(VertexProperty<T> left, VertexProperty<T> right) {
            if (!left.isPresent()) {
                return !right.isPresent() ? 0 : (presentFirst ? -1 : 1);
            } else if (!right.isPresent()) {
                return presentFirst ? 1 : -1;
            } else {
                return delegate != null ? delegate.compare(left.value(), right.value()) : 0;
            }
        }

        @Override
        public Comparator<VertexProperty<T>> reversed() {
            return new PresentComparator<>(delegate != null ? delegate.reversed() : null, !presentFirst);
        }
    }

    /**
     * Returns a stream representing the values of the vertex property. If the value is null or not present, an empty
     * stream is returned; if the value is not a list or array then a single element stream is returned; otherwise a
     * stream is returned over the individual elements of the value.
     */
    public static Stream<?> streamValue(VertexProperty<?> vp) {
        Object value = vp.orElse(null);
        if (value == null) {
            return Stream.empty();
        } else if (value instanceof List<?>) {
            return ((List<?>) value).stream();
        } else if (value.getClass().isArray()) {
            return Stream.of((Object[]) value);
        } else {
            return Stream.of(value);
        }
    }

    /**
     * Returns an optional representing the value of the vertex property. While the vertex property itself has methods
     * like {@code orElse}, it lacks methods like {@code map}.
     */
    public static <T> Optional<T> optionalValue(VertexProperty<T> vp) {
        return vp.isPresent() ? Optional.of(vp.value()) : Optional.empty();
    }

    /**
     * Returns an optional string representing the value of the vertex property with the specified key.
     */
    public static Optional<String> stringValue(Vertex v, Object propertyKey) {
        return optionalValue(v.property(key(propertyKey)));
    }

    /**
     * Returns an optional object representing the value of the vertex property with the specified key.
     */
    public static Optional<Object> objectValue(Vertex v, Object propertyKey) {
        return optionalValue(v.property(key(propertyKey)));
    }

    /**
     * Returns a vertex property comparator that considers non-present properties to be less than present. The supplied
     * comparator is used only when both vertex properties are present.
     */
    public static <T> Comparator<VertexProperty<T>> presentFirst(Comparator<T> comparator) {
        return new PresentComparator<>(comparator, true);
    }

    /**
     * Returns a vertex property comparator that considers non-present properties to be greater than present. The
     * supplied comparator is used only when both vertex properties are present.
     */
    public static <T> Comparator<VertexProperty<T>> presentLast(Comparator<T> comparator) {
        return new PresentComparator<>(comparator, false);
    }

    private static String key(Object propertyKey) {
        if (propertyKey instanceof Enum<?>) {
            return ((Enum<?>) propertyKey).name();
        } else if (propertyKey instanceof String) {
            return (String) propertyKey;
        } else {
            throw new IllegalArgumentException("unknown property key type: " + propertyKey);
        }
    }

    private VertexProperties() {
    }
}
