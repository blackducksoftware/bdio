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
import static com.google.common.base.StandardSystemProperty.USER_NAME;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.BdioOptions;
import com.blackducksoftware.bdio2.BdioWriter;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.blackducksoftware.common.net.Hostname;
import com.blackducksoftware.common.value.ProductList;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

import io.reactivex.Flowable;
import io.reactivex.functions.Predicate;

/**
 * Concatenate and convert BDIO files.
 *
 * @author jgustie
 */
public class ConcatenateTool extends Tool {

    public static void main(String[] args) {
        new ConcatenateTool(null).parseArgs(args).run();
    }

    private ByteSink output;

    private List<ByteSource> inputs = new ArrayList<>();

    // TODO Allow the ID to customized
    private Optional<String> identifierOverride = Optional.empty();

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

    public void setIdentifierOverride(@Nullable String identifierOverride) {
        this.identifierOverride = Optional.ofNullable(identifierOverride);
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

        RxJavaBdioDocument document = new RxJavaBdioDocument(new BdioOptions.Builder().build());
        StreamSupplier out = new BdioWriter.BdioFile(output.openStream());

        // Read all the configured inputs into a single sequence of entries
        Flowable<InputStream> data = Flowable.fromIterable(inputs).map(ByteSource::openStream);

        // Only collect limited entries for metadata if possible
        BdioMetadata metadata = document.metadata(data.flatMap(in -> document.read(in).takeUntil((Predicate<Object>) BdioDocument::needsMoreMetadata)))
                .blockingSingle(new BdioMetadata());
        completeMetadata(metadata);

        // Write the all the entries back out using the new metadata
        data.flatMap(document::read).subscribe(document.write(metadata, out));
    }

    private void completeMetadata(BdioMetadata metadata) {
        // Generate metadata specific to the operation we just performed
        BdioMetadata catMetadata = new BdioMetadata();
        catMetadata.creator(USER_NAME.value(), Hostname.get());
        catMetadata.creationDateTime(ZonedDateTime.now());
        catMetadata.publisher(ProductList.of(getProduct()));

        // Cross merge so we don't loose anything
        catMetadata.merge(metadata);
        metadata.merge(catMetadata);

        // Ensure we have an identifier
        identifierOverride.ifPresent(metadata::id);
        if (metadata.id() == null) {
            metadata.id(BdioObject.randomId());
        }
    }

}
