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

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import com.google.common.base.Ascii;

/**
 * An emitter that is backed by an arbitrary {@code Spliterator} of BDIO entries.
 * <p>
 * Generally the first entry emitted should be a "header" entry consisting of metadata and an empty list of nodes for
 * the graph. Subsequent entries should only have the identifier metadata and full lists of nodes.
 *
 * @author jgustie
 */
abstract class SpliteratorEmitter implements Emitter {

    /**
     * The spliterator over the BDIO entries.
     */
    private final Spliterator<Object> entries;

    protected SpliteratorEmitter(Spliterator<Object> entries) {
        this.entries = Objects.requireNonNull(entries);
    }

    @Override
    public void emit(Consumer<Object> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        Objects.requireNonNull(onNext);
        Objects.requireNonNull(onError);
        Objects.requireNonNull(onComplete);
        try {
            if (!entries.tryAdvance(onNext)) {
                onComplete.run();
            }
        } catch (UncheckedIOException e) {
            onError.accept(e.getCause());
        } catch (RuntimeException e) {
            onError.accept(e);
        }
    }

    @Override
    public void dispose() {
        // Right now, this does nothing because all we can do at this point is go out of scope
    }

    @Override
    public Stream<Object> stream() {
        // No point in decomposing through an intermediary spliterator
        return StreamSupport.stream(entries, false);
    }

    /**
     * Partitions a sequence of elements into buckets of specified capacity. The resulting sequence consists of lists
     * such that the sum of the weighing function applied to each element of the list will be strictly less then the
     * supplied capacity.
     */
    protected static <T> Spliterator<List<T>> partition(Spliterator<T> source, long capacity, ToIntFunction<T> weigher) {
        // Define a type to hold the list of elements and the current weight
        class Partition implements Consumer<T> {
            private final List<T> elements = new ArrayList<>(); // TODO Give an initial size

            private long weight;

            @Override
            public void accept(T element) {
                weight += weigher.applyAsInt(element);
                elements.add(element);
            }
        }

        // TODO The source size is obviously an over-estimate of the size...
        return new AbstractSpliterator<List<T>>(source.estimateSize(), source.characteristics()) {
            @Override
            public boolean tryAdvance(Consumer<? super List<T>> action) {
                Partition partition = new Partition();
                while (source.tryAdvance(partition) && partition.weight < capacity) {
                }
                if (partition.elements.isEmpty()) {
                    return false;
                } else {
                    action.accept(partition.elements);
                    return true;
                }
            }
        };
    }

    /**
     * Attempts to <em>estimate</em> the serialized JSON size of an object without incurring too much overhead.
     */
    protected static int estimateSize(@Nullable Object obj) {
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
     * Guesses a scheme based on a file name. We need to attempt this mapping because the original scheme is lost in the
     * legacy encoding, our only chance of reconstructing it is through extension matching.
     */
    protected static String guessScheme(String filename) {
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
                case "ar":
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

}
