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
package com.blackducksoftware.bdio2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.US;
import static java.util.stream.Collectors.joining;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.model.Dependency;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.common.base.ExtraUUIDs;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Ascii;
import com.google.common.collect.Multimap;

/**
 * Utilities used to aid in the conversion of legacy formats.
 *
 * @author jgustie
 */
class LegacyUtilities {

    /**
     * This module makes the configured object mapper behave like the one used to parse legacy scan container objects.
     */
    public static final class LegacyScanContainerModule extends Module {
        public static final LegacyScanContainerModule INSTANCE = new LegacyScanContainerModule();

        private LegacyScanContainerModule() {
        }

        @Override
        public String getModuleName() {
            return getClass().getSimpleName();
        }

        @Override
        public com.fasterxml.jackson.core.Version version() {
            return com.fasterxml.jackson.core.Version.unknownVersion();
        }

        @Override
        public void setupModule(SetupContext context) {
            ((ObjectMapper) context.getOwner()).enable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
            ((ObjectMapper) context.getOwner()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }
    }

    /**
     * Estimation of the size in bytes of each node translated from a legacy format. This estimate is based largely on
     * Protex BOM Tool output where each legacy "file" node only contains a few (less then 3) properties.
     * <p>
     * When used to size an array list for all the nodes in an entry, this empirically covers 95% of entries with only a
     * single array list expansion.
     */
    private static final int ESTIMATED_NODE_SIZE = 400;

    /**
     * Object mapper used for parsing legacy scan container objects.
     */
    private static final ObjectMapper SCAN_CONTAINER_OBJECT_MAPPER = new ObjectMapper().registerModule(LegacyScanContainerModule.INSTANCE);

    /**
     * UUID name space identifier to use for name based UUIDs generated from legacy emitters.
     */
    // This is a version 3 UUID using the URL name space on the name
    // "https://blackducksoftware.github.io/bdio#LegacyEmitter"
    private static final UUID LEGACY_EMITTER_NS = ExtraUUIDs.fromString("d4bb9cdd-d89c-3a42-af03-393e0be722e4");

    /**
     * UUID name space identifier to use for name based UUIDs representing a legacy dependency mapping.
     */
    private static final UUID DEPENDENCY_IDENTIFIER_NS = ExtraUUIDs.fromString("24ac915b-3be8-4a7d-8840-11fe8e84b680");

    /**
     * Returns a Jackson object mapper configured to parse legacy scan container objects.
     */
    public static ObjectMapper scanContainerObjectMapper() {
        return SCAN_CONTAINER_OBJECT_MAPPER;
    }

    /**
     * Provides an estimate for the number of nodes that will fit in an entry.
     */
    public static int averageEntryNodeCount() {
        return Bdio.MAX_ENTRY_SIZE / ESTIMATED_NODE_SIZE;
    }

    /**
     * Partitions a stream of nodes into a stream of lists of nodes where the number of nodes in each list stays within
     * the BDIO entry size limits.
     */
    public static Stream<List<Map<String, Object>>> partitionNodes(BdioMetadata metadata, Stream<Map<String, Object>> nodes) {
        int maxSize = Bdio.MAX_ENTRY_SIZE - estimateEntryOverhead(metadata);
        int averageSize = Bdio.MAX_ENTRY_SIZE / ESTIMATED_NODE_SIZE;
        return StreamSupport.stream(partition(nodes.spliterator(), averageSize, maxSize, LegacyUtilities::estimateSize), false);
    }

