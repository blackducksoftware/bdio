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

import static com.blackducksoftware.common.base.ExtraStrings.removePrefix;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.USER_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.blackducksoftware.common.base.ExtraStrings;
import com.blackducksoftware.common.io.HeapOutputStream;
import com.blackducksoftware.common.net.Hostname;
import com.blackducksoftware.common.value.Product;
import com.blackducksoftware.common.value.ProductList;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.io.ByteSource;
import com.google.common.io.CharStreams;

import io.reactivex.Flowable;

/**
 * Print metadata for BDIO files.
 *
 * @author jgustie
 */
public class HeadTool extends Tool {

    public static void main(String[] args) {
        new HeadTool(null).parseArgs(args).run();
    }

    public enum Format {
        KEY_VALUE,
        JSON,
        ENTRY,
    }

    private List<ByteSource> inputs = new ArrayList<>();

    private Format format = Format.KEY_VALUE;

    private String label;

    public HeadTool(String name) {
        super(name);
    }

    public void addInput(ByteSource input) {
        inputs.add(Objects.requireNonNull(input));
    }

    public void setFormat(Format format) {
        this.format = Objects.requireNonNull(format);
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    protected void printUsage() {
        printOutput("usage: %s [--json|--entry] [--label <URI>] [--env] [file ...]%n", name());
    }

    @Override
    protected void printHelp() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("--json", "Output metadata in JSON");
        options.put("--entry", "Output metadata as a BDIO entry");
        options.put("--label", "Override the named graph label");
        options.put("--env", "Include metadata collected from the environment");
        printOptionHelp(options);
    }

    @Override
    protected boolean isOptionWithArgs(String option) {
        return super.isOptionWithArgs(option) || option.equals("--label");
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String arg : options(args)) {
            if (arg.equals("--json")) {
                setFormat(Format.JSON);
                args = removeFirst(arg, args);
            } else if (arg.equals("--entry")) {
                setFormat(Format.ENTRY);
                args = removeFirst(arg, args);
            } else if (arg.startsWith("--label=")) {
                optionValue(arg).ifPresent(this::setLabel);
                args = removeFirst(arg, args);
            } else if (arg.equals("--env")) {
                addInput(getEnvironmentInput(""));
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

        // TODO How to change the context?
        BdioContext context = new BdioContext.Builder().build();
        RxJavaBdioDocument doc = new RxJavaBdioDocument(context);

        // TODO This ID reference should be done `using`
        AtomicReference<Object> id = new AtomicReference<>(label);
        doc.metadata(Flowable.fromIterable(inputs)
                .flatMap(s -> Flowable.using(s::openStream, in -> ConcatenateTool.readMetadata(doc, in, id), InputStream::close)))
                .singleOrError()
                .subscribe(this::printMetadata)
                .isDisposed();
    }

    protected void printMetadata(BdioMetadata metadata) {
        // Do not include empty strings
        metadata.values().removeIf(Predicate.isEqual(""));

        if (format == Format.ENTRY) {
            // Print out a BDIO entry (a named graph with an empty graph list)
            Objects.requireNonNull(metadata.id(), "missing required named graph label (did you forget to use `--label <URI>`?)");
            printJson(metadata.asNamedGraph());
        } else {
            // Compact the metadata using the default context before printing
            Map<String, Object> compacted = JsonLdProcessor.compact(metadata, Bdio.Context.DEFAULT.toString(), BdioContext.getDefault().jsonLdOptions());
            if (format == Format.JSON) {
                printJson(compacted);
            } else if (format == Format.KEY_VALUE) {
                compacted.forEach((k, v) -> printOutput("%s = %s%n", removePrefix(k, "@"), v));
            } else {
                throw new IllegalStateException("unknown format: " + format);
            }
        }
        printOutput("%n");
    }

    private static ByteSource getEnvironmentInput(String id) {
        return new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                HeapOutputStream buffer = new HeapOutputStream();
                try (Writer writer = new OutputStreamWriter(buffer, UTF_8)) {
                    JsonUtils.write(writer, environment(id).asNamedGraph());
                }
                return buffer.getInputStream();
            }
        };
    }

    public static BdioMetadata environment(String id) {
        BdioMetadata metadata = new BdioMetadata();
        metadata.id(id);
        metadata.creator(USER_NAME.value(), Hostname.get());
        metadata.creationDateTime(ZonedDateTime.now(ZoneId.systemDefault()));
        metadata.platform(ProductList.of(Product.os()));
        // TODO Publisher set to the BDIO version?

        if (env("TRAVIS").map(Boolean::parseBoolean).orElse(false)) {
            env("TRAVIS_BUILD_NUMBER").ifPresent(metadata::buildNumber);
            env("TRAVIS_BUILD_WEB_URL").ifPresent(metadata::buildDetails);
            env("TRAVIS_REPO_SLUG").map(s -> "https://github.com/" + s).ifPresent(metadata::sourceRepository);
            env("TRAVIS_BRANCH").ifPresent(metadata::sourceBranch);
            env("TRAVIS_TAG").ifPresent(metadata::sourceTag);
            env("TRAVIS_COMMIT").ifPresent(metadata::sourceRevision);
        } else if (env("JENKINS_URL").isPresent()) {
            env("BUILD_NUMBER").ifPresent(metadata::buildNumber);
            env("BUILD_URL").ifPresent(metadata::buildDetails);
            env("SVN_REVISION").ifPresent(metadata::sourceRevision);
            env("GIT_URL").ifPresent(metadata::sourceRepository);
            env("GIT_BRANCH").ifPresent(metadata::sourceBranch);
            env("GIT_COMMIT").ifPresent(metadata::sourceRevision);
        } else if (env("CIRCLECI").map(Boolean::parseBoolean).orElse(false)) {
            env("CIRCLE_BUILD_NUM").ifPresent(metadata::buildNumber);
            env("CIRCLE_BUILD_URL").ifPresent(metadata::buildDetails);
            env("CIRCLE_REPOSITORY_URL").ifPresent(metadata::sourceRepository);
            env("CIRCLE_BRANCH").ifPresent(metadata::sourceBranch);
            env("CIRCLE_TAG").ifPresent(metadata::sourceTag);
            env("CIRCLE_SHA1").ifPresent(metadata::sourceRevision);
        } else {
            // If we are not in a CI environment, make some guesses
            env("BUILD_NUMBER").ifPresent(metadata::buildNumber);

            // Is this a Subversion repository?
            exec("svn", "info", "--show-item", "revision").ifPresent(metadata::sourceRevision);

            // Is this a Git repository?
            exec("git", "remote", "get-url", "origin").ifPresent(metadata::sourceRepository);
            exec("git", "rev-parse", "--abbrev-ref", "HEAD").ifPresent(metadata::sourceBranch);
            exec("git", "tag", "--points-at").ifPresent(metadata::sourceTag);
            exec("git", "rev-parse", "HEAD").ifPresent(metadata::sourceRevision);
        }

        return metadata;
    }

    /**
     * Attempts to get the output of a forked command, returning an empty optional if anything fails.
     */
    private static Optional<String> exec(String... command) {
        try {
            Process proc = new ProcessBuilder(command).start();
            if (proc.waitFor() == 0) {
                return ExtraStrings.ofBlank(CharStreams.toString(new InputStreamReader(proc.getInputStream(), Charset.defaultCharset()))).map(String::trim);
            } else {
                return Optional.empty();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

}
