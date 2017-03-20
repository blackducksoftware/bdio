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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;

/**
 * Configuration and associated helper methods for reading and writing BDIO data.
 *
 * @author jgustie
 */
public class BlackDuckIoConfig {

    /**
     * The property key used to persist JSON-LD node identifiers.
     */
    private final Optional<String> identifierKey;

    /**
     * The property key used to persist "unknown" JSON-LD node properties.
     */
    private final Optional<String> unknownKey;

    /**
     * The property key used to persist a flag indicating implicit creation of missing BDIO data.
     */
    private final Optional<String> implicitKey;

    /**
     * The vertex label used to persist JSON-LD named graph metadata.
     */
    private final Optional<String> metadataLabel;

    /**
     * The partitioning strategy used isolate JSON-LD sub-graphs.
     */
    private final Optional<PartitionStrategy> partitionStrategy;

    /**
     * The JSON-LD value object mapper to use.
     */
    private final ValueObjectMapper valueObjectMapper;

    // TODO BdioFrame?

    /**
     * Builder for creating BDIO documents.
     */
    private final BdioDocument.Builder documentBuilder;

    private BlackDuckIoConfig(Builder builder) {
        identifierKey = Objects.requireNonNull(builder.identifierKey);
        unknownKey = Objects.requireNonNull(builder.unknownKey);
        implicitKey = Objects.requireNonNull(builder.implicitKey);
        metadataLabel = Objects.requireNonNull(builder.metadataLabel);
        partitionStrategy = Objects.requireNonNull(builder.partitionStrategy);
        valueObjectMapper = builder.valueObjectMapper.orElseGet(builder.defaultValueObjectMapper);
        documentBuilder = builder.documentBuilder.orElseGet(BdioDocument.Builder::new);
    }

    public Optional<String> identifierKey() {
        return identifierKey;
    }

    public Optional<String> unknownKey() {
        return unknownKey;
    }

    public Optional<String> implicitKey() {
        return implicitKey;
    }

    public Optional<String> metadataLabel() {
        return metadataLabel;
    }

    public Optional<PartitionStrategy> partitionStrategy() {
        return partitionStrategy;
    }

    public <D extends BdioDocument> D newBdioDocument(Class<D> type) {
        return documentBuilder.build(type);
    }

    public ValueObjectMapper valueObjectMapper() {
        return valueObjectMapper;
    }

    /**
     * Creates and initializes a new context for reading BDIO data into the supplied graph.
     */
    public ReadGraphContext newReadContext(Graph graph, BdioFrame frame, int batchSize) {
        ReadGraphContext context;
        if (graph instanceof SqlgGraph) {
            context = new SqlgReadGraphContext(this, (SqlgGraph) graph, batchSize);
        } else {
            context = new ReadGraphContext(this, graph, batchSize);
        }
        context.initialize(frame);
        return context;
    }

    /**
     * Creates and initializes a new context for writing BDIO data from the supplied graph.
     */
    public WriteGraphContext newWriteContext(Graph graph, BdioFrame frame) {
        WriteGraphContext context = new WriteGraphContext(this, graph);
        context.initialize(frame);
        return context;
    }

    public Builder newBuilder() {
        return new Builder()
                .identifierKey(identifierKey.orElse(null))
                .unknownKey(unknownKey.orElse(null))
                .metadataLabel(metadataLabel.orElse(null))
                .partitionStrategy(partitionStrategy.orElse(null))
                .valueObjectMapper(valueObjectMapper)
                .documentBuilder(documentBuilder);
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {

        private Optional<String> identifierKey = Optional.empty();

        private Optional<String> unknownKey = Optional.empty();

        private Optional<String> implicitKey = Optional.empty();

        private Optional<String> metadataLabel = Optional.empty();

        private Optional<PartitionStrategy> partitionStrategy = Optional.empty();

        private Optional<ValueObjectMapper> valueObjectMapper = Optional.empty();

        private Optional<BdioDocument.Builder> documentBuilder = Optional.empty();

        private Supplier<ValueObjectMapper> defaultValueObjectMapper = BlackDuckIoMapper.build().create()::createMapper;

        private Builder() {
        }

        public Builder identifierKey(@Nullable String identifierKey) {
            this.identifierKey = Optional.ofNullable(identifierKey);
            return this;
        }

        public Builder unknownKey(@Nullable String unknownKey) {
            this.unknownKey = Optional.ofNullable(unknownKey);
            return this;
        }

        public Builder implicitKey(@Nullable String implicitKey) {
            this.implicitKey = Optional.ofNullable(implicitKey);
            return this;
        }

        public Builder metadataLabel(@Nullable String metadataLabel) {
            this.metadataLabel = Optional.ofNullable(metadataLabel);
            return this;
        }

        public Builder partitionStrategy(@Nullable PartitionStrategy partitionStrategy) {
            this.partitionStrategy = Optional.ofNullable(partitionStrategy);
            return this;
        }

        public Builder valueObjectMapper(@Nullable ValueObjectMapper valueObjectMapper) {
            this.valueObjectMapper = Optional.ofNullable(valueObjectMapper);
            return this;
        }

        public Builder documentBuilder(@Nullable BdioDocument.Builder documentBuilder) {
            this.documentBuilder = Optional.ofNullable(documentBuilder);
            return this;
        }

        // ValueObjectMapper is kind of special because TinkerPop has configuration for it already
        Builder withDefaultValueObjectMapper(Supplier<ValueObjectMapper> defaultValueObjectMapper) {
            this.defaultValueObjectMapper = Objects.requireNonNull(defaultValueObjectMapper);
            return this;
        }

        public BlackDuckIoConfig create() {
            return new BlackDuckIoConfig(this);
        }
    }

}
