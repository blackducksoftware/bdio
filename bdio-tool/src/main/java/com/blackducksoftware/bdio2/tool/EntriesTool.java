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

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.Charset.defaultCharset;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.BdioOptions;
import com.blackducksoftware.bdio2.EmitterFactory;
import com.blackducksoftware.common.base.ExtraStrings;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;

/**
 * Tool for dumping the JSON-LD contents of a BDIO file.
 *
 * @author jgustie
 */
public class EntriesTool extends Tool {

    public static void main(String[] args) {
        new EntriesTool(null).parseArgs(args).run();
    }

    private ByteSource input;

    private String entryDelimiter = "%n%n";

    @Nullable
    private String[] command;

    public EntriesTool(@Nullable String name) {
        super(name);
    }

    public void setInput(ByteSource input) {
        this.input = Objects.requireNonNull(input);
    }

    public void setEntryDelimiter(String entryDelimiter) {
        this.entryDelimiter = Objects.requireNonNull(entryDelimiter);
    }

    public void setCommand(@Nullable String[] command) {
        this.command = command != null && command.length > 0 ? command : null;
    }

    @Override
    protected void printUsage() {
        printOutput("usage: %s [-0] [--exec <utility> [argument ...] ; ] [file]%n", name());
    }

    @Override
    protected void printHelp() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("-0", "Delimit entries with a NUL (\"\\0\") character.");
        options.put("--exec <utility> [argument ...] ;", "Pipe the contents of each entry into the supplied utility");
        printOptionHelp(options);
    }

    @Override
    protected boolean isOptionWithArgs(String option) {
        return super.isOptionWithArgs(option) || option.equals("--exec");
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        String[][] exec = parseExec(args);
        args = exec[0];
        setCommand(exec[1]);

        for (String arg : options(args)) {
            if (arg.equals("-0")) {
                setEntryDelimiter("\0");
                args = removeFirst(arg, args);
            }
        }

        setInput(getInput(Iterables.getFirst(arguments(args), "-")));
        return super.parseArguments(args);
    }

    @Override
    public void execute() throws IOException {
        checkState(input != null, "input is not set");
        BdioOptions options = new BdioOptions.Builder().build();
        Consumer<Object> action = command != null ? this::execEntry : this::printEntry;
        EmitterFactory.newEmitter(options, input.openBufferedStream()).stream().forEach(action);
    }

    protected void printEntry(Object entry) {
        printJson(entry);
        printOutput(entryDelimiter);
    }

    protected void execEntry(Object entry) {
        try {
            Process process = new ProcessBuilder(command).inheritIO().redirectInput(Redirect.PIPE).start();
            try (Writer out = new OutputStreamWriter(process.getOutputStream(), defaultCharset())) {
                if (isPretty()) {
                    JsonUtils.writePrettyPrint(out, entry);
                } else {
                    JsonUtils.write(out, entry);
                }
                out.flush();
            }
            process.waitFor();
        } catch (IOException e) {
            formatException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Helper to parse an "exec" command out of the arguments. The resulting array will contain two elements, the first
     * is list of arguments with the execution removed, the second is the (possibly empty) execution.
     */
    private static String[][] parseExec(String[] args) {
        int execStart = -1;
        int execEnd = args.length;
        for (int i = 0; i < args.length; ++i) {
            if (args[i].startsWith("--exec=")) {
                execStart = i;
            } else if (args[i].equals(";")) {
                execEnd = i;
            }
        }

        String[][] exec = new String[2][];
        if (execStart >= 0) {
            exec[1] = Arrays.copyOfRange(args, execStart, execEnd);
            exec[1][0] = ExtraStrings.removePrefix(exec[1][0], "--exec=");

            exec[0] = new String[args.length - exec[1].length - (execEnd < args.length ? 1 : 0)];
            System.arraycopy(args, 0, exec[0], 0, execStart);
            if (exec[0].length > execStart) {
                System.arraycopy(args, execEnd + 1, exec[0], execStart, args.length - exec[0].length - exec[1].length);
            }
        } else {
            exec[0] = args;
            exec[1] = new String[0];
        }
        return exec;
    }

}
