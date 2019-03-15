/*
 * Copyright 2019 Synopsys, Inc.
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

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioFrame;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.blackducksoftware.bdio2.rxjava.RxJavaJsonLdProcessing;
import com.blackducksoftware.common.base.ExtraEnums;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

import io.reactivex.Flowable;

/**
 * Perform JSON-LD operations.
 *
 * @author jgustie
 */
public class JsonLdTool extends Tool {

    public static void main(String[] args) {
        new JsonLdTool(null).parseArgs(args).run();
    }

    /**
     * The supported JSON-LD operations.
     */
    public enum Operation {

        IDENTITY, COMPACT, EXPAND, FLATTEN, FRAME;

        /**
         * Check if this operation supports an argument.
         */
        private boolean hasArgument() {
            return this == COMPACT || this == FLATTEN || this == FRAME;
        }

        /**
         * Applies this operation to the supplied JSON-LD processing instance, optionally consuming the argument if
         * necessary (not all operations require an argument).
         */
        private Flowable<?> apply(RxJavaJsonLdProcessing jsonld, @Nullable Object arg) {
            switch (this) {
            case IDENTITY:
                return jsonld.identity();
            case COMPACT:
                return jsonld.compact(arg);
            case EXPAND:
                return jsonld.expand();
            case FLATTEN:
                return jsonld.flatten(arg);
            case FRAME:
                return jsonld.frame(arg);
            default:
                throw new IllegalArgumentException("unknown operation: " + this);
            }
        }
    }

    /**
     * The JSON-LD operation to perform.
     */
    private Operation operation = Operation.IDENTITY;

    /**
     * Additional argument to pass to the operation, expressed as a function of the expand context.
     */
    private Function<BdioContext, Object> operationArgument;

    /**
     * The expansion context used to initially expand the raw input.
     */
    private Object expandContext;

    private ByteSource input;

    private ByteSink output;

    public JsonLdTool(String name) {
        super(name);
    }

    /**
     * Set the operation to perform.
     * <p>
     * Note: invoking this method will clear the operation argument.
     */
    public void setOperation(Operation operation) {
        Objects.requireNonNull(operation);
        if (this.operation != operation) {
            operationArgument = null;
        }
        this.operation = operation;
    }

    /**
     * The operation argument is required for compaction, flattening or framing. For compaction and flattening, the
     * argument is the context used to compact the results; for framing, the argument is the frame to use.
     */
    public void setOperationArgument(@Nullable Object operationArgument) {
        this.operationArgument = operationArgument != null ? x -> operationArgument : null;
    }

    /**
     * Invoke this method to have a JSON-LD frame constructed for BDIO data using the expansion context. For example,
     * the frame will include the BDIO types.
     */
    public void generateFrameFromExpandContext() {
        checkState(operation == Operation.FRAME, "must first set operation to FRAME");
        operationArgument = ctx -> new BdioFrame.Builder().context(ctx).build();
    }

    /**
     * Perform compaction (or flattening) using the same context used for expansion.
     */
    public void useExpandContextForCompaction() {
        checkState(operation == Operation.COMPACT || operation == Operation.FLATTEN, "must first set operation to COMPACT or FLATTEN");
        operationArgument = ctx -> ctx.jsonLdOptions().getExpandContext();
    }

    /**
     * The initial step in a JSON-LD operation is to expand (normalize) the data using an expansion context. If the
     * input is not fully expanded, e.g. it is plain JSON data or is JSON-LD that does not embed a reference to it's own
     * context, then the expansion context should be explicitly set.
     * <p>
     * The expansion context can be a list, a context map or a string representing a URL of a context. Additionally, the
     * special token {@code bdio} is recognized.
     */
    public void setExpandContext(Object expandContext) {
        if (Objects.equals(expandContext, "bdio")) {
            this.expandContext = Bdio.Context.DEFAULT;
        } else {
            this.expandContext = Objects.requireNonNull(expandContext);
        }
    }

    public void setInput(ByteSource input) {
        this.input = Objects.requireNonNull(input);
    }

    public void setOutput(ByteSink output) {
        this.output = Objects.requireNonNull(output);
    }

    @Override
    protected boolean isOptionWithArgs(String option) {
        return super.isOptionWithArgs(option) || option.equals("--output") || option.equals("--context");
    }

    @Override
    protected void printUsage() {
        printOutput("usage: %s [--context=bdio|<uri>] [--output=<file>]%n", name());
        printOutput("          [expand|compact [<context>]|flatten [<context>]|frame [<frame>]]%n");
        printOutput("          [file]%n");
    }

    @Override
    protected void printHelp() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("--context", "The JSON-LD expansion context");
        options.put("--output", "File to write results to");
        printOptionHelp(options);
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String option : options(args)) {
            if (option.startsWith("--output=")) {
                optionValue(option).map(this::getOutput).ifPresent(this::setOutput);
                args = removeFirst(option, args);
            } else if (option.startsWith("--context=")) {
                optionValue(option).ifPresent(this::setExpandContext);
                args = removeFirst(option, args);
            }
        }

        List<String> arguments = arguments(args);
        int i = 0;

        if (i < arguments.size()) {
            ExtraEnums.search(Operation.class)
                    .ignoringCase().byName(arguments.get(i++))
                    .ifPresent(this::setOperation);
        }
        if (operation.hasArgument()) {
            if (i < arguments.size()) {
                // TODO Resolve file path to URI
                setOperationArgument(URI.create(arguments.get(i++)).toString());
            }
            if (operationArgument == null) {
                if (operation == Operation.FRAME) {
                    generateFrameFromExpandContext();
                } else if (operation == Operation.COMPACT || operation == Operation.FLATTEN) {
                    useExpandContextForCompaction();
                }
            }
        }

        if (i < arguments.size()) {
            // TODO Capture the file name to compute the expand context later if we need to
            setInput(getInput(arguments.get(i++)));
        }
        if (input == null) {
            setInput(getInput("-"));
        }
        if (output == null) {
            setOutput(getOutput("-"));
        }

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        // Argument is optional for flatten
        if (operation == Operation.COMPACT && operationArgument == null) {
            throw new IllegalStateException("missing context");
        } else if (operation == Operation.FRAME && operationArgument == null) {
            throw new IllegalStateException("missing frame");
        }

        // TODO This needs to do what `GraphTool.getExpandContext` does
        BdioContext context = new BdioContext.Builder().expandContext(this.expandContext).build();
        RxJavaBdioDocument doc = new RxJavaBdioDocument(context);
        Object arg = operationArgument != null ? operationArgument.apply(context) : null;

        try (InputStream in = input.openStream()) {
            try (Writer out = output.asCharSink(UTF_8).openStream()) {
                operation.apply(doc.jsonLd(doc.read(in)), arg).blockingForEach(e -> {
                    if (isPretty()) {
                        JsonUtils.writePrettyPrint(out, e);
                    } else {
                        JsonUtils.write(out, e);
                    }
                });
                // TODO Do we need an entry delimiter?
            }
        }
    }

}
