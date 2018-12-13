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
package com.blackducksoftware.bdio2.test;

import static com.blackducksoftware.common.base.ExtraStrings.removeSuffix;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toSet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.util.GraphFactory;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParametersFactory;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;

/**
 * A test runner that combines the behavior of both a suite and a parameterized runner. Runner works similar to a
 * parameterized runner with a single {@code Graph} parameter that is initialized using the properties from the
 * {@code @GraphConfiguration} annotation. It also allows {@code @SuiteClasses}, however
 *
 * @author jgustie
 */
public class GraphRunner extends Suite {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    public @interface GraphConfigurations {
        GraphConfiguration[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Repeatable(GraphConfigurations.class)
    public @interface GraphConfiguration {
        public String value();
    }

    /**
     * Holder so we can inherit an existing graph from a parent runner.
     */
    private static ThreadLocal<List<Graph>> PARENT_GRAPHS = new ThreadLocal<>();

    /**
     * Flag indicating the graph was inherited from a parent graph runner.
     */
    private final boolean graphCameFromParent;

    /**
     * The graphs managed by this runner, possibly empty for suites.
     */
    private final List<Graph> graphs;

    /**
     * The children runners.
     */
    private final List<Runner> runners;

    public GraphRunner(Class<?> klass, RunnerBuilder builder) throws InitializationError {
        super(klass, Collections.emptyList());

        SuiteClasses suite = klass.getAnnotation(SuiteClasses.class);
        if (suite != null) {
            // Optionally open a graph an build the children recursively
            graphCameFromParent = false;
            graphs = getGraphs(klass);
            PARENT_GRAPHS.set(graphs);
            try {
                runners = unmodifiableList(builder.runners(klass, suite.value()));
            } finally {
                PARENT_GRAPHS.remove();
            }
        } else {
            // Either inherit a graph or open a new one
            List<Graph> parentGraphs = PARENT_GRAPHS.get();
            if (parentGraphs != null && !parentGraphs.isEmpty()) {
                graphCameFromParent = true;
                graphs = parentGraphs;
            } else {
                graphCameFromParent = false;
                graphs = getGraphs(klass);
                if (graphs.isEmpty()) {
                    throw new InitializationError("There are no graph configurations available");
                }
            }

            // Create a test for each configured graph
            ParametersRunnerFactory runnerFactory = new BlockJUnit4ClassRunnerWithParametersFactory();
            ImmutableList.Builder<Runner> runners = ImmutableList.builderWithExpectedSize(graphs.size());
            for (Map.Entry<String, List<Object>> graph : getTestParameters(graphs).entrySet()) {
                TestWithParameters test = new TestWithParameters(graph.getKey(), getTestClass(), graph.getValue());
                runners.add(runnerFactory.createRunnerForTestWithParameters(test));
            }
            this.runners = runners.build();
        }
    }

    @Override
    protected List<Runner> getChildren() {
        return runners;
    }

    @Override
    public void run(RunNotifier notifier) {
        try {
            super.run(notifier);
        } finally {
            // If we have a graph that was not inherited, close it now
            if (!graphCameFromParent) {
                List<Throwable> errors = new ArrayList<>();
                for (Graph graph : graphs) {
                    try {
                        if (graph.features().graph().supportsTransactions()) {
                            graph.tx().onClose(Transaction.CLOSE_BEHAVIOR.ROLLBACK);
                        }

                        graph.close();
                    } catch (Exception e) {
                        errors.add(e);
                    }
                }
                if (!errors.isEmpty()) {
                    // TODO What should we do here?
                }
            }
        }
    }

    /**
     * Loads zero or more graphs using the configuration from the supplied class.
     */
    @Nullable
    private static List<Graph> getGraphs(Class<?> klass) throws InitializationError {
        ImmutableList.Builder<Graph> result = ImmutableList.builder();
        for (GraphConfiguration annotation : klass.getAnnotationsByType(GraphConfiguration.class)) {
            // Try to load the configuration specified by the annotation
            Configuration configuration;
            try {
                URL resource = klass.getResource(annotation.value());
                if (resource != null) {
                    configuration = new PropertiesConfiguration(resource);
                } else {
                    throw new InitializationError("Configuration resource not found: '" + annotation.value() + "'");
                }
            } catch (ConfigurationException e) {
                throw new InitializationError(e);
            }

            // Try to open a graph using the TinkerPop graph factory
            try {
                result.add(GraphFactory.open(configuration));
            } catch (RuntimeException e) {
                // If loading one graph fails, close all the others
                List<Throwable> errors = new ArrayList<>();
                errors.add(e);
                for (Graph graph : result.build()) {
                    try {
                        graph.close();
                    } catch (Exception ee) {
                        errors.add(ee);
                    }
                }
                throw new InitializationError(errors);
            }
        }
        return result.build();
    }

    /**
     * Returns a map of test names to their (single) parameter lists.
     */
    private static Map<String, List<Object>> getTestParameters(List<Graph> graphs) {
        // Compute default names for all the graphs
        ListMultimap<String, Object> result = LinkedListMultimap.create(graphs.size());
        for (Graph graph : graphs) {
            result.put("[" + graph.getClass().getSimpleName() + "]", graph);
        }

        // Only add the configuration properties name if we have a conflict
        Set<String> conflictingKeys = result.keys().entrySet().stream().filter(e -> e.getCount() > 1).map(e -> e.getElement()).collect(toSet());
        for (String conflictKey : conflictingKeys) {
            for (Object graph : result.get(conflictKey)) {
                String fileName = ((PropertiesConfiguration) ((Graph) graph).configuration()).getFileName();
                String newName = "[" + graph.getClass().getSimpleName() + "-" + removeSuffix(fileName, ".properties") + "]";
                result.put(newName, graph);
            }
            result.removeAll(conflictKey);
        }

        // This will cause failures if any key still has 2 or more entries
        return Multimaps.asMap(result);
    }

}
