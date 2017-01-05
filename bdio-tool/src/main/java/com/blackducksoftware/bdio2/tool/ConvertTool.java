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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.legacy.ScanContainerEmitter;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

/**
 * Tool for converting legacy formats into BDIO.
 *
 * @author jgustie
 */
public class ConvertTool extends Tool {

    public static void main(String[] args) {
        new ConvertTool().parseArgs(args).run();
    }

    private ByteSource input;

    private ByteSink output;

    public void setInput(ByteSource input) {
        this.input = Objects.requireNonNull(input);
    }

    public void setOutput(ByteSink output) {
        this.output = Objects.requireNonNull(output);
    }

    @Override
    protected Tool parseArguments(String[] args) {
        List<String> arguments = arguments(args);
        setInput(getInput(Iterables.get(arguments, 0, "-")));
        setOutput(getOutput(Iterables.get(arguments, 1, "-")));
        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        OutputStream outputStream = output.openStream();
        InputStream inputStream = input.openStream();

        // Create a new BDIO document using a ScanContainer parser
        BdioDocument document = new BdioDocument.Builder()
                .usingParser(ScanContainerEmitter::new)
                .build(RxJavaBdioDocument.class);

        // The ScanContainerEmitter produces a header entry, so this should be safe?
        document
                .takeFirstMetadata(metadata -> document.writeToFile(metadata, outputStream))
                .read(inputStream);
    }

}
