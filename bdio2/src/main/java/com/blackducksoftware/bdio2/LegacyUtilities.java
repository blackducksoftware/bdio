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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.model.Dependency;
import com.blackducksoftware.bdio2.model.File;
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
     * Estimation of the size in bytes of each node translated from a legacy format. This estimate is based largely on
     * Protex BOM Tool output where each legacy "file" node only contains a few (less then 3) properties.
     * <p>
     * When used to size an array list for all the nodes in an entry, this empirically covers 95% of entries with only a
     * single array list expansion.
     */
    private static final int ESTIMATED_NODE_SIZE = 400;

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
        int averageSize = Bdio.MAX_ENTRY_SIZE / ESTIMATED_NODE_SIZE; // TODO Is this right?
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

    @Nullable
    private static String dependencyIdentifier(ValueObjectMapper valueObjectMapper, Dependency dep) {
        Stream<Object> dependsOn = valueObjectMapper.fromReferenceValueObject(dep.get(Bdio.ObjectProperty.dependsOn.toString()));
        Stream<Object> license = valueObjectMapper.fromReferenceValueObject(dep.get(Bdio.ObjectProperty.license.toString()));
        byte[] name = Stream.concat(dependsOn, license).map(Object::toString).collect(Collectors.joining("><", "<", ">")).getBytes();
        return "urn:uuid:" + UUID.nameUUIDFromBytes(name);
    }

    /**
     * Helper to merge a dependency into a map of dependencies. This is used to reduce the overall number of dependency
     * objects created for similar declarations.
     */
    public static void mergeDependency(Multimap<String, Dependency> dependencies, Dependency dependency) {
        ValueObjectMapper valueObjectMapper = ValueObjectMapper.getContextValueObjectMapper();
        String id = dependencyIdentifier(valueObjectMapper, dependency);
        dependency.put(JsonLdConsts.ID, id);

        // Look for an dependency we can add this to
        for (Dependency dep : dependencies.get(id)) {
            if (dep.keySet().equals(dependency.keySet())) {
                if (dep.containsKey(Bdio.ObjectProperty.declaredBy.toString())) {
                    valueObjectMapper.fromReferenceValueObject(dependency.get(Bdio.ObjectProperty.declaredBy.toString()))
                            .map(Object::toString)
                            .map(File::new)
                            .forEach(dep::declaredBy);
                    return;
                } else if (dep.containsKey(Bdio.ObjectProperty.evidence.toString())) {
                    valueObjectMapper.fromReferenceValueObject(dependency.get(Bdio.ObjectProperty.evidence.toString()))
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
    public static String guessScheme(String filename) {
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

        // Hopefully this won't break anything downstream that is depending on a specific scheme...
        return "unknown";
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

}
