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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.umlg.sqlg.structure.RecordId;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.datatype.DatatypeSupport;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;

/**
 * Creates a JSON-LD value object mapper.
 *
 * @author jgustie
 */
public class BlackDuckIoMapper implements Mapper<ValueObjectMapper> {

    private BlackDuckIoMapper(BlackDuckIoMapper.Builder builder) {
        Map<Class<?>, Object> serializers = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class).stream())
                .collect(Collectors.toMap(p -> p.getValue0(), p -> p.getValue1()));
        serializers.forEach((k, v) -> {
            // TODO Customize the based on what the registry said
        });
    }

    @Override
    public ValueObjectMapper createMapper() {
        // TODO Should the builder be state on the instance?
        ValueObjectMapper.Builder builder = new ValueObjectMapper.Builder();

        // TODO This is sqlg specific because record identifiers do not serialize to JSON
        builder.useDatatypeHandler(Bdio.Datatype.Default.toString(), DatatypeHandler.from(
                x -> DatatypeSupport.Default().isInstance(x) || x instanceof RecordId,
                DatatypeSupport.Default()::serialize,
                DatatypeSupport.Default()::deserialize));

        // TODO Really this is sqlg specific because TinkerGraph lets any object in
        builder.useDatatypeHandler(Bdio.Datatype.Fingerprint.toString(), DatatypeHandler.from(
                DatatypeSupport.Fingerprint()::isInstance,
                DatatypeSupport.Fingerprint()::serialize,
                nullSafe(DatatypeSupport.Fingerprint()::deserialize).andThen(Object::toString)));
        builder.useDatatypeHandler(Bdio.Datatype.Products.toString(), DatatypeHandler.from(
                DatatypeSupport.Products()::isInstance,
                DatatypeSupport.Products()::serialize,
                nullSafe(DatatypeSupport.Products()::deserialize).andThen(Object::toString)));

        // TODO Switch back to ZonedDateTime in Sqlg 1.3.3
        builder.useDatatypeHandler(Bdio.Datatype.DateTime.toString(), DatatypeHandler.from(
                LocalDateTime.class::isInstance,
                DatatypeSupport.DateTime()::serialize,
                nullSafe(DatatypeSupport.DateTime()::deserialize).andThen(LocalDateTime::from)));

        return builder.build();
    }

    /**
     * Wraps the supplied function such that it is not invoked with {@code null} values.
     */
    private static <T, R> Function<T, R> nullSafe(Function<T, R> f) {
        Objects.requireNonNull(f);
        return new Function<T, R>() {
            @Override
            public R apply(T t) {
                return Optional.ofNullable(t).map(f).orElse(null);
            }

            @Override
            public <V> Function<V, R> compose(Function<? super V, ? extends T> before) {
                Objects.requireNonNull(before);
                return (V v) -> Optional.ofNullable(v).map(before).map(f).orElse(null);
            }

            @Override
            public <V> Function<T, V> andThen(Function<? super R, ? extends V> after) {
                Objects.requireNonNull(after);
                return (T t) -> Optional.ofNullable(t).map(f).map(after).orElse(null);
            }
        };
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
