/*
 * Copyright 2019 Synopsys, Inc.
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

import static com.blackducksoftware.common.base.ExtraStrings.afterFirst;
import static com.blackducksoftware.common.base.ExtraStrings.beforeFirst;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.MoreCollectors.toOptional;
import static java.util.function.Predicate.isEqual;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.reactivestreams.Publisher;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioContext;
import com.blackducksoftware.bdio2.BdioDocument;
import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.BdioSubscriber;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.bdio2.rxjava.RxJavaBdioDocument;
import com.blackducksoftware.bdio2.tool.linter.Linter.RawNodeRule;
import com.blackducksoftware.common.value.HID;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

import io.reactivex.Flowable;
import io.reactivex.FlowableTransformer;
import io.reactivex.Single;
import io.reactivex.SingleTransformer;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * Tool for filtering BDIO contents.
 *
 * @author jgustie
 */
public class FilterTool extends Tool {

    // TODO A "non-standard" filter that keeps or removes anything not part of standard BDIO
    // TODO A node type filter that keeps or removes specific nodes types

    public static void main(String[] args) {
        new FilterTool(null).parseArgs(args).run();
    }

    /**
     * A filter that rewrites a document metadata using an external command.
     */
    private static class MetadataFilter implements SingleTransformer<BdioMetadata, BdioMetadata> {

        private final String command;

        public MetadataFilter(String command) {
            this.command = Objects.requireNonNull(command);
        }

        @Override
        public Single<BdioMetadata> apply(Single<BdioMetadata> upstream) {
            return upstream.flatMap(o -> {
                // Compact, filter and expand
                JsonLdOptions opts = BdioContext.getDefault().jsonLdOptions();
                Map<String, Object> compacted = JsonLdProcessor.compact(o, Bdio.Context.DEFAULT.toString(), opts);
                Object filtered = filter(compacted);
                List<Object> expanded = JsonLdProcessor.expand(filtered, opts);
                if (expanded != null) {
                    // The expanded output should be a single element list
                    return Flowable.fromIterable(expanded).singleOrError();
                } else {
                    // If the expanded output is null it means the filter execution aborted the operation
                    return Single.error(new RuntimeException("aborted"));
                }
            }).map(this::toMetadata);
        }

        protected Object filter(Map<String, Object> compacted) {
            // TODO Intercept some commands and optimize them
            // e.g. `jq 'del(.captureInterval)'` can just remove 'captureInterval' from the map and return it
            return execute(command, compacted);
        }

        @SuppressWarnings("unchecked")
        protected BdioMetadata toMetadata(Object obj) {
            return new BdioMetadata((Map<String, Object>) obj);
        }
    }

    /**
     * Only keeps a "page" of nodes as specified by a limit and offset. This filter does not maintain referential
     * integrity and is therefore only suitable for splitting larger documents into smaller pieces which must be later
     * re-combined to form a valid document.
     */
    private static class PageFilter implements FlowableTransformer<Map<String, Object>, Map<String, Object>> {

        private final long start;

        private final long count;

        public PageFilter(long start, long count) {
            this.start = start;
            this.count = count;
        }

        @Override
        public Publisher<Map<String, Object>> apply(Flowable<Map<String, Object>> upstream) {
            if (count == 0L) {
                return upstream.ignoreElements().toFlowable();
            } else if (count < 0L) {
                return upstream.skip(start);
            } else {
                return upstream.skip(start).limit(count);
            }
        }
    }

    /**
     * Only keep files from the given subdirectory. The result will contain that directory as its base.
     */
    private static class SubdirectoryFilter implements FlowableTransformer<Map<String, Object>, Map<String, Object>> {

        /**
         * The new base path, essentially empty.
         */
        private static final HID BASE = new HID.Builder().push("file", "").build();

        /**
         * The directory being retained in the output.
         */
        private final HID directory;

        /**
         * Intermediate root node state. Because we are changing the base directory, we need to update the root object
         * node to reflect the change. This map can be in the following states:
         * <ul>
         * <li>Empty, indicating we have not encountered the root object or the base directory</li>
         * <li>Contains the type and identifier of the root object, indicating we have encountered the root object but
         * not the base directory</li>
         * <li>Contains the base node identifier, indicating we have encountered the base directory but not the root
         * object</li>
         * <li>{@literal null}, indicating the root object has been successfully updated</li>
         * </ul>
         */
        private Map<String, Object> root = new LinkedHashMap<>();

        public SubdirectoryFilter(String directory) {
            this.directory = HID.from(directory);

            // Since the BASE path is hard coded to be "file:///", we need the input to match
            checkArgument(Objects.equals(this.directory.getBase().getScheme(), "file"),
                    "invalid subdirectory filter specification: %s", directory);
        }

