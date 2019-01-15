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
package com.blackducksoftware.bdio2.tool;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.common.io.ExtraIO;
import com.google.common.io.ByteSink;

/**
 * Used to write BDIO JSON-LD to a console.
 *
 * @author jgustie
 */
public class BdioConsoleStreamSupplier extends ByteSink implements StreamSupplier {

    /**
     * The console print stream to emit to.
     */
    private final PrintStream out;

    /**
     * The delimiter to use between entries. Not that the delimiter is given two arguments: the current entry number
     * (starting at -1) and the default entry name.
     */
    private String entryDelimiter;

    /**
     * The number on entries.
     */
    private final AtomicInteger entryCount = new AtomicInteger(-1);

    public BdioConsoleStreamSupplier(PrintStream out) {
        this(out, "%n%n");
    }

    public BdioConsoleStreamSupplier(PrintStream out, String entryDelimiter) {
        this.out = Objects.requireNonNull(out);
        this.entryDelimiter = Objects.requireNonNull(entryDelimiter);
    }

    @Override
    public OutputStream newStream() throws IOException {
        int entryNumber = entryCount.incrementAndGet();
        out.format(entryDelimiter, entryNumber, Bdio.dataEntryName(entryNumber));
        return ExtraIO.ignoreClose(out);
    }

    @Override
    public OutputStream openStream() throws IOException {
        // TODO Block closing?
        return out;
    }

}
