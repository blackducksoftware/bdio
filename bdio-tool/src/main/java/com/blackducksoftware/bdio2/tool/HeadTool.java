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
import java.util.List;
import java.util.Objects;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioOptions;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
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
        RxJavaBdioDocument document = new RxJavaBdioDocument(new BdioOptions.Builder().build());
        Flowable.fromIterable(inputs)
                .map(ByteSource::openStream)
                .map(in -> document.metadata(document.read(in).takeUntil((Predicate<Object>) BdioDocument::needsMoreMetadata))
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
                printOutput("%s = %s%n", formatKey(k), formatValue(k, v));
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
        ValueObjectMapper valueObjectMapper = ValueObjectMapper.getContextValueObjectMapper();
        return valueObjectMapper.fromFieldValue(key, value);
    }

}
