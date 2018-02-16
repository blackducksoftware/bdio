/*
 * Copyright 2016 Black Duck Software, Inc.
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

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.javatuples.Pair;

import com.blackducksoftware.bdio2.Bdio;

/**
 * Creates a BDIO to graph mapper implementation.
 *
 * @author jgustie
 */
public class BlackDuckIoMapper implements Mapper<GraphMapper> {

    @Nullable
    private final Bdio.ContentType contentType;

    @Nullable
    private final Object expandContext;

    private final Optional<BlackDuckIoTokens> tokens;

    private final List<DatatypeRegistration> datatypes;

    private final Optional<MultiValueCollectorRegistration> multiValueCollector;

    private BlackDuckIoMapper(BlackDuckIoMapper.Builder builder) {
        contentType = builder.version.contentType(builder.contentType);
        expandContext = builder.version.expandContext(builder.expandContext);
        tokens = Optional.ofNullable(builder.tokens);
        datatypes = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class, DatatypeRegistration.class).stream())
                .map(Pair::getValue1).collect(toList());
        multiValueCollector = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class, MultiValueCollectorRegistration.class).stream())
                .map(Pair::getValue1).findAny();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GraphMapper createMapper() {
        GraphMapper.Builder mapperBuilder = GraphMapper.build().forContentType(contentType, expandContext);
        tokens.ifPresent(mapperBuilder::tokens);
        datatypes.forEach(r -> mapperBuilder.addDatatype(r.iri(), r.handler()));
        multiValueCollector.ifPresent(r -> mapperBuilder.multiValueCollector(r.collector()));
        return mapperBuilder.create();
    }

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements Mapper.Builder<Builder> {

        private final List<IoRegistry> registries = new ArrayList<>();

        private BlackDuckIoVersion version;

        @Nullable
        private Bdio.ContentType contentType;

        @Nullable
        private Object expandContext;

        @Nullable
        private BlackDuckIoTokens tokens;

        private Builder() {
            version = BlackDuckIoVersion.defaultVersion();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Builder addRegistry(IoRegistry registry) {
            registries.add(Objects.requireNonNull(registry));
            return this;
        }

        // TODO If you set version to 1.x clear the contentType/expandContext
        // TODO If you set the contentType/expandContext set the version to 2.0 (or default)

        public Builder version(BlackDuckIoVersion version) {
            this.version = Objects.requireNonNull(version);
            return this;
        }

        public Builder contentType(Bdio.ContentType contentType, Object expandContext) {
            this.contentType = contentType;
            this.expandContext = expandContext;
            return this;
        }

        public Builder tokens(@Nullable BlackDuckIoTokens tokens) {
            this.tokens = tokens;
            return this;
        }

        public BlackDuckIoMapper create() {
            return new BlackDuckIoMapper(this);
        }

    }

}
