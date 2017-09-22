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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.function.Consumer;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.model.Project;
import com.blackducksoftware.bdio2.test.BdioTest;

/**
 * Helper for building up named graphs.
 *
 * @author jgustie
 */
public class NamedGraphBuilder {

    private BdioMetadata metadata;

    private LinkedList<BdioObject> graph;

    public NamedGraphBuilder() {
        metadata = BdioMetadata.createRandomUUID();
        graph = new LinkedList<>();
        graph.add(new Project(BdioObject.randomId()));
    }

    public NamedGraphBuilder withBaseFile(File base) {
        ((Project) graph.getFirst()).base(base);
        graph.add(base);
        return this;
    }

    public NamedGraphBuilder withProject(Consumer<Project> project) {
        project.accept((Project) graph.getFirst());
        return this;
    }

    public NamedGraphBuilder add(BdioObject object) {
        checkArgument(!(object instanceof Project), "project cannot be added");
        graph.add(object);
        return this;
    }

    public InputStream build() {
        return BdioTest.zipJsonBytes(metadata.asNamedGraph(graph));
    }

}
