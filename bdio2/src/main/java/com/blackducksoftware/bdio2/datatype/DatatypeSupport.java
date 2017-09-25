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
package com.blackducksoftware.bdio2.datatype;

import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.Objects;
import java.util.function.Function;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;
import com.blackducksoftware.common.value.ContentRange;
import com.blackducksoftware.common.value.ContentType;
import com.blackducksoftware.common.value.Digest;
import com.blackducksoftware.common.value.ProductList;

/**
 * Support for the built-in datatypes.
 *
 * @author jgustie
 */
public class DatatypeSupport {

    public static DatatypeHandler<Object> Default() {
        return DefaultDatatypeHandler.INSTANCE;
    }

    public static DatatypeHandler<ZonedDateTime> DateTime() {
        return DateTimeDatatypeHandler.INSTANCE;
    }

    public static DatatypeHandler<Digest> Digest() {
        return DigestDatatypeHandler.INSTANCE;
    }

    public static DatatypeHandler<Long> Long() {
        return LongDatatypeHandler.INSTANCE;
    }

    public static DatatypeHandler<ProductList> Products() {
        return ProductsDatatypeHandler.INSTANCE;
    }

    public static DatatypeHandler<ContentRange> ContentRange() {
        return ContentRangeDatatypeHandler.INSTANCE;
    }

    public static DatatypeHandler<ContentType> ContentType() {
        return ContentTypeDatatypeHandler.INSTANCE;
    }

    /**
     * Returns the built-in datatype handler for the supplied datatype.
     */
    public static DatatypeHandler<?> getDatatypeHandler(Bdio.Datatype datatype) {
        // Because we iterate over the enumeration, this implementation ensures we do not accidentally forget a type.
        switch (datatype) {
        case Default:
            return Default();
        case DateTime:
            return DateTime();
        case Digest:
            return Digest();
        case Long:
            return Long();
        case Products:
            return Products();
        case ContentRange:
            return ContentRange();
        case ContentType:
            return ContentType();
        default:
            throw new IllegalArgumentException("unrecognized datatype: " + datatype.name());
        }
    }

    /**
     * Returns the Java type used by the provided datatype handlers.
     */
    public static Class<?> getJavaType(Bdio.Datatype datatype) {
        return ((BuiltInDatatypeHandler<?>) getDatatypeHandler(datatype)).javaType;
    }

    /**
     * @see DatatypeSupport#Default()
     */
    private static final class DefaultDatatypeHandler extends BuiltInDatatypeHandler<Object> {
        private static final DefaultDatatypeHandler INSTANCE = new DefaultDatatypeHandler();

        private DefaultDatatypeHandler() {
            super(String.class);
        }

        @Override
        public boolean isInstance(Object value) {
            return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
        }

        @Override
        public Object serialize(Object value) {
            return isInstance(value) ? value : value.toString();
        }

        @Override
        public Object deserialize(Object value) {
            return isInstance(value) ? value : value.toString();
        }
    }

    /**
     * @see DatatypeSupport#DateTime()
     */
    private static final class DateTimeDatatypeHandler extends BuiltInDatatypeHandler<ZonedDateTime> {
        private static final DateTimeDatatypeHandler INSTANCE = new DateTimeDatatypeHandler();

        private DateTimeDatatypeHandler() {
            super(ZonedDateTime.class);
        }

        @Override
        public ZonedDateTime deserialize(Object value) {
            if (value instanceof ZonedDateTime || value == null) {
                return (ZonedDateTime) value;
            } else if (value instanceof String) {
                return ZonedDateTime.parse((String) value);
            } else if (value instanceof Temporal) {
                return ZonedDateTime.from(((Temporal) value));
            } else if (value instanceof Date) {
                return ZonedDateTime.ofInstant(((Date) value).toInstant(), ZoneOffset.UTC);
            } else {
                throw invalidInput(value, DateTimeException::new);
            }
        }
    }

    /**
     * @see DatatypeSupport#Digest()
     */
    private static final class DigestDatatypeHandler extends BuiltInDatatypeHandler<Digest> {
        private static final DigestDatatypeHandler INSTANCE = new DigestDatatypeHandler();

