/*
 * Copyright 2017 Black Duck Software, Inc.
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.reactivestreams.Publisher;

import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

import io.reactivex.Flowable;
import io.reactivex.functions.Predicate;

/**
 * Concatenate BDIO files.
 *
 * @author jgustie
 */
public class ConcatenateTool extends Tool {

    public static void main(String[] args) {
        new ConcatenateTool(null).parseArgs(args).run();
    }

    private ByteSink output;

    private List<ByteSource> inputs = new ArrayList<>();

    public ConcatenateTool(@Nullable String name) {
        super(name);
    }

    @Override
    protected boolean isOptionWithArgs(String option) {
        return super.isOptionWithArgs(option) || option.equals("--output");
    }

    public void setOutput(ByteSink output) {
        this.output = Objects.requireNonNull(output);
    }

    public void addInput(ByteSource input) {
        inputs.add(Objects.requireNonNull(input));
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String option : options(args)) {
            if (option.startsWith("--output=")) {
                optionValue(option).map(this::getOutput).ifPresent(this::setOutput);
                args = removeFirst(option, args);
            }
        }
        if (output == null) {
            setOutput(getOutput("-"));
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

        // TODO How to change the context?
        BdioContext context = new BdioContext.Builder().build();
        RxJavaBdioDocument doc = new RxJavaBdioDocument(context);

        // TODO This ID reference should be done `using`
        AtomicReference<Object> id = new AtomicReference<>();
        BdioMetadata metadata = doc.metadata(Flowable.fromIterable(inputs)
                .flatMap(s -> Flowable.using(s::openStream, in -> readMetadata(doc, in, id), InputStream::close)))
                .singleOrError().blockingGet();

        try (StreamSupplier out = getBdioOutput(output)) {
            Flowable.fromIterable(inputs)
                    .flatMap(s -> Flowable.using(s::openStream, doc::read, InputStream::close))
                    .subscribe(doc.write(metadata, out));
        }
    }

    /**
     * Reads only the BDIO entries necessary for metadata extraction.
     */
    private Publisher<Object> readMetadata(RxJavaBdioDocument doc, InputStream in, AtomicReference<Object> id) {
        // TODO This logic needs to be shared with the `HeadTool`
        return doc.read(in).takeUntil((Predicate<Object>) doc::needsMoreMetadata)
                .map(e -> {
                    if (e instanceof Map<?, ?>) {
                        // Make sure all of the entries have the same identifier
                        @SuppressWarnings("unchecked")
                        Map<String, Object> ng = (Map<String, Object>) e;
                        if (!id.compareAndSet(null, ng.get(JsonLdConsts.ID))) {
                            ng.replace(JsonLdConsts.ID, id.get());
                        }
                    }
                    return e;
                });
    }

}
