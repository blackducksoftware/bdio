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
package com.blackducksoftware.bdio2.tinkerpop.sqlg;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.io.AbstractIoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.umlg.sqlg.structure.RecordId;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.StandardJavaValueMapper;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIo;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoNormalizationSpi;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoReaderSpi;
import com.blackducksoftware.bdio2.tinkerpop.spi.BlackDuckIoSpi;

public class SqlgBlackDuckIo extends BlackDuckIoSpi {

    private static final SqlgBlackDuckIo INSTANCE = new SqlgBlackDuckIo();

    private static final SqlgIoRegistryBdio REGISTRY = new SqlgIoRegistryBdio();

    public static final class SqlgIoRegistryBdio extends AbstractIoRegistry {
        private SqlgIoRegistryBdio() {
            register(BlackDuckIo.class, null, new SqlgValueMapper());
        }
    }

    public static final class SqlgValueMapper extends StandardJavaValueMapper {
        private SqlgValueMapper() {
        }

        @Override
        protected Object fromValue(Bdio.Datatype datatype, Object value) {
            if (datatype == Bdio.Datatype.Default && value instanceof RecordId) {
                // Additional support for the Sqlg identifier type
                return value.toString();
            } else if (datatype == Bdio.Datatype.ContentRange
                    || datatype == Bdio.Datatype.ContentType
                    || datatype == Bdio.Datatype.Digest
                    || datatype == Bdio.Datatype.Products) {
                // Sqlg cannot handle these objects, just return the string representation
                return super.fromValue(datatype, value).toString();
            } else {
                return super.fromValue(datatype, value);
            }
        }

        @Override
        public Collector<? super Object, ?, ?> getCollector(String container) {
            if (determineStandardContainer(container) != Bdio.Container.single) {
                // Sqlg expects multi-valued properties to be in string arrays
                return collectingAndThen(mapping(o -> Objects.toString(o, null), toList()),
                        l -> l.stream().allMatch(Objects::isNull) ? null : l.toArray(new String[l.size()]));
            } else {
                return super.getCollector(container);
            }
        }

        @Override
        public Stream<?> split(Object value) {
            // Reverse the array representation created by the collector
            if (value != null && value.getClass().isArray()) {
                return Arrays.stream((Object[]) value);
            } else {
                return Stream.of(value); // TODO Or use the super?
            }
        }
    }

    public static SqlgBlackDuckIo getInstance() {
        return INSTANCE;
    }

    private SqlgBlackDuckIo() {
    }

    @Override
    public Consumer<Mapper.Builder<?>> onMapper(@SuppressWarnings("rawtypes") Consumer<Mapper.Builder> onMapper) {
        Consumer<Mapper.Builder<?>> bdioRegistry = b -> b.addRegistry(REGISTRY);
        return onMapper != null ? bdioRegistry.andThen(onMapper::accept) : bdioRegistry;
    }

    @Override
    protected Optional<BlackDuckIoReaderSpi> providerReader(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame,
            int batchSize) {
        if (options.identifierKey().isPresent()) {
            return Optional.of(new SqlgBlackDuckIoReader(traversal, options, frame, batchSize));
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Optional<BlackDuckIoNormalizationSpi> providerNormalization(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        return Optional.of(new SqlgBlackDuckIoNormalization(traversal, options, frame));
    }

}
