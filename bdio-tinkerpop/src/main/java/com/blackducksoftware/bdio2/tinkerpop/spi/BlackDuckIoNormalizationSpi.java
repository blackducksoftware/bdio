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
package com.blackducksoftware.bdio2.tinkerpop.spi;

import static com.blackducksoftware.common.base.ExtraCollectors.enumNames;
import static org.apache.tinkerpop.gremlin.process.traversal.P.eq;
import static org.apache.tinkerpop.gremlin.process.traversal.P.within;
import static org.apache.tinkerpop.gremlin.process.traversal.P.without;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.V;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.has;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.hasNot;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.or;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.property;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.select;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;
import com.blackducksoftware.common.base.ExtraStreams;
import com.blackducksoftware.common.value.HID;

public abstract class BlackDuckIoNormalizationSpi extends AbstractBlackDuckIoSpi {

    // TODO Anywhere we use the "name()" we should be going through the context...

    protected BlackDuckIoNormalizationSpi(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        super(traversal, options, frame);
    }

    public void identifyRoot() {
        // We need a root label and a metadata label in order to perform this normalization
        if (!options().rootLabel().isPresent() || !options().metadataLabel().isPresent()) {
            return;
        }

        GraphTraversalSource g = traversal();

        // Drop any existing root edges
        g.E().hasLabel(options().rootLabel().get()).drop().iterate();

        // Get the identifier (technically there should only be one) of the metadata vertex
        Object[] metadataId = g.V().hasLabel(options().metadataLabel().get()).id().toList().toArray();

        // Recreate root edges between the root vertices and metadata vertices
        g.V().hasLabel(within(ExtraStreams.stream(Bdio.Class.class).filter(Bdio.Class::root).collect(enumNames())))
                .where(inE(Bdio.ObjectProperty.subproject.name(), Bdio.ObjectProperty.previousVersion.name()).count().is(0))
                .as("root")
                .V(metadataId).addE(options().rootLabel().get()).to("root")
                .iterate();
    }

    public void addMissingFileParents() {
        // We need a file parent key in order to perform this normalization
        if (!options().fileParentKey().isPresent()) {
            return;
        }

        removeParents();
        createMissingFiles();
        createParentEdges();
    }

    public void addMissingProjectDependencies() {
        // We need a root label in order to perform this normalization
        if (!options().rootLabel().isPresent()) {
            return;
        }

        GraphTraversalSource g = traversal();

        // Create edges from the un-connected components to the root object
        g.E().hasLabel(options().rootLabel().get())
                .inV().as("roots")
                .V().hasLabel(Bdio.Class.Component.name())
                .not(inE(Bdio.ObjectProperty.dependsOn.name()))
                .as("directDependencies")
                .addV(Bdio.Class.Dependency.name())
                .addE(Bdio.ObjectProperty.dependsOn.name()).to("directDependencies")
                .outV()
                .addE(Bdio.ObjectProperty.dependency.name()).from("roots")
                .iterate();
    }

    public void implyFileSystemTypes() {
        updateDirectoryFileSystemTypes();
        updateSymlinkFileSystemTypes(traversal());
        updateRegularFileSystemTypes(traversal());
    }

    protected void removeParents() {
        GraphTraversalSource g = traversal();

        // TODO At some point we need to support explicit parent edges...
        g.E().hasLabel(Bdio.ObjectProperty.parent.name()).drop().iterate();

        // Base files must not have a parent property or we will walk right past them to the root
        g.V().out(Bdio.ObjectProperty.base.name()).properties(options().fileParentKey().get()).drop().iterate();
    }

    protected void createMissingFiles() {
        GraphTraversalSource g = traversal();

        // Loop until we can't find anymore missing files
        long implicitCreation = 1;
        while (implicitCreation > 0) {
            implicitCreation = g.V().hasLabel(Bdio.Class.File.name())
                    .as("f").values(Bdio.DataProperty.path.name()).dedup().aggregate("paths")
                    .select("f").values(options().fileParentKey().get()).where(without("paths"))
                    .dedup().as("path")
                    .addV(Bdio.Class.File.name())
                    .property(Bdio.DataProperty.path.name(), select("path"))
                    .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.DIRECTORY.toString())
                    .sideEffect(t -> {
                        HID.from(t.path("path")).tryParent().map(HID::toUriString).ifPresent(v -> t.get().property(options().fileParentKey().get(), v));
                        options().identifierKey().ifPresent(key -> t.get().property(key, BdioObject.randomId()));
                    })
                    .count().next();
        }
    }

    protected void createParentEdges() {
        GraphTraversalSource g = traversal();

        // Iterate over all the files with a "_parent" property and create the edge back up to the parent
        g.V()
                .hasLabel(Bdio.Class.File.name())
                .as("child").values(options().fileParentKey().get()).as("pp").select("child")
                .addE(Bdio.ObjectProperty.parent.name())
                .to(V().hasLabel(Bdio.Class.File.name())
                        .as("parent").values(Bdio.DataProperty.path.name()).where(eq("pp")).<Vertex> select("parent"))
                .iterate();
    }

    @SuppressWarnings("unchecked")
    protected void updateDirectoryFileSystemTypes() {
        GraphTraversalSource g = traversal();

        g.V().out(Bdio.ObjectProperty.parent.name())
                .or(hasNot(Bdio.DataProperty.fileSystemType.name()),
                        // TODO We should be checking for other types as well
                        has(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.REGULAR.toString()))
                .coalesce(
                        or(has(Bdio.DataProperty.byteCount.name()), has(Bdio.DataProperty.contentType.name()))
                                .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.DIRECTORY_ARCHIVE.toString()),
                        property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.DIRECTORY.toString()))
                .iterate();
    }

    protected void updateSymlinkFileSystemTypes(GraphTraversalSource g) {
        g.V().hasLabel(Bdio.Class.File.name())
                .hasNot(Bdio.DataProperty.fileSystemType.name())
                .has(Bdio.DataProperty.linkPath.name())
                .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.SYMLINK.toString())
                .iterate();
    }

    protected void updateRegularFileSystemTypes(GraphTraversalSource g) {
        g.V().hasLabel(Bdio.Class.File.name())
                .hasNot(Bdio.DataProperty.fileSystemType.name())
                .has(Bdio.DataProperty.encoding.name())
                .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.REGULAR_TEXT.toString())
                .iterate();

        g.V().hasLabel(Bdio.Class.File.name())
                .hasNot(Bdio.DataProperty.fileSystemType.name())
                .property(Bdio.DataProperty.fileSystemType.name(), Bdio.FileSystemType.REGULAR.toString())
                .iterate();
    }

}
