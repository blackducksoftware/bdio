/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio2.tinkerpop;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collector;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.javatuples.Pair;

import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;

/**
 * Creates a JSON-LD to graph mapper.
 *
 * @author jgustie
 */
public class BlackDuckIoMapper implements Mapper<GraphMapper> {

    /**
     * Allows for the registration of custom graph initializers.
     */
    public interface GraphInitializer {

        /**
         * Initializes the graph to use the specified topology.
         */
        void initialize(GraphTopology graphTopology);

    }

    /**
     * Allows for the registration of custom datatypes or overridden datatype handlers.
     */
    public interface DatatypeRegistration {
        /**
         * Returns the IRI used to identify the datatype.
         */
        String iri();

        /**
         * Returns the handler used for the datatype.
         */
        DatatypeHandler<?> handler();
    }

    /**
     * Allows for the registration of a single multi-value collector.
     */
    public interface MultiValueCollectorRegistration {

        /**
         * Returns the collector used when a property contains multiple values.
         */
        Collector<? super Object, ?, ?> collector();

    }

    /**
     * Used to initialize the graph topology on each construction.
     */
    private final Consumer<GraphTopology.Builder> onGraphTopology;

    /**
     * Used to initialize the graph mapper on each construction.
     */
    private final Consumer<GraphMapper.Builder> onGraphMapper;

    private BlackDuckIoMapper(BlackDuckIoMapper.Builder builder) {
        // Configure the graph topology
        List<GraphInitializer> initializers = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class, GraphInitializer.class).stream())
                .map(Pair::getValue1).collect(toList());

        onGraphTopology = gt -> {
            initializers.forEach(i -> gt.addInitializer(i::initialize));
            builder.onGraphTopology.ifPresent(c -> c.accept(gt));
        };

        // Configure the graph mapper
        List<DatatypeRegistration> datatypes = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class, DatatypeRegistration.class).stream())
                .map(Pair::getValue1).collect(toList());
        Optional<MultiValueCollectorRegistration> multiValueCollector = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class, MultiValueCollectorRegistration.class).stream())
                .map(Pair::getValue1).findAny();

        onGraphMapper = gm -> {
            datatypes.forEach(dr -> gm.addDatatype(dr.iri(), dr.handler()));
            multiValueCollector.ifPresent(mvcr -> gm.multiValueCollector(mvcr.collector()));
            builder.onGraphMapper.ifPresent(c -> c.accept(gm));
            gm.withTopology(this::createTopology);
        };
    }

    public GraphTopology createTopology() {
        GraphTopology.Builder topologyBuilder = GraphTopology.build();
        onGraphTopology.accept(topologyBuilder);
        return topologyBuilder.create();
    }

    @Override
    public GraphMapper createMapper() {
        GraphMapper.Builder mapperBuilder = GraphMapper.build();
        onGraphMapper.accept(mapperBuilder);
        return mapperBuilder.create();
    }

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements Mapper.Builder<Builder> {

        private final List<IoRegistry> registries = new ArrayList<>();

        private Optional<Consumer<GraphTopology.Builder>> onGraphTopology = Optional.empty();

        private Optional<Consumer<GraphMapper.Builder>> onGraphMapper = Optional.empty();

        private Builder() {
        }

        @Override
        public Builder addRegistry(IoRegistry registry) {
            registries.add(Objects.requireNonNull(registry));
            return this;
        }

        public BlackDuckIoMapper create() {
            return new BlackDuckIoMapper(this);
        }

        public Builder onGraphTopology(@Nullable Consumer<GraphTopology.Builder> onGraphTopology) {
            this.onGraphTopology = Optional.ofNullable(onGraphTopology);
            return this;
        }

        public Builder onGraphMapper(@Nullable Consumer<GraphMapper.Builder> onGraphMapper) {
            this.onGraphMapper = Optional.ofNullable(onGraphMapper);
            return this;
        }
    }

}
