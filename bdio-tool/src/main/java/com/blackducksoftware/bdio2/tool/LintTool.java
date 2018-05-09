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
package com.blackducksoftware.bdio2.tool;

import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioOptions;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.blackducksoftware.bdio2.tool.linter.Linter;
import com.blackducksoftware.bdio2.tool.linter.Linter.CompletedGraphRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.LT;
import com.blackducksoftware.bdio2.tool.linter.Linter.LoadedGraphRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.RawEntryRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.RawNodeRule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Rule;
import com.blackducksoftware.bdio2.tool.linter.Linter.Severity;
import com.blackducksoftware.bdio2.tool.linter.Linter.Violation;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;
import com.google.common.io.ByteSource;

/**
 * Check BDIO for common mistakes.
 *
 * @author jgustie
 */
public class LintTool extends AbstractGraphTool {

    public static void main(String[] args) {
        new LintTool(null).parseArgs(args).run();
    }

    /**
     * The rules to evaluated indexed by their simple name.
     */
    private final Map<String, Rule<?>> rules;

    /**
     * The accumulated list of violations.
     */
    private final List<Violation> violations;

    /**
     * The default limit of per-rule violations to report before suppressing.
     */
    private int maxViolations = 5;

    public LintTool(String name) {
        super(name);
        rules = Linter.loadAllRules().collect(toMap(r -> r.getClass().getSimpleName(), r -> r));
        violations = new ArrayList<>();
        graphTool().onGraphLoaded(g -> g.V().forEachRemaining(this::executeWithLoadedGraph));
        graphTool().onGraphInitialized(this::executeWithCompletedGraph);
        graphTool().setProperty("bdio.metadataLabel", LT._Metadata.name());
        graphTool().setProperty("bdio.rootLabel", LT._root.name());
        graphTool().setProperty("bdio.identifierKey", LT._id.name());
        graphTool().setProperty("bdio.unknownKey", LT._unknown.name());
        graphTool().setProperty("bdio.implicitKey", LT._implicit.name());
        graphTool().setProperty("bdio.partitionStrategy.partitionKey", LT._partition.name());
    }

    public void setMaxViolations(int maxViolations) {
        Preconditions.checkArgument(maxViolations >= 0, "max violations must not be negative");
        this.maxViolations = maxViolations;
    }

    @Override
    protected void printUsage() {
        printOutput("usage: %s [--max-violations=N] [file ...]%n", name());
    }

    @Override
    protected void printHelp() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("--max-violations=N", "Maximum number of violations per-rule (default " + maxViolations + ")");
        printOptionHelp(options);
    }

    @Override
    protected Set<String> optionsWithArgs() {
        return ImmutableSet.of("--max-violations");
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        // TODO What syntax do we use for enable/disable?
        // javac: -Xlint/-Xlint:all -Xlint:none -Xlint:name -Xlint:-name

        for (String arg : options(args)) {
            if (arg.startsWith("--max-violations=")) {
                optionValue(arg).map(Integer::valueOf).ifPresent(this::setMaxViolations);
                args = removeFirst(arg, args);
            }
        }

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        // If we have raw data rules, parse the input just for validation
        if (rules.values().stream().anyMatch(r -> r instanceof RawEntryRule || r instanceof RawNodeRule)) {
            Stopwatch readTimer = Stopwatch.createStarted();
            for (Map.Entry<URI, ByteSource> input : graphTool().getInputs().entrySet()) {
                BdioOptions.Builder options = new BdioOptions.Builder();
                options.expandContext(GraphTool.expandContext(input.getKey()));
                RxJavaBdioDocument doc = new RxJavaBdioDocument(options.build());
                doc.read(input.getValue().openStream())
                        .doOnNext(this::executeWithRawEntry)
                        .flatMapIterable(BdioDocument::toGraphNodes)
                        .doOnNext(this::executeWithRawNode)
                        .ignoreElements()
                        .blockingAwait();
            }
            printDebugMessage("Time to read BDIO input: %s%n", readTimer.stop());
        }

        // If we have graph rules, delegate to the super to load the graph
        if (rules.values().stream().anyMatch(r -> r instanceof LoadedGraphRule || r instanceof CompletedGraphRule)) {
            super.execute();
        }

        // Report the number of rules that were executed
        printDebugMessage("%d rules executed%n", rules.size());

        // If we have any violations, report them
        Multiset<Severity> severityCounts = TreeMultiset.create();
        Multiset<Class<?>> ruleCounts = HashMultiset.create();
        for (Violation violation : violations) {
            severityCounts.add(violation.severity());
            int ruleCount = ruleCounts.add(violation.rule().getClass(), 1);
            if (ruleCount < maxViolations) {
                printOutput("%s: %s: [%s] %s%n",
                        violation.target(), violation.severity(),
                        violation.rule().getClass().getSimpleName(), violation.message());
            } else if (ruleCount == maxViolations) {
                printOutput("[%s] limit reached, further occurances of this volation will be suppressed%n", violation.rule().getClass().getSimpleName());
            }
        }

        // Print the summary count
        printOutput("%s%n", severityCounts.entrySet().stream()
                .map(e -> MessageFormat.format("{0,choice,1#1 {1}|1<{0,number,integer} {1}s}", e.getCount(), e.getElement()))
                .collect(joining(String.format("%n"))));

        // Any errors should force a non-success exit status
        if (severityCounts.contains(Severity.error)) {
            throw new ExitException(1);
        }
    }

    @Override
    protected void executeWithGraph(Graph graph) {
        // This hook is for looking at the complete graph, we already looked at each individual input
    }

    protected void executeWithRawEntry(Object entry) {
        rules.values().stream()
                // TODO Sort this so that Metadata runs first
                .flatMap(ofType(RawEntryRule.class))
                .flatMap(rule -> rule.validate(entry))
                .forEachOrdered(violations::add);
    }

    protected void executeWithRawNode(Map<String, Object> node) {
        rules.values().stream()
                .flatMap(ofType(RawNodeRule.class))
                .flatMap(rule -> rule.validate(node))
                .forEachOrdered(violations::add);
    }

    protected void executeWithLoadedGraph(Vertex vertex) {
        rules.values().stream()
                .flatMap(ofType(LoadedGraphRule.class))
                .flatMap(rule -> rule.validate(vertex))
                .forEachOrdered(violations::add);
    }

    protected void executeWithCompletedGraph(GraphTraversalSource g) {
        rules.values().stream()
                .flatMap(ofType(CompletedGraphRule.class))
                .flatMap(rule -> rule.validate(g))
                .forEachOrdered(violations::add);
    }

}