        private DigestDatatypeHandler() {
            super(Digest.class);
        }

        @Override
        public Digest deserialize(Object value) {
            if (value instanceof Digest || value == null) {
                return (Digest) value;
            } else if (value instanceof String) {
                return Digest.parse((String) value);
            } else {
                throw invalidInput(value, IllegalArgumentException::new);
            }
        }
    }

    /**
     * @see DatatypeSupport#Long()
     */
    private static final class LongDatatypeHandler extends BuiltInDatatypeHandler<Long> {
        private static final LongDatatypeHandler INSTANCE = new LongDatatypeHandler();

        private LongDatatypeHandler() {
            super(Long.class);
        }

        @Override
        public Object serialize(Object value) {
            return value;
        }

        @Override
        public Long deserialize(Object value) {
            if (value instanceof Long || value == null) {
                return (Long) value;
            } else if (value instanceof String) {
                return Long.valueOf((String) value);
            } else if (value instanceof Number) {
                return Long.valueOf(((Number) value).longValue());
            } else {
                throw invalidInput(value, NumberFormatException::new);
            }
        }
    }

    /**
     * @see DatatypeSupport#Products()
     */
    private static final class ProductsDatatypeHandler extends BuiltInDatatypeHandler<ProductList> {
        private static final ProductsDatatypeHandler INSTANCE = new ProductsDatatypeHandler();

        private ProductsDatatypeHandler() {
            super(ProductList.class);
        }

        @Override
        public ProductList deserialize(Object value) {
            if (value instanceof ProductList || value == null) {
                return (ProductList) value;
            } else if (value instanceof String) {
                return ProductList.parse((String) value);
            } else {
                throw invalidInput(value, IllegalArgumentException::new);
            }
        }
    }

    /**
     * @see DatatypeSupport#ContentRange()
     */
    private static final class ContentRangeDatatypeHandler extends BuiltInDatatypeHandler<ContentRange> {
        private static final ContentRangeDatatypeHandler INSTANCE = new ContentRangeDatatypeHandler();

        private ContentRangeDatatypeHandler() {
            super(ContentRange.class);
        }

        @Override
        public ContentRange deserialize(Object value) {
            if (value instanceof ContentRange || value == null) {
                return (ContentRange) value;
            } else if (value instanceof String) {
                return ContentRange.parse((String) value);
            } else {
                throw invalidInput(value, IllegalArgumentException::new);
            }
        }
    }

    /**
     * @see DatatypeSupport#ContentType()
     */
    private static final class ContentTypeDatatypeHandler extends BuiltInDatatypeHandler<ContentType> {
        private static final ContentTypeDatatypeHandler INSTANCE = new ContentTypeDatatypeHandler();

        private ContentTypeDatatypeHandler() {
            super(ContentType.class);
        }

        @Override
        public ContentType deserialize(Object value) {
            if (value instanceof ContentType || value == null) {
                return (ContentType) value;
            } else if (value instanceof String) {
                return ContentType.parse((String) value);
            } else {
                throw invalidInput(value, IllegalArgumentException::new);
            }
        }
    }

    /**
     * Base class for the built-in datatype handlers.
     */
    private static abstract class BuiltInDatatypeHandler<T> implements DatatypeHandler<T> {

        /**
         * The Java type associated with this datatype handler. This may be different from the type parameter, if for
         * example, the datatype handler can support multiple types.
         */
        private final Class<?> javaType;

        private BuiltInDatatypeHandler(Class<?> javaType) {
            this.javaType = Objects.requireNonNull(javaType);
        }

        @Override
        public boolean isInstance(Object value) {
            return javaType.isInstance(value);
        }

        @Override
        public Object serialize(Object value) {
            return Objects.toString(value, null);
        }

        /**
         * Constructs a new runtime exception indicating the supplied value was not suitable for deserialization. The
         * supplied exception factory should produce the same type of exception that other value object producing
         * methods producing throw when presented with invalid input.
         */
        protected static <X extends RuntimeException> X invalidInput(Object value, Function<String, X> factory) {
            Objects.requireNonNull(value);
            return factory.apply("invalid input " + value + " (" + value.getClass().getName() + ")");
        }
    }

    private DatatypeSupport() {
    }
}
