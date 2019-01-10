/*
 * Copyright 2018 Black Duck Software, Inc.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.io.ByteSource;

import io.reactivex.Flowable;
import io.reactivex.functions.Predicate;

/**
 * Print metadata for BDIO files.
 *
 * @author jgustie
 */
public class HeadTool extends Tool {

    public static void main(String[] args) {
        new HeadTool(null).parseArgs(args).run();
    }

    // TODO There should be a separate tool that can generate metadata using environment variables and other information

    private List<ByteSource> inputs = new ArrayList<>();

    private boolean json;

    public HeadTool(String name) {
        super(name);
    }

    public void addInput(ByteSource input) {
        inputs.add(Objects.requireNonNull(input));
    }

    public void setJson(boolean json) {
        this.json = json;
    }

    @Override
    protected void printUsage() {
        printOutput("usage: %s [--json] [file ...]%n", name());
    }

    @Override
    protected void printHelp() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("--json", "Output metadata in JSON");
        printOptionHelp(options);
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String arg : options(args)) {
            if (arg.startsWith("--json")) {
                setJson(true);
                args = removeFirst(arg, args);
            }
        }
        for (String name : arguments(args)) {
            addInput(getInput(name));
        }
        if (inputs.isEmpty()) {
            addInput(getInput("-"));
        }

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        checkState(!inputs.isEmpty(), "input is not set");
        RxJavaBdioDocument document = new RxJavaBdioDocument(new BdioContext.Builder().build());
        Flowable.fromIterable(inputs)
                .map(ByteSource::openStream)
                .map(in -> document.metadata(document.read(in).takeUntil((Predicate<Object>) document::needsMoreMetadata))
                        .reduceWith(BdioMetadata::new, BdioMetadata::merge).blockingGet())
                .subscribe(this::printMetadata)
                .isDisposed();
    }

    protected void printMetadata(BdioMetadata metadata) {
        // TODO Format key/value pairs before printing JSON?
        if (json) {
            printJson(metadata);
        } else {
            metadata.forEach((k, v) -> {
                if (!k.equals(JsonLdConsts.CONTEXT)) {
                    printOutput("%s = %s%n", formatKey(k), formatValue(k, v));
                }
            });
        }
        printOutput("%n%n");
    }

    private static String formatKey(String key) {
        if (key.startsWith("@")) {
            return key.substring(1);
        } else {
            for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
                if (dataProperty.toString().equals(key)) {
                    return dataProperty.name();
                }
            }
        }
        return key;
    }

    private static Object formatValue(String key, Object value) {
        return BdioContext.getDefault().fromFieldValue(key, value);
    }

}
