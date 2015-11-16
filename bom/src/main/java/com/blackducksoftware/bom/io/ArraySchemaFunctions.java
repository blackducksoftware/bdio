/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 * 
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bom.io;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.blackducksoftware.bom.SimpleType;
import com.blackducksoftware.bom.Type;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * Functions to use with the array schema.
 *
 * @author jgustie
 */
public final class ArraySchemaFunctions {
    private ArraySchemaFunctions() {
    }

    /**
     * @see ArraySchemaFunctions#toTypeSet()
     */
    private static final class ToTypeSet implements Function<Object, Set<Type>> {
        private static final Function<Object, Set<Type>> INSTANCE = new ToTypeSet();

        private static final Function<Object, Type> TO_TYPE = Functions.compose(SimpleType.stringConverter(), Functions.toStringFunction());

        @Override
        public Set<Type> apply(Object input) {
            if (input instanceof Iterable<?>) {
                return ImmutableSet.copyOf(Iterables.transform((Iterable<?>) input, TO_TYPE));
            } else {
                return ImmutableSet.of(TO_TYPE.apply(input));
            }
        }
    }

    /**
     * @see ArraySchemaFunctions#toDataMap(Object)
     */
    private static final class ToDataMap<T, R> implements Function<Function<T, R>, R> {
        private final T input;

        private ToDataMap(T input) {
            this.input = checkNotNull(input);
        }

        @Override
        public R apply(Function<T, R> f) {
            return f.apply(input);
        }
    }

    /**
     * @see ArraySchemaFunctions#getAt(int)
     */
    private static final class ArrayGet<T> implements Function<T[], T> {
        private final int index;

        private ArrayGet(int index) {
            this.index = index;
        }

        @Override
        public T apply(T[] array) {
            return array[index];
        }
    }

    /**
     * @see ArraySchemaFunctions#join(Joiner, int...)
     */
    private static final class ArrayJoin implements Function<String[], String> {
        private final Joiner joiner;

        private final int[] indices;

        private ArrayJoin(Joiner joiner, int[] indices) {
            this.joiner = checkNotNull(joiner);
            this.indices = checkNotNull(indices);
        }

        @Override
        public String apply(String[] input) {
            return joiner.join(pick(Arrays.asList(input), indices));
        }
    }

    /**
     * @see ArraySchemaFunctions#appendOnto(Iterable, Function)
     */
    private static final class Appender implements Function<Object, Object> {
        private final Iterable<?> o;

        private Appender(Iterable<?> o) {
            this.o = checkNotNull(o);
            checkArgument(!Iterables.isEmpty(o));
        }

        @Override
        public Object apply(Object input) {
            Iterable<?> iterableInput;
            if (input instanceof Iterable<?>) {
                iterableInput = (Iterable<?>) input;
            } else {
                iterableInput = Optional.fromNullable(input).asSet();
            }
            Iterable<?> result = Iterables.concat(iterableInput, o);
            return Iterables.size(result) == 1 ? Iterables.getOnlyElement(result) : result;
        }
    }

    /**
     * Function for converting objects to sets of {@code Type} instances. If the object being transformed is iterable,
     * each element will be converted to a type, otherwise the resulting set will be of size one. Objects are
     * transformed into types by pushing their {@code toString} representation through {@code SimpleType}.
     */
    static Function<Object, Set<Type>> toTypeSet() {
        return ToTypeSet.INSTANCE;
    }

    /**
     * Function which applies another function to fixed input. Useful for converting a map of functions into a map of
     * values.
     */
    static <T, R> Function<Function<T, R>, R> toDataMap(T input) {
        return new ToDataMap<>(input);
    }

    /**
     * Returns a function that maps to a single element in an array.
     */
    public static <T> Function<T[], T> getAt(int index) {
        return new ArrayGet<>(index);
    }

    /**
     * Returns a function that maps an array to the output of the joiner being applied to the specified indices.
     * <p>
     * For example:
     *
     * <pre>
     * join(Joiner.on(','), 0, 2).apply(new String[] { &quot;a&quot;, &quot;b&quot;, &quot;c&quot; }).equals(&quot;a,c&quot;);
     * </pre>
     */
    public static Function<String[], String> join(Joiner joiner, int... indices) {
        return new ArrayJoin(joiner, indices);
    }

    /**
     * Returns a function that appends the specified collection to output of another function. If the function returns
     * an iterable, to two iterables are combined into a single iterable. If the result of combining the output with the
     * collection is a single element, this function will only output that element.
     * <p>
     * Any iterables returned by the function are live views over the sources, you probably want to copy them.
     */
    public static Function<? super String[], Object> appendOnto(Iterable<?> c, Function<? super String[], ? extends Object> f) {
        return Functions.compose(new Appender(c), f);
    }

    /**
     * Returns a function that maps an element in a string array to an integer.
     */
    public static Function<String[], Integer> integerAt(final int index, final Locale locale) {
        checkNotNull(locale);
        return new Function<String[], Integer>() {
            @Override
            public Integer apply(String[] input) {
                if (!isNullOrEmpty(input[index])) {
                    try {
                        // Use a new localized formatter to parse the integer
                        // TODO It would be nice to reuse these, but they are not thread safe
                        return NumberFormat.getIntegerInstance(locale).parse(input[index]).intValue();
                    } catch (ParseException e) {
                        // Convert checked parse exception to a runtime exception
                        throw Throwables.propagate(new NumberFormatException().initCause(e));
                    }
                }
                return null;
            }
        };
    }

    /**
     * Returns a string prefixing function.
     */
    public static Function<String, String> prefixConverter(final String prefix) {
        checkNotNull(prefix);
        return new Function<String, String>() {
            @Override
            public String apply(String input) {
                return prefix + input;
            }
        };
    }

    /**
     * Returns a function that discards empty values.
     */
    public static Function<String, String> emptyToNull() {
        return new Function<String, String>() {
            @Override
            public String apply(String input) {
                return Strings.emptyToNull(input);
            }
        };
    }

    /**
     * Returns a function that discards the specified value.
     */
    public static Function<String, String> nullIf(final String value) {
        checkNotNull(value);
        return new Function<String, String>() {
            @Override
            public String apply(String input) {
                return value.equals(input) ? null : input;
            }
        };
    }

    /**
     * Returns a function that replaces the specified value.
     */
    public static Function<String, String> when(final String expected, final String value) {
        return new Function<String, String>() {
            @Override
            public String apply(String input) {
                return Objects.equals(expected, input) ? value : input;
            }
        };
    }

    /**
     * Returns a function that returns one of two values based on an existing value.
     */
    public static Function<String, String> when(final String expected, final String value, final String elseValue) {
        return new Function<String, String>() {
            @Override
            public String apply(String input) {
                return Objects.equals(expected, input) ? value : elseValue;
            }
        };
    }

    /**
     * Returns an iterable over the non-{@code null} elements with the specified indexes.
     */
    private static <T> Iterable<T> pick(Iterable<T> input, int... at) {
        List<T> result = new LinkedList<>();
        for (int i : at) {
            result.add(Iterables.get(input, i, null));
        }
        return result;
    }
}