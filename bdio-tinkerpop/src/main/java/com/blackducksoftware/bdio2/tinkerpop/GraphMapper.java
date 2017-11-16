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

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioOptions;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.Maps;

/**
 * The representation of the BDIO graph schema. This class describes how to perform the mapping between a JSON-LD graph
 * and a TinkerPop graph. It is expected that the TinkerPop graph has a fixed schema while the JSON-LD graph may include
 * extensible data.
 *
 * @author jgustie
 */
public class GraphMapper {

    /**
     * The graph topology to use for mapping.
     */
    private final GraphTopology topology;

    /**
     * The JSON-LD value object mapper to use.
     */
    private final ValueObjectMapper valueObjectMapper;

    /**
     * Options for creating BDIO documents.
     */
    private final BdioOptions options;

    private GraphMapper(Builder builder) {
        // Get a reference to the topology
        topology = builder.topology.get();

        // Construct the value object mapper
        ValueObjectMapper.Builder valueObjectMapperBuilder = new ValueObjectMapper.Builder();
        topology.forEachEmbeddedType((label, id) -> {
            // Normally the ValueObjectMapper only tracks fully qualified types but we need to use it prior to
            // JSON-LD expansion so we need to tell it to recognize the vertex labels as well
            valueObjectMapperBuilder.addEmbeddedType(label);
            valueObjectMapperBuilder.addEmbeddedType(id);
        });
        topology.forEachMultiValueDataPropertyKey(valueObjectMapperBuilder::addMultiValueKey);
        builder.datatypes.forEach(valueObjectMapperBuilder::useDatatypeHandler);
        builder.multiValueCollector.ifPresent(valueObjectMapperBuilder::multiValueCollector);
        valueObjectMapper = valueObjectMapperBuilder.build();

        // Set our value object mapper as the context mapper
        ValueObjectMapper.setContextValueObjectMapper(valueObjectMapper);

        // Construct the BDIO options
        BdioOptions.Builder optionsBuilder = new BdioOptions.Builder();
        optionsBuilder.forContentType(builder.contentType, builder.expandContext);
        optionsBuilder.applicationContext(topology.applicationContext());
        options = optionsBuilder.build();

    }

    /**
     * Returns a reference to the topology. This is because construction of the actual topology is often deferred until
     * the graph mapper itself is constructed.
     */
    GraphTopology topology() {
        return topology;
    }

    // TODO Should we have a separate VertexProperties class?

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

    /**
     * @see GraphMapper#presentFirst(Comparator)
     * @see GraphMapper#presentLast(Comparator)
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

    public ValueObjectMapper valueObjectMapper() {
        return valueObjectMapper;
    }

    public RxJavaBdioDocument newBdioDocument() {
        return new RxJavaBdioDocument(options);
    }

    public Map<String, Object> compact(Map<String, Object> input) throws JsonLdError {
        return JsonLdProcessor.compact(input, topology.context(), options.jsonLdOptions());
    }

    public List<Object> expand(Object input) throws JsonLdError {
        return JsonLdProcessor.expand(input, options.jsonLdOptions());
    }

    public Map<String, Object> frame() {
        // Construct the JSON-LD frame
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put(JsonLdConsts.CONTEXT, topology.context());
        frame.put(JsonLdConsts.TYPE, topology.type());
        return frame;
    }

    /**
     * Returns all of the unknown properties, serialized as a single string. If there are no unknown properties in
     * the supplied node map, then the optional will be empty.
     */
    public Optional<String> preserveUnknownProperties(Map<String, Object> node) {
        return Optional.of(Maps.filterKeys(node, GraphMapper::isUnknownKey))
                .filter(m -> !m.isEmpty())
                .map(m -> {
                    try {
                        return JsonUtils.toString(m);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    /**
     * Given a preserved serialization of unknown properties, replay them back to the supplied consumer.
     */
    public void restoreUnknownProperties(@Nullable Object value, BiConsumer<String, Object> unknownPropertyConsumer) {
        if (value != null) {
            try {
                Object unknown = JsonUtils.fromString(value.toString());
                if (unknown instanceof Map<?, ?>) {
                    for (Map.Entry<?, ?> unknownProperty : ((Map<?, ?>) unknown).entrySet()) {
                        unknownPropertyConsumer.accept((String) unknownProperty.getKey(), unknownProperty.getValue());
                    }
                }
            } catch (IOException e) {
                // Ignore this...
            }
        }
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {

        private Supplier<GraphTopology> topology;

        private final Map<String, DatatypeHandler<?>> datatypes = new LinkedHashMap<>();

        private Optional<Collector<? super Object, ?, ?>> multiValueCollector = Optional.empty();

        private Bdio.ContentType contentType;

        private Object expandContext;

        private Builder() {
            topology = () -> GraphTopology.build().create();
        }

        public Builder multiValueCollector(@Nullable Collector<? super Object, ?, ?> multiValueCollector) {
            this.multiValueCollector = Optional.ofNullable(multiValueCollector);
            return this;
        }

        public Builder forContentType(@Nullable Bdio.ContentType contentType, @Nullable Object expandContext) {
            this.contentType = contentType;
            this.expandContext = expandContext;
            return this;
        }

        public Builder addDatatype(String iri, DatatypeHandler<?> handler) {
            datatypes.put(Objects.requireNonNull(iri), Objects.requireNonNull(handler));
            return this;
        }

        public Builder withTopology(Supplier<GraphTopology> topology) {
            this.topology = Objects.requireNonNull(topology);
            return this;
        }

        public Builder withConfiguration(Configuration configuration) {
            Objects.requireNonNull(configuration);
            // TODO Should we allow configuration of anything else?
            return this;
        }

        public GraphMapper create() {
            return new GraphMapper(this);
        }

    }

    /**
     * Check to see if the specified key represents an unknown property.
     */
    private static boolean isUnknownKey(String key) {
        // If framing did not recognize the attribute, it will still have a scheme or prefix separator
        return key.indexOf(':') >= 0;
    }

}