        @Override
        public Flowable<Map<String, Object>> apply(Flowable<Map<String, Object>> upstream) {
            return upstream.flatMap(n -> {
                if (RawNodeRule.types(n).anyMatch(isEqual(Bdio.Class.File.toString()))) {
                    return filterFile(n);
                } else if (n.containsKey(Bdio.ObjectProperty.base.toString())) {
                    return filterRoot(n);
                } else {
                    return Flowable.just(n);
                }
            });
        }

        private Flowable<Map<String, Object>> filterFile(Map<String, Object> node) {
            HID filteredPath = BdioContext.getDefault().getFieldValue(Bdio.DataProperty.path, node)
                    .map(HID::from).flatMap(this::filter).findFirst().orElse(null);

            if (filteredPath != null) {
                Map<String, Object> newNode = new LinkedHashMap<>(node);
                newNode.put(Bdio.DataProperty.path.toString(), filteredPath.toUriString());

                // If this node has new base path, update the root node accordingly
                if (root != null && filteredPath.equals(BASE)) {
                    Map<String, Object> value = new LinkedHashMap<>(ImmutableMap.of(JsonLdConsts.ID, newNode.get(JsonLdConsts.ID)));
                    root.put(Bdio.ObjectProperty.base.toString(), value);
                    if (root.containsKey(JsonLdConsts.TYPE) && root.containsKey(JsonLdConsts.ID)) {
                        Flowable<Map<String, Object>> result = Flowable.just(root, newNode);
                        root = null;
                        return result;
                    }
                }

                // Return the new node with the updated path
                return Flowable.just(newNode);
            } else {
                // Discard files which are not in the filtered directory
                return Flowable.empty();
            }
        }

        private Flowable<Map<String, Object>> filterRoot(Map<String, Object> node) {
            if (root != null) {
                Map<String, Object> newNode = new LinkedHashMap<>(node);
                if (root.containsKey(Bdio.ObjectProperty.base.toString())) {
                    // We found the base directory before the root, adjust the root node
                    newNode.putAll(root);
                    root = null;
                } else {
                    // We found the root before the base directory, save details for later
                    newNode.remove(Bdio.ObjectProperty.base.toString());
                    root.put(JsonLdConsts.TYPE, node.get(JsonLdConsts.TYPE));
                    root.put(JsonLdConsts.ID, node.get(JsonLdConsts.ID));
                }
                return Flowable.just(newNode);
            } else {
                Map<String, Object> newNode = new LinkedHashMap<>(node);
                newNode.remove(Bdio.ObjectProperty.base.toString());
                return Flowable.just(newNode);
            }
        }

        private Stream<HID> filter(HID path) {
            if (path.equals(directory)) {
                // We want to keep the directory itself (which is NOT an ancestor!)
                return Stream.of(BASE);
            } else if (path.isAncestor(directory)) {
                // Rebase descendants of the filtered directory
                return Stream.of(path.getRebased(directory, BASE));
            } else {
                // Drop files that are not within the directory
                return Stream.empty();
            }
        }
    }

    /**
     * Some filters will produce empty nodes. This filter will remove nodes consisting of only an identifier and type.
     * This is potentially dangerous as logically empty nodes may still be referenced from elsewhere in the document.
     */
    private static class PruneEmptyFilter implements FlowableTransformer<Map<String, Object>, Map<String, Object>> {

        private final boolean enabled;

        public PruneEmptyFilter(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public Publisher<Map<String, Object>> apply(Flowable<Map<String, Object>> upstream) {
            if (enabled) {
                return upstream.filter(n -> n.size() > 2 || !n.containsKey(JsonLdConsts.ID) || !n.containsKey(JsonLdConsts.TYPE));
            } else {
                return upstream;
            }
        }
    }

    private static <U, T, D> SingleTransformer<U, D> compose(
            SingleTransformer<? super U, ? extends T> first,
            SingleTransformer<? super T, ? extends D> second) {
        return u -> u.compose(first).compose(second);
    }

    private ByteSink output;

    private ByteSource input;

    private SingleTransformer<BdioMetadata, BdioMetadata> metadataFilter = m -> m;

    // TODO Instead of a list should we just compose the transformers themselves into a single transformer?
    private final List<FlowableTransformer<Map<String, Object>, Map<String, Object>>> filters = new ArrayList<>();

    private boolean pruneEmpty;

    public FilterTool(@Nullable String name) {
        super(name);
    }

    public void setOutput(ByteSink output) {
        this.output = Objects.requireNonNull(output);
    }

    public void setInput(ByteSource input) {
        this.input = Objects.requireNonNull(input);
    }

    public void addMetadataFilter(String command) {
        metadataFilter = compose(metadataFilter, new MetadataFilter(command));
    }

    public void addPageFilter(long start, long count) {
        filters.add(new PageFilter(start, count));
    }

