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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.javatuples.Pair;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.datatype.DatatypeSupport;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;

/**
 * Creates a JSON-LD to graph mapper.
 *
 * @author jgustie
 */
public class BlackDuckIoMapper implements Mapper<GraphMapper> {

    /**
     * Strongly typed consumer used for discovery.
     */
    public interface GraphMapperConfigurator extends Consumer<GraphMapper.Builder> {
    }

    private final Map<String, DatatypeHandler<?>> datatypeHandlers;

    private final Consumer<GraphMapper.Builder> onGraphMapper;

    private BlackDuckIoMapper(BlackDuckIoMapper.Builder builder) {
        // IoRegistry implementations need to register both a String (the IRI) and a DatatypeHandler
        Map<Class<?>, String> typeMappings = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class, String.class).stream())
                .collect(Collectors.toMap(Pair::getValue0, Pair::getValue1));

        // Add missing type mappings
        for (Bdio.Datatype datatype : Bdio.Datatype.values()) {
            typeMappings.putIfAbsent(DatatypeSupport.getJavaType(datatype), datatype.toString());
        }

        // Keep all the datatype handlers which have a type mapping
        datatypeHandlers = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class, DatatypeHandler.class).stream())
                .map(p -> Pair.with(typeMappings.get(p.getValue0()), p.getValue1()))
                .filter(p -> p.getValue0() != null)
                .collect(Collectors.toMap(Pair::getValue0, Pair::getValue1));

        // Accumulate all the configurations
        onGraphMapper = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class, GraphMapperConfigurator.class).stream())
                .map(Pair::getValue1)
                .reduce(b -> {}, Consumer::andThen, Consumer::andThen);
    }

    @Override
    public GraphMapper createMapper() {
        GraphMapper.Builder mapperBuilder = GraphMapper.build();
        datatypeHandlers.forEach(mapperBuilder::addDatatype);
        onGraphMapper.accept(mapperBuilder);
        return mapperBuilder.create();
    }

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements Mapper.Builder<Builder> {

        private final List<IoRegistry> registries = new ArrayList<>();

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
    }

}
