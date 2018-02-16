/*
 * Copyright 2018 Synopsys, Inc.
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

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.stream.Collector;

import org.apache.tinkerpop.gremlin.structure.io.AbstractIoRegistry;
import org.umlg.sqlg.structure.RecordId;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.datatype.DatatypeSupport;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;
import com.blackducksoftware.common.base.ExtraFunctions;

/**
 * An I/O registry that provides Sqlg specific configuration.
 *
 * @author jgustie
 */
public class SqlgIoRegistryBdio extends AbstractIoRegistry {

    private static final SqlgIoRegistryBdio INSTANCE = new SqlgIoRegistryBdio();

    private SqlgIoRegistryBdio() {
        // Basically turn any multi-valued anything into a String[] or Sqlg won't accept it
        register(BlackDuckIo.class, null, new MultiValueCollectorRegistration() {
            @Override
            public Collector<? super Object, ?, ?> collector() {
                return collectingAndThen(mapping(Object::toString, toList()), l -> l.toArray(new String[l.size()]));
            }
        });

        // This is just to make sure Sqlg works (otherwise RecordId wouldn't serialize correctly)
        registerOverride(Bdio.Datatype.Default, DatatypeHandler.from(
                x -> DatatypeSupport.Default().isInstance(x) || x instanceof RecordId,
                DatatypeSupport.Default()::serialize,
                DatatypeSupport.Default()::deserialize));

        // Really this is sqlg specific because TinkerGraph lets any object in
        registerOverride(Bdio.Datatype.Digest, DatatypeHandler.from(
                DatatypeSupport.Digest()::isInstance,
                DatatypeSupport.Digest()::serialize,
                ExtraFunctions.nullSafe(DatatypeSupport.Digest()::deserialize).andThen(Object::toString)));
        registerOverride(Bdio.Datatype.Products, DatatypeHandler.from(
                DatatypeSupport.Products()::isInstance,
                DatatypeSupport.Products()::serialize,
                ExtraFunctions.nullSafe(DatatypeSupport.Products()::deserialize).andThen(Object::toString)));
        registerOverride(Bdio.Datatype.ContentRange, DatatypeHandler.from(
                DatatypeSupport.ContentRange()::isInstance,
                DatatypeSupport.ContentRange()::serialize,
                ExtraFunctions.nullSafe(DatatypeSupport.ContentRange()::deserialize).andThen(Object::toString)));
        registerOverride(Bdio.Datatype.ContentType, DatatypeHandler.from(
                DatatypeSupport.ContentType()::isInstance,
                DatatypeSupport.ContentType()::serialize,
                ExtraFunctions.nullSafe(DatatypeSupport.ContentType()::deserialize).andThen(Object::toString)));
    }

    public static SqlgIoRegistryBdio instance() {
        return INSTANCE;
    }

    /**
     * Helper to register an override for a built-in BDIO datatype.
     */
    private void registerOverride(Bdio.Datatype datatype, DatatypeHandler<?> handler) {
        register(BlackDuckIo.class, null, new DatatypeRegistration() {
            @Override
            public String iri() {
                return datatype.toString();
            }

            @Override
            public DatatypeHandler<?> handler() {
                return handler;
            }
        });
    }

}
