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
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An emitter that is backed by an arbitrary {@code Spliterator} of BDIO entries.
 * <p>
 * Generally the first entry emitted should be
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

}
