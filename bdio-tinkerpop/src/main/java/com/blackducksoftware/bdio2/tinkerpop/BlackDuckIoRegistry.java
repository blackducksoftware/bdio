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

import static com.blackducksoftware.common.base.ExtraCollectors.getOnly;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.Optional;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.AbstractIoRegistry;
import org.umlg.sqlg.structure.RecordId;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.datatype.DatatypeSupport;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper.DatatypeHandler;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoMapper.GraphMapperConfigurator;
import com.blackducksoftware.common.base.ExtraFunctions;
import com.blackducksoftware.common.value.ContentRange;
import com.blackducksoftware.common.value.ContentType;
import com.blackducksoftware.common.value.Digest;
import com.blackducksoftware.common.value.ProductList;

public class BlackDuckIoRegistry extends AbstractIoRegistry {

    protected BlackDuckIoRegistry(Configuration configuration) {
        // Give the graph mapper access to the configuration for property based configuration
        register(b -> b.withConfiguration(configuration));

        // Special case a bunch of behavior only for Sqlg
        if (configuration.getString(Graph.GRAPH, "").equals(SqlgGraph.class.getName())) {
            // Basically turn any multi-valued anything into a String[] or Sqlg won't accept it
            register(b -> b.multiValueCollector(size -> {
                if (size == 1) {
                    return collectingAndThen(getOnly(), Optional::get);
                } else {
                    return collectingAndThen(mapping(Object::toString, toList()), l -> l.toArray(new String[l.size()]));
                }
            }));

            // This is just to make sure Sqlg works (otherwise RecordId wouldn't serialize correctly)
            register(BlackDuckIo.class, String.class, DatatypeHandler.from(
                    x -> DatatypeSupport.Default().isInstance(x) || x instanceof RecordId,
                    DatatypeSupport.Default()::serialize,
                    DatatypeSupport.Default()::deserialize));

            // Really this is sqlg specific because TinkerGraph lets any object in
            register(BlackDuckIo.class, Digest.class, DatatypeHandler.from(
                    DatatypeSupport.Digest()::isInstance,
                    DatatypeSupport.Digest()::serialize,
                    ExtraFunctions.nullSafe(DatatypeSupport.Digest()::deserialize).andThen(Object::toString)));
            register(BlackDuckIo.class, ProductList.class, DatatypeHandler.from(
                    DatatypeSupport.Products()::isInstance,
                    DatatypeSupport.Products()::serialize,
                    ExtraFunctions.nullSafe(DatatypeSupport.Products()::deserialize).andThen(Object::toString)));
            register(BlackDuckIo.class, ContentRange.class, DatatypeHandler.from(
                    DatatypeSupport.ContentRange()::isInstance,
                    DatatypeSupport.ContentRange()::serialize,
                    ExtraFunctions.nullSafe(DatatypeSupport.ContentRange()::deserialize).andThen(Object::toString)));
            register(BlackDuckIo.class, ContentType.class, DatatypeHandler.from(
                    DatatypeSupport.ContentType()::isInstance,
                    DatatypeSupport.ContentType()::serialize,
                    ExtraFunctions.nullSafe(DatatypeSupport.ContentType()::deserialize).andThen(Object::toString)));
        }
    }

    // This is really just here so we can invoke register using a lambda
    protected final void register(GraphMapperConfigurator configurator) {
        register(BlackDuckIo.class, null, configurator);
    }

}
