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

import com.blackducksoftware.bdio2.BdioGenerator;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;

/**
 * Tool for dumping the JSON-LD contents of a BDIO file.
 *
 * @author jgustie
 */
public class EntriesTool extends Tool {

    // TODO Configurable per-entry delimiters?

    public static void main(String[] args) {
        new EntriesTool().parseArguments(args).run();
    }

    private ByteSource input;

    public EntriesTool() {
    }

    public void setInput(ByteSource input) {
        this.input = Objects.requireNonNull(input);
    }

    @Override
    protected Tool parseArguments(String[] args) {
        setInput(getInput(Iterables.getFirst(arguments(args), "-")));
        return super.parseArguments(args);
    }

    @Override
    public void execute() throws IOException {
        checkState(input != null, "input is not set");
        new BdioGenerator(input.openBufferedStream()).stream().forEach(this::printJson);
    }

}