    /**
     * Attempts to <em>estimate</em> the serialized JSON size of an object without incurring too much overhead.
     */
    public static int estimateSize(@Nullable Object obj) {
        // NOTE: It is better to over estimate then under estimate. String sizes are inflated 10% to account for UTF-8
        // encoding and we count a delimiter for every collection element (even the last).
        if (obj == null) {
            return 4; // "null"
        } else if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString().length(); // Accounts for things like negative numbers
        } else if (obj instanceof List<?>) {
            int size = 2; // "[]"
            for (Object item : (List<?>) obj) {
                size += 1 + estimateSize(item); // <item> ","
            }
            return size;
        } else if (obj instanceof Map<?, ?>) {
            int size = 2; // "{}"
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                size += 1 + estimateSize(entry.getKey()); // <key> ":"
                size += 1 + estimateSize(entry.getValue()); // <value> ","
            }
            return size;
        } else {
            // `StandardCharsets.UTF_8.newEncoder().averageBytesPerChar() == 1.1`
            return 2 + (int) (1.1 * obj.toString().length()); // '"' <obj> '"'
        }
    }

    /**
     * Provides an estimate of the per-entry overhead needed given the supplied metadata.
     */
    public static int estimateEntryOverhead(BdioMetadata metadata) {
        // The per-entry overhead is 20 bytes plus the size of the identifier: `{"@id":<ID>,"@graph":[<NODES>]}`
        return 20 + estimateSize(metadata.id());
    }

    /**
     * Helper to merge a dependency into a map of dependencies. This is used to reduce the overall number of dependency
     * objects created for similar declarations.
     */
    public static void mergeDependency(Multimap<String, Dependency> dependencies, Dependency dependency) {
        BdioContext context = BdioContext.getActive();
        String id = dependencyIdentifier(context, dependency);
        dependency.put(JsonLdConsts.ID, id);

        // Look for an dependency we can add this to
        for (Dependency dep : dependencies.get(id)) {
            if (dep.keySet().equals(dependency.keySet())) {
                if (dep.containsKey(Bdio.ObjectProperty.declaredBy.toString())) {
                    context.getFieldValue(Bdio.ObjectProperty.declaredBy.toString(), dependency)
                            .map(Object::toString)
                            .map(File::new)
                            .forEach(dep::declaredBy);
                    return;
                } else if (dep.containsKey(Bdio.ObjectProperty.evidence.toString())) {
                    context.getFieldValue(Bdio.ObjectProperty.evidence.toString(), dependency)
                            .map(Object::toString)
                            .map(File::new)
                            .forEach(dep::evidence);
                    return;
                }
            }
        }

        // If we fell through, just add the dependency
        dependencies.put(id, dependency);
    }

    /**
     * Guesses a scheme based on a file name. We need to attempt this mapping because the original scheme is lost in the
     * legacy encoding, our only chance of reconstructing it is through extension matching.
     */
    public static String guessScheme(@Nullable String filename) {
        if (filename != null) {
            // Scan backwards through the file name trying to match extensions (case-insensitively)
            // NOTE: we do not differentiate the extension from the base name, e.g. "zip.foo" WILL match as "zip"
            int start, end;
            start = end = filename.length();
            while (start > 0) {
                char c = filename.charAt(--start);
                if (c == '/') {
                    // We've hit the end of the filename
                    break;
                } else if (c == '.') {
                    switch (Ascii.toLowerCase(filename.substring(start + 1, end))) {
                    // This list was taken from the old-old legacy code which used to only match extensions
                    case "zip":
                    case "bz":
                    case "z":
                    case "nupg":
                    case "xpi":
                    case "egg":
                    case "jar":
                    case "war":
                    case "rar":
                    case "apk":
                    case "ear":
                    case "car":
                    case "nbm":
                        return "zip";
                    case "rpm":
                        return "rpm";
                    case "tar":
                    case "tgz":
                    case "txz":
                    case "tbz":
                    case "tbz2":
                        return "tar";
                    case "a":
                    case "ar":
                    case "deb":
                    case "lib":
                        return "ar";
                    case "arj":
                        return "arj";
                    case "7z":
                        return "sevenZ";
                    default:
                        // Keep looking
                        end = start;
                    }
                }
            }
        }

        // Hopefully this won't break anything downstream that is depending on a specific scheme...
        return "unknown";
    }

    /**
     * Attempt to construct a file URI using the supplied parts, falling back to the base directory.
     */
    public static String toFileUri(@Nullable String hostName, @Nullable String baseDir, @Nullable String fragment) {
        try {
            return new URI("file", hostName, baseDir, fragment).toASCIIString();
        } catch (URISyntaxException e) {
            return fragment != null ? baseDir + '#' + fragment : baseDir;
        }
    }

    /**
     * Legacy formats expected the BOM name (when used) to uniquely identify the graph, this method creates a URI to use
     * as the graph label from the BOM name.
     */
    public static String toNameUri(String name) {
        // IMPORTANT: This logic is permanent
        return ExtraUUIDs.toUriString(ExtraUUIDs.nameUUIDFromBytes(LEGACY_EMITTER_NS, name.toLowerCase(US).getBytes(UTF_8)));
    }

    /**
     * Partitions a sequence of elements into buckets of specified capacity. The resulting sequence consists of lists
     * such that the sum of the weighing function applied to each element of the list will be strictly less then the
     * supplied maximum weight. The average weight is used to estimate the size of the resulting spliterator.
     */
    private static <T> Spliterator<List<T>> partition(Spliterator<T> source, int averageWeight, long maxWeight, ToIntFunction<T> weigher) {
        // Use the supplied capacity and average weight to estimate the size (we can no longer claim "sized")
        int averagePartitionSize = Math.toIntExact(maxWeight / averageWeight);
        long estimatedSize = source.estimateSize() / averagePartitionSize;
        int characteristics = source.characteristics() & ~(Spliterator.SIZED | Spliterator.SUBSIZED);

        // Define a type to hold the list of elements and the current weight
        class Partition implements Consumer<T> {
            private final List<T> elements = new ArrayList<>(averagePartitionSize);

            private long weight;

            @Override
            public void accept(T element) {
                weight += weigher.applyAsInt(element);
                elements.add(element);
            }
        }

        // Create a spliterator which fills up partition instances from the source spliterator
        return new AbstractSpliterator<List<T>>(estimatedSize, characteristics) {
            @Override
            public boolean tryAdvance(Consumer<? super List<T>> action) {
                Partition partition = new Partition();
                while (source.tryAdvance(partition) && partition.weight < maxWeight) {}
                if (partition.elements.isEmpty()) {
                    return false;
                } else {
                    action.accept(partition.elements);
                    return true;
                }
            }
        };
    }

    @Nullable
    private static String dependencyIdentifier(BdioContext context, Dependency dep) {
        Stream<?> dependsOn = context.getFieldValue(Bdio.ObjectProperty.dependsOn.toString(), dep);
        Stream<?> license = context.getFieldValue(Bdio.ObjectProperty.license.toString(), dep);
        byte[] name = Stream.concat(dependsOn, license).map(Object::toString).collect(joining(">,<", "<", ">")).getBytes(UTF_8);
        return ExtraUUIDs.toUriString(ExtraUUIDs.nameUUIDFromBytes(DEPENDENCY_IDENTIFIER_NS, name));
    }

}
