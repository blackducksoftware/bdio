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

import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

import com.blackducksoftware.bdio2.datatype.Fingerprint;
import com.blackducksoftware.bdio2.datatype.Products;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;

/**
 * Creates a JSON-LD value object mapper.
 *
 * @author jgustie
 */
public class BlackDuckIoMapper implements Mapper<ValueObjectMapper> {

    /**
     * A special value mapper to normalize types supported by the graph.
     */
    private static class GraphValueObjectMapper extends ValueObjectMapper {
        @Override
        public Object fromFieldValue(Object input) {
            Object modelValue = super.fromFieldValue(input);
            if (modelValue instanceof Instant) {
                // TODO Switch back to ZonedDateTime in Sqlg 1.3.3
                // return ZonedDateTime.ofInstant((Instant) modelValue, ZoneOffset.UTC);
                return LocalDateTime.ofInstant((Instant) modelValue, ZoneId.systemDefault());
            } else if (modelValue instanceof Fingerprint || modelValue instanceof Products) {
                return modelValue.toString();
            } else {
                return modelValue;
            }
        }

        @Override
        public Object toValueObject(Object value) {
            if (value instanceof ZonedDateTime) {
                return super.toValueObject(((ZonedDateTime) value).toInstant());
            } else if (value instanceof LocalDateTime) {
                // TODO Normalize on ZonedDateTime in Sqlg 1.3.3
                ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset((LocalDateTime) value);
                return super.toValueObject(((LocalDateTime) value).toInstant(offset));
            } else {
                return super.toValueObject(value);
            }
        }
    }

    private BlackDuckIoMapper(BlackDuckIoMapper.Builder builder) {
        Map<Class<?>, Object> serializers = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class).stream())
                .collect(Collectors.toMap(p -> p.getValue0(), p -> p.getValue1()));
        serializers.forEach((k, v) -> {
            // TODO Customize the GraphValueObjectMapper
        });
    }

    @Override
    public ValueObjectMapper createMapper() {
        return new GraphValueObjectMapper();
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
