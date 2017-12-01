/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio2.tool;

import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static java.util.stream.Collectors.toMap;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.google.common.base.Stopwatch;
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

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        // TODO What syntax do we use for enable/disable?
        // javac: -Xlint/-Xlint:all -Xlint:none -Xlint:name -Xlint:-name

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        // If we have raw data rules, parse the input just for validation
        if (rules.values().stream().anyMatch(r -> r instanceof RawEntryRule || r instanceof RawNodeRule)) {
            Stopwatch readTimer = Stopwatch.createStarted();
            for (Map.Entry<URI, ByteSource> input : graphTool().getInputs().entrySet()) {
                BdioOptions.Builder options = new BdioOptions.Builder();
                GraphTool.setContentType(input.getKey(), options::forContentType);
                RxJavaBdioDocument doc = new RxJavaBdioDocument(options.build());
                doc.read(input.getValue().openStream())
                        .doOnNext(this::executeWithRawEntry)
                        .flatMapIterable(BdioDocument::toGraphNodes)
                        .doOnNext(this::executeWithRawNode)
                        .subscribe();
            }
            printDebugMessage("Time to read BDIO input: %s%n", readTimer.stop());
        }

        // If we have graph rules, delegate to the super to load the graph
        if (rules.values().stream().anyMatch(r -> r instanceof LoadedGraphRule || r instanceof CompletedGraphRule)) {
            super.execute();
        }

        // If we have any violations, report them
        Multiset<Severity> violationCounts = TreeMultiset.create();
        for (Violation violation : violations) {
            violationCounts.add(violation.rule().severity());
            printOutput("%s: %s: [%s] %s%n",
                    violation.context(), violation.rule().severity(),
                    violation.rule().getClass().getSimpleName(), violation.formatMessage());
        }
        if (!violationCounts.isEmpty()) {
            printOutput(violationCounts.entrySet().stream()
                    .map(e -> MessageFormat.format("{0,choice,1#1 {1}|1<{0,number,integer} {1}s}", e.getCount(), e.getElement()))
                    .collect(Collectors.joining("%n", "", "%n")));
        }
        printDebugMessage("%d rules executed%n", rules.size());
    }

    @Override
    protected void executeWithGraph(Graph graph) {
        // This hook is for looking at the complete graph, we already looked at each individual input
    }

    protected void executeWithRawEntry(Object entry) {
        rules.values().stream()
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
