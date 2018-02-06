/*
 * Copyright 2018 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2.tool.linter;

import static org.apache.tinkerpop.gremlin.process.traversal.P.neq;
import static org.apache.tinkerpop.gremlin.process.traversal.P.without;

import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.tool.linter.Linter.CompletedGraphRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.blackducksoftware.bdio2.tool.linter.Linter.ViolationBuilder;

public class ImpliedFileSystemTypeConflict implements CompletedGraphRule {

    @Override
    public Stream<Violation> validate(GraphTraversalSource input) {
        ViolationBuilder result = new ViolationBuilder(this);

        // If you have children you should be a directory
        input.V().hasLabel(Bdio.Class.File.name())
                .in(Bdio.ObjectProperty.parent.name())
                .has(Bdio.DataProperty.fileSystemType.name(), without(
                        Bdio.FileSystemType.DIRECTORY.toString(),
                        Bdio.FileSystemType.DIRECTORY_ARCHIVE.toString()))
                .forEachRemaining(v -> result.target(v).error("Parent"));

        // If you have a byte count you should be a regular file or an archive
        input.V().hasLabel(Bdio.Class.File.name())
                .has(Bdio.DataProperty.byteCount.name())
                .has(Bdio.DataProperty.fileSystemType.name(), without(
                        Bdio.FileSystemType.REGULAR.toString(),
                        Bdio.FileSystemType.REGULAR_BINARY.toString(),
                        Bdio.FileSystemType.REGULAR_TEXT.toString(),
                        Bdio.FileSystemType.DIRECTORY_ARCHIVE.toString()))
                .forEachRemaining(v -> result.target(v).error("ByteCount"));

        // If you have a link path, you should have a type of symlink
        input.V().hasLabel(Bdio.Class.File.name())
                .has(Bdio.DataProperty.linkPath.name())
                .has(Bdio.DataProperty.fileSystemType.name(), neq(Bdio.FileSystemType.SYMLINK.toString()))
                .forEachRemaining(v -> result.target(v).error("LinkPath"));

        // If you have an encoding, you should have a type of regular/text
        input.V().hasLabel(Bdio.Class.File.name())
                .has(Bdio.DataProperty.encoding.name())
                .has(Bdio.DataProperty.fileSystemType.name(), neq(Bdio.FileSystemType.REGULAR_TEXT.toString()))
                .forEachRemaining(v -> result.target(v).error("Encoding"));

        return result.build();
    }

}
