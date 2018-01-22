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

import static com.blackducksoftware.common.io.ExtraIO.buffer;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.Charset.defaultCharset;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.ProcessBuilder.Redirect;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.BdioOptions;
import com.blackducksoftware.bdio2.EmitterFactory;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableSet;
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
        this.command = command;
    }

    @Override
    protected Set<String> optionsWithArgs() {
        return ImmutableSet.of("--exec");
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String arg : options(args)) {
            if (arg.startsWith("--exec=")) {
                optionValue(arg).map(cmd -> cmd.split("[ \t\n\r\f]")).ifPresent(this::setCommand);
                args = removeFirst(arg, args);
            } else if (arg.equals("-0")) {
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
            try (Writer out = new OutputStreamWriter(buffer(process.getOutputStream()), defaultCharset())) {
                JsonUtils.write(out, entry);
                out.flush();
            }
            process.waitFor();
        } catch (IOException e) {
            formatException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
