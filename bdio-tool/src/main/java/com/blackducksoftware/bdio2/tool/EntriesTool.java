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

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.util.Objects;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.BdioOptions;
import com.blackducksoftware.bdio2.EmitterFactory;
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

    // TODO Configurable per-entry delimiters?
    private String entryDelimiter = "%n%n";

    public EntriesTool(@Nullable String name) {
        super(name);
    }

    public void setInput(ByteSource input) {
        this.input = Objects.requireNonNull(input);
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        setInput(getInput(Iterables.getFirst(arguments(args), "-")));
        return super.parseArguments(args);
    }

    @Override
    public void execute() throws IOException {
        checkState(input != null, "input is not set");
        BdioOptions options = new BdioOptions.Builder().build();
        EmitterFactory.newEmitter(options, input.openBufferedStream()).stream().forEach(this::printEntry);
    }

    protected void printEntry(Object entry) {
        printJson(entry);
        printOutput(entryDelimiter);
    }

}
