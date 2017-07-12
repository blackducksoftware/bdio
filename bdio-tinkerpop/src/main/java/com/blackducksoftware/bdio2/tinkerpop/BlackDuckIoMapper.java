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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    private final Map<String, DatatypeHandler<?>> datatypeHandlers;

    private final Consumer<GraphMapper.Builder> onGraphMapper;

    private BlackDuckIoMapper(BlackDuckIoMapper.Builder builder) {
        // IoRegistry implementations need to register both a String (the IRI) and a DatatypeHandler
        Map<Class<?>, String> typeMappings = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class, String.class).stream())
                .collect(Collectors.toMap(Pair::getValue0, Pair::getValue1));

        // Keep all the datatype handlers which have a type mapping
        datatypeHandlers = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class, DatatypeHandler.class).stream())
                .map(p -> Pair.with(typeMappings.get(p.getValue0()), p.getValue1()))
                .filter(p -> p.getValue0() != null)
                .collect(Collectors.toMap(Pair::getValue0, Pair::getValue1));

        onGraphMapper = builder.onGraphMapper.orElse(b -> {
        });
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

        private Optional<Consumer<GraphMapper.Builder>> onGraphMapper = Optional.empty();

        private Builder() {
        }

        @Override
        public Builder addRegistry(IoRegistry registry) {
            registries.add(Objects.requireNonNull(registry));
            return this;
        }

        public Builder onGraphMapper(@Nullable Consumer<GraphMapper.Builder> onGraphMapper) {
            this.onGraphMapper = Optional.ofNullable(onGraphMapper);
            return this;
        }

        public BlackDuckIoMapper create() {
            return new BlackDuckIoMapper(this);
        }
    }

}