    public void addPageFilter(String page) {
        long start;
        long count;
        if (page.indexOf(',') < 0) {
            start = Long.parseLong(page);
            count = -1L;
        } else {
            start = Long.parseLong(beforeFirst(page, ','));
            count = Long.parseLong(afterFirst(page, ','));
        }
        addPageFilter(start, count);
    }

    public void addSubdirectoryFilter(String directory) {
        filters.add(new SubdirectoryFilter(directory));
    }

    public void setPruneEmpty(boolean pruneEmpty) {
        this.pruneEmpty = pruneEmpty;
    }

    @Override
    protected void printUsage() {
        printOutput("usage: %s [--output <file>]%n", name());
        printOutput("          [--metadata-filter <command>]%n");
        printOutput("          [--page-filter <start>[,<count>]]%n");
        printOutput("          [--subdirectory-filter <directory>]%n");
        printOutput("          [--prune-empty]%n");
        printOutput("          [file]%n");
        printOutput("%n");
    }

    @Override
    protected void printHelp() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("--output=<file>", "Specify a file to write results");
        options.put("--metadata-filter=<command>", "This is the filter for rewriting metadata.");
        options.put("--page-filter=<start>[,<count>]", "Only keeps a \"page\" of nodes as specified by a limit and offset.");
        options.put("--subdirectory-filter=<directory>", "Only keep files from the given subdirectory. The result will contain that directory as its base.");
        options.put("--prune-empty", "Some filters will produce empty nodes. This option removes nodes with only a type and identifier.");
        printOptionHelp(options);
    }

    @Override
    protected boolean isOptionWithArgs(String option) {
        return super.isOptionWithArgs(option)
                || option.equals("--output")
                || option.equals("--metadata-filter")
                || option.equals("--page-filter")
                || option.equals("--subdirectory-filter");
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String option : options(args)) {
            if (option.startsWith("--output=")) {
                optionValue(option).map(this::getOutput).ifPresent(this::setOutput);
                args = removeFirst(option, args);
            } else if (option.startsWith("--metadata-filter=")) {
                optionValue(option).ifPresent(this::addMetadataFilter);
                args = removeFirst(option, args);
            } else if (option.startsWith("--page-filter=")) {
                optionValue(option).ifPresent(this::addPageFilter);
                args = removeFirst(option, args);
            } else if (option.startsWith("--subdirectory-filter=")) {
                optionValue(option).ifPresent(this::addSubdirectoryFilter);
                args = removeFirst(option, args);
            } else if (option.equals("--prune-empty")) {
                setPruneEmpty(true);
                args = removeFirst(option, args);
            }
        }

        if (output == null) {
            setOutput(getOutput("-"));
        }

        arguments(args).stream().collect(toOptional()).map(this::getInput).ifPresent(this::setInput);
        if (input == null) {
            setInput(getInput("-"));
        }

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        checkState(output != null, "missing output");
        checkState(input != null, "missing input");

        // TODO How to change the context?
        BdioContext context = new BdioContext.Builder().build();
        RxJavaBdioDocument doc = new RxJavaBdioDocument(context);

        BdioMetadata metadata;
        try (InputStream in = input.openStream()) {
            metadata = doc.metadata(doc.read(in).takeUntil((io.reactivex.functions.Predicate<Object>) doc::needsMoreMetadata))
                    .singleOrError()
                    .compose(metadataFilter)
                    .blockingGet();
        }

        try (InputStream in = input.openStream()) {
            try (StreamSupplier out = getBdioOutput(output)) {
                doc.jsonLd(doc.read(in))
                        .expand().flatMapIterable(x -> x)
                        .flatMapIterable(BdioDocument::toGraphNodes)
                        .compose(this::filter)
                        .subscribe(new BdioSubscriber(metadata, out, RxJavaPlugins::onError));
            }
        }
    }

    /**
     * Filters the supplied node, possibly returning alternative representations.
     */
    private Publisher<Map<String, Object>> filter(Flowable<Map<String, Object>> nodes) {
        Flowable<Map<String, Object>> filtered = nodes;
        for (FlowableTransformer<Map<String, Object>, Map<String, Object>> filter : filters) {
            filtered = filtered.compose(filter);
        }
        return filtered.compose(new PruneEmptyFilter(pruneEmpty));
    }

    /**
     * Executes a native filter command to modify the supplied input serialized as JSON.
     * <p>
     * Note that due to performance overhead, it is unlikely that you would want to use this
     * on anything but full entries or metadata.
     */
    private static Object execute(String filter, Object input) {
        Objects.requireNonNull(input);
        try {
            // TODO Should we be setting BDIO_XXX environment variables?
            Process proc = new ProcessBuilder(commandArgument(filter)).start();
            try (Writer out = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream(), Charset.defaultCharset()))) {
                JsonUtils.write(out, input);
            }
            if (proc.waitFor() != 0) {
                return null;
            }
            return JsonUtils.fromInputStream(proc.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

}
