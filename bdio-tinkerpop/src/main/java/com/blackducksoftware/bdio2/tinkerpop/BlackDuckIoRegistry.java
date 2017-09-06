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

import java.time.ZonedDateTime;

import org.apache.tinkerpop.gremlin.structure.io.AbstractIoRegistry;
import org.umlg.sqlg.structure.RecordId;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.datatype.DatatypeSupport;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;
import com.blackducksoftware.common.value.ContentRange;
import com.blackducksoftware.common.value.ContentType;
import com.blackducksoftware.common.value.Digest;
import com.blackducksoftware.common.value.ProductList;

public class BlackDuckIoRegistry extends AbstractIoRegistry {

    private static final BlackDuckIoRegistry INSTANCE = new BlackDuckIoRegistry();

    private BlackDuckIoRegistry() {
        // Register a Java type which can be used to overwrite the DatatypeHandler of all the built-in types
        for (Bdio.Datatype datatype : Bdio.Datatype.values()) {
            register(BlackDuckIo.class, defaultJavaType(datatype), datatype.toString());
        }

        // This is just to make sure Sqlg works (otherwise RecordId wouldn't serialize correctly)
        register(BlackDuckIo.class, String.class, DatatypeHandler.from(
                x -> DatatypeSupport.Default().isInstance(x) || x instanceof RecordId,
                DatatypeSupport.Default()::serialize,
                DatatypeSupport.Default()::deserialize));

        // TODO Do we need these still?
        // // Really this is sqlg specific because TinkerGraph lets any object in
        // register(BlackDuckIo.class, Fingerprint.class, DatatypeHandler.from(
        // DatatypeSupport.Fingerprint()::isInstance,
        // DatatypeSupport.Fingerprint()::serialize,
        // nullSafe(DatatypeSupport.Fingerprint()::deserialize).andThen(Object::toString)));
        // register(BlackDuckIo.class, Products.class, DatatypeHandler.from(
        // DatatypeSupport.Products()::isInstance,
        // DatatypeSupport.Products()::serialize,
        // nullSafe(DatatypeSupport.Products()::deserialize).andThen(Object::toString)));
    }

    /**
     * This mapping ensures we do not accidentally forget a type.
     */
    private static Class<?> defaultJavaType(Bdio.Datatype datatype) {
        switch (datatype) {
        case Default:
            return String.class;
        case DateTime:
            return ZonedDateTime.class;
        case Digest:
            return Digest.class;
        case Long:
            return Long.class;
        case Products:
            return ProductList.class;
        case ContentRange:
            return ContentRange.class;
        case ContentType:
            return ContentType.class;
        default:
            throw new IllegalArgumentException("unrecognized datatype: " + datatype.name());
        }
    }

    // TODO Should this be something like `forGraph(Graph)` instead?
    public static BlackDuckIoRegistry getInstance() {
        return INSTANCE;
    }

}
