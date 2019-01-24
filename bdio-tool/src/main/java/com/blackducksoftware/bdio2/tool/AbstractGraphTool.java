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
package com.blackducksoftware.bdio2.tool;

import java.io.File;
import java.net.URI;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

import com.google.common.io.ByteSource;

/**
 * Base class for tools that want to load BDIO data into a graph for processing. Internally a {@link GraphTool} instance
 * is
 * used to load the graph.
 *
 * @author jgustie
 */
public abstract class AbstractGraphTool extends Tool {

    /**
     * The graph tool is used internally to handle loading BDIO into a graph.
     */
    private final GraphTool graphTool;

    protected AbstractGraphTool(String name) {
        super(name);
        graphTool = new GraphTool(name);
        graphTool.setGraph(TinkerGraph.class.getName());
        graphTool.setInitializeSchema(true);
        graphTool.onGraphComplete(this::executeWithGraph);
    }

    /**
     * Provides direct access to the graph tool for sub-class configuration.
     */
    protected final GraphTool graphTool() {
        return graphTool;
    }

    public void addInput(@Nullable URI id, ByteSource input) {
        graphTool().addInput(id, input);
    }

    public void addInput(File file) {
        graphTool().addInput(file);
    }

    public void setExpandContext(Object expandContext) {
        graphTool().setExpandContext(expandContext);
    }

    @Override
    public void setVerbosity(Level verbosity) {
        super.setVerbosity(verbosity);
        graphTool().setVerbosity(verbosity);
    }

    @Override
    protected String formatException(Throwable failure) {
        return graphTool().formatException(failure);
    }

    @Override
    protected boolean isOptionWithArgs(String option) {
        return super.isOptionWithArgs(option) || option.equals("--context");
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String arg : options(args)) {
            if (arg.startsWith("--context=")) {
                setExpandContext(arg);
                args = removeFirst(arg, args);
            }
        }

        boolean hasInput = false;
        for (String name : arguments(args)) {
            addInput(new File(name).toURI(), getInput(name));
            hasInput = true;
        }
        if (!hasInput) {
            addInput(null, getInput("-"));
        }

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        graphTool().execute();
    }

    protected abstract void executeWithGraph(Graph graph);

}
