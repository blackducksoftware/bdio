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

import static com.google.common.collect.MoreCollectors.toOptional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;
import org.javatuples.Pair;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.BdioValueMapper;
import com.github.jsonldjava.core.RemoteDocument;

/**
 * Produces the frame used to serialize the JSON-LD data to the graph topology. Every node in the framed output is
 * mapped to a vertex using the compacted terms as labels and property names.
 *
 * @author jgustie
 */
public final class BlackDuckIoMapper implements Mapper<BdioFrame> {

    private final Supplier<BdioFrame> frame;

    private BlackDuckIoMapper(BlackDuckIoMapper.Builder builder) {
        // Check the registries for a value mapper
        Optional<BdioValueMapper> valueMapper = builder.registries.stream()
                .flatMap(registry -> registry.find(BlackDuckIo.class, BdioValueMapper.class).stream())
                .map(Pair::getValue1)
                .collect(toOptional());

        // References from the builder
        Optional<BlackDuckIoVersion> version = Optional.ofNullable(builder.version);
        BdioFrame existingFrame = builder.existingFrame;
        UnaryOperator<BdioContext.Builder> contextConfig = Objects.requireNonNull(builder.context);
        UnaryOperator<BdioFrame.Builder> frameConfig = Objects.requireNonNull(builder.frame);

        // Apply the default context
        if (existingFrame == null) {
            Object expandContext = version.map(BlackDuckIoVersion::expandContext).orElse(Bdio.Context.DEFAULT);
            contextConfig = andThen(b -> b.expandContext(expandContext), contextConfig);
        }

        // Determine where we should start our builders from
        Supplier<BdioContext.Builder> newContextBuilder = existingFrame != null
                ? existingFrame.context()::newBuilder
                : BdioContext.Builder::new;
        Supplier<BdioFrame.Builder> newFrameBuilder = existingFrame != null
                ? existingFrame::newBuilder
                : BdioFrame.Builder::new;

        // Create a frame supplier that incorporate the initial builder states and the new configurations
        Supplier<BdioContext> context = andThen(contextConfig, b -> valueMapper.map(b::valueMapper).orElse(b)).apply(newContextBuilder.get())::build;
        this.frame = andThen(frameConfig, b -> b.context(context.get())).apply(newFrameBuilder.get())::build;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BdioFrame createMapper() {
        return frame.get();
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder implements Mapper.Builder<Builder> {

        /**
         * The list of {@code IoRegistry} instances added by the actual graph implementation
         * (or us acting on the behalf of the graph implementation).
         */
        private final List<IoRegistry> registries = new ArrayList<>();

        private BlackDuckIoVersion version;

        private UnaryOperator<BdioContext.Builder> context = UnaryOperator.identity();

        private UnaryOperator<BdioFrame.Builder> frame = UnaryOperator.identity();

        private BdioFrame existingFrame;

        private Builder() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Builder addRegistry(IoRegistry registry) {
            registries.add(Objects.requireNonNull(registry));
            return this;
        }

        public Builder version(BlackDuckIoVersion version) {
            this.version = Objects.requireNonNull(version);
            return this;
        }

        /**
         * Injects the supplied remote document so it is available for offline use while expanding JSON-LD.
         */
        public Builder injectDocument(RemoteDocument document) {
            context = andThen(context, b -> b.injectDocument(document));
            return this;
        }

        /**
         * Updates the set of identifiers used for classes and indicates which classes should be included as vertices.
         */
        public Builder classDetails(Collection<String> classes, Collection<String> embeddedTypes) {
            context = andThen(context, b -> b.embeddedTypes(embeddedTypes));
            frame = andThen(frame, b -> b.classes(classes));
            return this;
        }

        /**
         * Sets the JSON-LD context (and base URL) used to frame JSON-LD into the graph topology.
         */
        public Builder context(@Nullable String base, @Nullable Object expandContext) {
            context = andThen(context, b -> b.base(base).expandContext(expandContext));
            return this;
        }

        /**
         * Make the resulting {@code BlackDuckIoMapper} construct frames from an existing starting point.
         */
        public Builder fromExistingFrame(BdioFrame existingFrame) {
            this.existingFrame = Objects.requireNonNull(existingFrame);
            return this;
        }

        public BlackDuckIoMapper create() {
            return new BlackDuckIoMapper(this);
        }
    }

    // Strange...andThen does not narrow it's return type
    private static <T> UnaryOperator<T> andThen(UnaryOperator<T> a, UnaryOperator<T> b) {
        return a.andThen(b)::apply;
    }

}
