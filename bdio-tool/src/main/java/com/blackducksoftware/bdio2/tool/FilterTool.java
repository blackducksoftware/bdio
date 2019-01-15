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

import static com.blackducksoftware.common.base.ExtraStrings.splitOnFirst;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.MoreCollectors.toOptional;
import static java.util.function.Predicate.isEqual;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

import io.reactivex.Flowable;
import io.reactivex.FlowableTransformer;
import io.reactivex.functions.Predicate;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * Tool for filtering BDIO contents.
 *
 * @author jgustie
 */
public class FilterTool extends Tool {

    public static void main(String[] args) {
        new FilterTool(null).parseArgs(args).run();
    }

    /**
     * Only keeps a "page" of nodes as specified by a limit and offset.
     */
    private static class PageFilter implements FlowableTransformer<Map<String, Object>, Map<String, Object>> {

        // TODO Fundamentally broken because of references!

        private final long start;

        private final long count;

        public PageFilter(long start, long count) {
            checkArgument(count >= 0, "count must be at least zero: %s", count);
            this.start = start;
            this.count = count;
        }

        @Override
        public Publisher<Map<String, Object>> apply(Flowable<Map<String, Object>> upstream) {
            return upstream.skip(start).take(count);
        }
    }

    /**
     * Only keeps nodes with a missing or explicitly specified namespace.
     * <p>
     * <em>IMPORTANT:</em> This filter does NOT consider namespace inheritance!
     */
    private static class NamespaceFilter implements FlowableTransformer<Map<String, Object>, Map<String, Object>> {

        // TODO Fundamentally broken because of references!

        private final ImmutableSet<String> namespaces;

        public NamespaceFilter(Collection<String> namespaces) {
            this.namespaces = ImmutableSet.copyOf(namespaces);
        }

        @Override
        public Publisher<Map<String, Object>> apply(Flowable<Map<String, Object>> upstream) {
            return upstream.filter(n -> !n.containsKey(Bdio.DataProperty.namespace.toString())
                    || namespaces.contains(n.get(Bdio.DataProperty.namespace.toString())));
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
                } else if (root != null && n.containsKey(Bdio.ObjectProperty.base.toString())) {
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
                    root.put(Bdio.ObjectProperty.base.toString(), newNode.get(JsonLdConsts.ID));
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
            if (root.containsKey(Bdio.ObjectProperty.base.toString())) {
                // We found the base directory before the root, adjust the root node
                Map<String, Object> newNode = new LinkedHashMap<>(node);
                newNode.putAll(root);
                root = null;
                return Flowable.just(newNode);
            } else {
                // We found the root before the base directory, save details for later
                root.put(JsonLdConsts.TYPE, node.get(JsonLdConsts.TYPE));
                root.put(JsonLdConsts.ID, node.get(JsonLdConsts.ID));
                return Flowable.just(node);
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

    private ByteSink output;

    private ByteSource input;

    private final List<FlowableTransformer<Map<String, Object>, Map<String, Object>>> filters = new ArrayList<>();

    public FilterTool(@Nullable String name) {
        super(name);
    }

    public void setOutput(ByteSink output) {
        this.output = Objects.requireNonNull(output);
    }

    public void setInput(ByteSource input) {
        this.input = Objects.requireNonNull(input);
    }

    public void addPageFilter(long start, long count) {
        filters.add(new PageFilter(start, count));
    }

    public void addPageFilter(String page) {
        // If only one number was supplied assume it is the count
        filters.add(splitOnFirst(page, ',', (s, c) -> c.isEmpty()
                ? new PageFilter(0, Long.valueOf(s))
                : new PageFilter(Long.valueOf(s), Long.valueOf(c))));
    }

    public void addNamespaceFilter(Collection<String> namespaces) {
        filters.add(new NamespaceFilter(namespaces));
    }

    public void addNamespaceFilter(String namespaces) {
        addNamespaceFilter(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(namespaces));
    }

    public void addSubdirectoryFilter(String directory) {
        filters.add(new SubdirectoryFilter(directory));
    }

    @Override
    protected boolean isOptionWithArgs(String option) {
        return super.isOptionWithArgs(option)
                || option.equals("--output")
                || option.equals("--page-filter")
                || option.equals("--namespace-filter")
                || option.equals("--subdirectory-filter");
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String option : options(args)) {
            if (option.startsWith("--output=")) {
                optionValue(option).map(this::getOutput).ifPresent(this::setOutput);
                args = removeFirst(option, args);
            } else if (option.startsWith("--page-filter=")) {
                optionValue(option).ifPresent(this::addPageFilter);
                args = removeFirst(option, args);
            } else if (option.startsWith("--namespace-filter=")) {
                optionValue(option).ifPresent(this::addNamespaceFilter);
                args = removeFirst(option, args);
            } else if (option.startsWith("--subdirectory-filter=")) {
                optionValue(option).ifPresent(this::addSubdirectoryFilter);
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

        // Read the file twice, first to extract the metadata, then to extract and filter the data
        BdioMetadata metadata;
        try (InputStream in = input.openStream()) {
            metadata = doc.metadata(doc.read(in).takeUntil((Predicate<Object>) doc::needsMoreMetadata)).singleOrError().blockingGet();
        }
        try (InputStream in = input.openStream()) {
            try (StreamSupplier out = getBdioOutput(output)) {
                doc.jsonLd(doc.read(in))
                        .expand().flatMapIterable(x -> x)
                        .flatMapIterable(BdioDocument::toGraphNodes)
                        .flatMap(this::filter)
                        .subscribe(new BdioSubscriber(metadata, out, RxJavaPlugins::onError));
            }
        }
    }

    /**
     * Filters the supplied node, possibly returning an alternative representation.
     */
    private Publisher<Map<String, Object>> filter(Map<String, Object> node) {
        Flowable<Map<String, Object>> filtered = Flowable.just(node);
        for (FlowableTransformer<Map<String, Object>, Map<String, Object>> filter : filters) {
            filtered = filtered.compose(filter);
        }
        return filtered;
    }

}
