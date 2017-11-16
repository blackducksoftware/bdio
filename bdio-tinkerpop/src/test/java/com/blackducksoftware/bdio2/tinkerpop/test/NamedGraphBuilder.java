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
package com.blackducksoftware.bdio2.tinkerpop.test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.model.Component;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.model.FileCollection;
import com.blackducksoftware.bdio2.model.Project;
import com.blackducksoftware.bdio2.test.BdioTest;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;

/**
 * Helper for building up named graphs.
 *
 * @author jgustie
 */
public class NamedGraphBuilder {

    private BdioMetadata metadata;

    private List<BdioObject> graph;

    public NamedGraphBuilder() {
        metadata = BdioMetadata.createRandomUUID();
        graph = new ArrayList<>();
    }

    public NamedGraphBuilder project(Consumer<Project> projectConfiguration) {
        projectConfiguration.andThen(graph::add).accept(new Project(BdioObject.randomId()));
        return this;
    }

    public NamedGraphBuilder fileCollection(Consumer<FileCollection> fileCollectionConfiguration) {
        fileCollectionConfiguration.andThen(graph::add).accept(new FileCollection(BdioObject.randomId()));
        return this;
    }

    public NamedGraphBuilder file(Consumer<File> fileConfiguration) {
        fileConfiguration.andThen(graph::add).accept(new File(BdioObject.randomId()));
        return this;
    }

    public NamedGraphBuilder component(Consumer<Component> componentConfiguration) {
        componentConfiguration.andThen(graph::add).accept(new Component(BdioObject.randomId()));
        return this;
    }

    public <F extends BdioObject, T extends BdioObject> NamedGraphBuilder relateToFirst(Class<F> fromType, BiConsumer<F, T> relation) {
        return doRelate(FluentIterable.from(graph).filter(fromType).iterator(), relation);
    }

    public <F extends BdioObject, T extends BdioObject> NamedGraphBuilder relateToLast(Class<F> fromType, BiConsumer<F, T> relation) {
        return doRelate(FluentIterable.from(Lists.reverse(graph)).skip(1).filter(fromType).iterator(), relation);
    }

    private <F, T> NamedGraphBuilder doRelate(Iterator<F> from, BiConsumer<F, T> relation) {
        if (from.hasNext()) {
            @SuppressWarnings("unchecked") // Just let this fail with a ClassCastException
            T to = (T) graph.get(graph.size() - 1);
            relation.accept(from.next(), to);
        }
        return this;
    }

    public InputStream build() {
        return BdioTest.zipJsonBytes(metadata.asNamedGraph(graph));
    }

}
