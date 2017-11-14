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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioObject;
import com.blackducksoftware.bdio2.BdioOptions;
import com.blackducksoftware.bdio2.BdioWriter;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.blackducksoftware.common.value.ProductList;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableSet;
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
    private Optional<String> id = Optional.empty();

    public ConcatenateTool(@Nullable String name) {
        super(name);
    }

    @Override
    protected Set<String> optionsWithArgs() {
        return ImmutableSet.of("--output");
    }

    public void setOutput(ByteSink output) {
        this.output = Objects.requireNonNull(output);
    }

    public void addInput(ByteSource input) {
        inputs.add(Objects.requireNonNull(input));
    }

    public void setId(@Nullable String id) {
        this.id = Optional.ofNullable(id);
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String option : options(args)) {
            if (option.startsWith("--output=")) {
                optionValue(option).map(Tool::getOutput).ifPresent(this::setOutput);
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

        BdioMetadata metadata = new BdioMetadata();
        metadata.id(id.orElseGet(BdioObject::randomId));
        metadata.producer(ProductList.of(getProduct()));
        metadata.creationDateTime(ZonedDateTime.now());
        metadata.creator(StandardSystemProperty.USER_NAME.value());

        // Read all the configured inputs into a single sequence of entries
        Flowable<InputStream> data = Flowable.fromIterable(inputs).map(ByteSource::openStream);

        // Only collect limited entries for metadata if possible
        document.metadata(data.flatMap(in -> document.read(in).takeUntil((Predicate<Object>) ConcatenateTool::needsMoreMetadata)))
                .subscribe(m -> combineMetadata(metadata, m))
                .isDisposed();

        // Write the all the entries back out using the new metadata
        data.flatMap(document::read).subscribe(document.write(metadata, out));
    }

    /**
     * We can stop processing metadata if we are looking at a legacy format.
     */
    private static boolean needsMoreMetadata(Object entry) {
        if (entry instanceof Map<?, ?>) {
            String key = Bdio.DataProperty.producer.toString();
            Object value = ((Map<?, ?>) entry).get(key);
            if (value != null) {
                ProductList products = (ProductList) ValueObjectMapper.getContextValueObjectMapper().fromFieldValue(key, value);
                if (products.tryFind(p -> p.name().equals("LegacyScanContainerEmitter") || p.name().equals("LegacyBdio1xEmitter")).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void combineMetadata(BdioMetadata catMetadata, BdioMetadata otherMetadata) {
        // TODO Merge other into cat (can't use BdioMetadata.merge because of @id conflicts)
    }

}
