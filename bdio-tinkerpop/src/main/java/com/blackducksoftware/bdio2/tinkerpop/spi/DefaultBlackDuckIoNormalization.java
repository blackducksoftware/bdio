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

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoOptions;

final class DefaultBlackDuckIoNormalization extends BlackDuckIoNormalizationSpi {

    /**
     * Cached transaction support flag.
     */
    private final boolean supportsTransactions;

    public DefaultBlackDuckIoNormalization(GraphTraversalSource traversal, BlackDuckIoOptions options, BdioFrame frame) {
        super(traversal, options, frame);
        this.supportsTransactions = graph().features().graph().supportsTransactions();
    }

    @Override
    public void identifyRoot() {
        tx(() -> super.identifyRoot());
    }

    @Override
    public void addMissingFileParents() {
        tx(() -> super.addMissingFileParents());
    }

    @Override
    public void addMissingProjectDependencies() {
        tx(() -> super.addMissingProjectDependencies());
    }

    @Override
    public void implyFileSystemTypes() {
        tx(() -> super.implyFileSystemTypes());
    }

    private void tx(Runnable task) {
        if (supportsTransactions) {
            graph().tx().open();
            try {
                task.run();
                graph().tx().commit();
            } catch (RuntimeException | Error e) {
                graph().tx().rollback();
                throw e;
            }
        } else {
            task.run();
        }
    }

}
