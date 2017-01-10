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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.tinkerpop.gremlin.structure.io.AbstractIoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;

/**
 * Creates a JSON-LD value object mapper.
 *
 * @author jgustie
 */
public class BlackDuckIoMapper implements Mapper<ValueObjectMapper> {

    /**
     * An IO Registry used to supplement the real {@link org.umlg.sqlg.structure.SqlgIoRegistry}.
     */
    private static class SqlgBdioIoRegistry extends AbstractIoRegistry {
        private static final SqlgBdioIoRegistry INSTANCE = new SqlgBdioIoRegistry();

        private SqlgBdioIoRegistry() {
            // TODO Ideally, instead of a Boolean, this would be some type of plugin to the ValueObjectMapper (or an
            // extension of) that performs the extra mapping for Instant types. For right now, that isn't necessary.
            register(BlackDuckIo.class, Instant.class, Boolean.TRUE);
        }

        public static SqlgBdioIoRegistry getInstance() {
            return INSTANCE;
        }
    }

    /**
     * A special value mapper to use with Sqlg to make sure we only use supported types.
     */
    private static class SqlgValueObjectMapper extends ValueObjectMapper {
        @Override
        public Object fromFieldValue(Object input) {
            Object modelValue = super.fromFieldValue(input);
            if (modelValue instanceof Instant) {
                // TODO Switch back to ZonedDateTime in Sqlg 1.3.3
                // return ZonedDateTime.ofInstant((Instant) modelValue, ZoneOffset.UTC);
                return LocalDateTime.ofInstant((Instant) modelValue, ZoneId.systemDefault());
            } else {
                return modelValue;
            }
        }

        @Override
        public Object toValueObject(Object value) {
            if (value instanceof ZonedDateTime) {
                return super.toValueObject(((ZonedDateTime) value).toInstant());
            } else if (value instanceof LocalDateTime) {
                ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset((LocalDateTime) value);
                return super.toValueObject(((LocalDateTime) value).toInstant(offset));
            } else {
                return super.toValueObject(value);
            }
        }
    }

    /**
     * TODO This is a flag indicating we detected Sqlg, eventually it will be replaced by type adapters...
     */
    private final boolean sqlg;

    private BlackDuckIoMapper(BlackDuckIoMapper.Builder builder) {
        // TODO This is kind of a round-about way of detecting Sqlg, but it's more written for the generic approach
        Map<Class<?>, Object> m = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class).stream())
                .collect(Collectors.toMap(p -> p.getValue0(), p -> p.getValue1()));
        sqlg = m.get(Instant.class) != null;
    }

    @Override
    public ValueObjectMapper createMapper() {
        return sqlg ? new SqlgValueObjectMapper() : new ValueObjectMapper();
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

            // If it is a SqlgIoRegistry, add our extra Sqlg registry as well
            if (registry.getClass().getSimpleName().equals("SqlgIoRegistry")) {
                registries.add(SqlgBdioIoRegistry.getInstance());
            }

            return this;
        }

        public BlackDuckIoMapper create() {
            return new BlackDuckIoMapper(this);
        }
    }

}
