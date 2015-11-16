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
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Predicates.notNull;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.Set;

import rx.functions.Func1;

import com.blackducksoftware.bom.ImmutableNode;
import com.blackducksoftware.bom.Node;
import com.blackducksoftware.bom.SimpleType;
import com.blackducksoftware.bom.Term;
import com.blackducksoftware.bom.Type;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Schema for converting an array of strings into a node through the use of per-term transformation functions.
 *
 * @author jgustie
 */
public class ArraySchema implements Func1<String[], Node> {

    // Most of the complexity here is just building up the map of terms to Function<String[], Object>; once those
    // mappings are constructed it is relatively easy to use that map to generate a node from an array.

    /**
     * An array schema builder.
     */
    public static final class Builder {
        /**
         * Locale used to format the array contents.
         */
        private final Locale locale;

        /**
         * The map of terms to functions created by this builder.
         */
        private final Map<Term, Function<String[], Object>> functions = new LinkedHashMap<>();

        private Builder(Locale locale) {
            this.locale = checkNotNull(locale);
        }

        public ArraySchema build() {
            return new ArraySchema(functions);
        }

        /**
         * Assign the specified term to the result of a function over the entire input row. Overrides previous
         * selections for the term. Assumes the function returns the appropriate type for the term.
         */
        public Builder addDataRaw(Term term, Function<String[], ?> f) {
            // We don't care what the function returns: it is going to be an object
            functions.put(term, (Function<String[], Object>) f);
            return this;
        }

        /**
         * Assign the specified term to the exact value of the specified column. Overrides previous selections for the
         * term. Assumes the term accepts {@code String} types.
         */
        public Builder addData(Term term, int index) {
            return addDataRaw(term, ArraySchemaFunctions.<String> getAt(index));
        }

        /**
         * Assign the specified term to the transformed value of the specified column. Overrides previous selections for
         * the term. Assumes the transformation returns the appropriate type for the term.
         */
        public Builder addData(Term term, int index, Function<String, ?> f) {
            return addDataRaw(term, Functions.compose(f, ArraySchemaFunctions.<String> getAt(index)));
        }

        /**
         * Shorthand for adding a column using a locale sensitive integer converter.
         */
        public Builder addDataInt(Term term, int index) {
            return addDataRaw(term, ArraySchemaFunctions.integerAt(index, locale));
        }

        /**
         * Shorthand for adding multiple columns joined together as a single string.
         */
        public Builder addDataJoined(Term term, Joiner joiner, int... indices) {
            return addDataRaw(term, ArraySchemaFunctions.join(joiner, indices));
        }

        /**
         * Specify that the identifier for the row is at the specified column. Overrides previous selections for the
         * {@link JsonLdTerm#ID} term.
         * <p>
         * Convenience for calling {@code addData(JsonLdTerm.ID, index)}.
         */
        public Builder idAt(int index) {
            return addData(JsonLdTerm.ID, index);
        }

        /**
         * Specify that the identifier for the row is the transformed value at the specified column. Overrides previous
         * selections for the {@link JsonLdTerm#ID} term.
         * <p>
         * Convenience for calling {@code addColumn(JsonLdTerm.ID, index, f)}.
         */
        public Builder idAt(int index, Function<String, String> f) {
            return addData(JsonLdTerm.ID, index, f);
        }

        /**
         * Specify that the type for the row is at the specified column. Overrides previous selections for the
         * {@link JsonLdTerm#TYPE} term.
         * <p>
         * Convenience for calling {@code addData(JsonLdTerm.TYPE, index, SimpleType.stringConverter())}.
         */
        public Builder typeAt(int index) {
            return addData(JsonLdTerm.TYPE, index, SimpleType.stringConverter());
        }

        /**
         * Specify that the type for the row is the transformed value at the specified column. Overrides previous
         * selections for the {@link JsonLdTerm#TYPE} term.
         * <p>
         * Convenience for calling
         * {@code addData(JsonLdTerm.TYPE, index, Functions.compose(SimpleType.stringConverter(), f))}.
         */
        public Builder typeAt(int index, Function<String, String> f) {
            return addData(JsonLdTerm.TYPE, index, Functions.compose(SimpleType.stringConverter(), f));
        }

        /**
         * Appends the specified types to each row regardless of what the row contains.
         */
        public Builder addTypes(Type... types) {
            if (types.length > 0) {
                Function<? super String[], ?> typesFunction = functions.get(JsonLdTerm.TYPE);
                if (typesFunction != null) {
                    typesFunction = ArraySchemaFunctions.appendOnto(ImmutableList.copyOf(types), typesFunction);
                } else {
                    typesFunction = Functions.constant(ImmutableList.copyOf(types));
                }
                functions.put(JsonLdTerm.TYPE, (Function<String[], Object>) typesFunction);
            }
            return this;
        }
    }

    /**
     * The mapping of terms to the functions that generate their values from an array of strings.
     */
    private final Map<Term, Function<String[], Object>> functions;

    private ArraySchema(Map<Term, Function<String[], Object>> functions) {
        checkArgument(functions.containsKey(JsonLdTerm.ID), "no identifier information");
        checkArgument(functions.containsKey(JsonLdTerm.TYPE), "no type information");
        this.functions = ImmutableMap.copyOf(functions);
    }

    public static Builder builder(Locale locale) {
        return new Builder(locale);
    }

    public static Builder builder() {
        return builder(Locale.getDefault(Category.FORMAT));
    }

    @Override
    public Node call(String[] array) {
        String id = Functions.compose(Functions.toStringFunction(), functions.get(JsonLdTerm.ID)).apply(array);
        Set<Type> types = Functions.compose(ArraySchemaFunctions.toTypeSet(), functions.get(JsonLdTerm.TYPE)).apply(array);
        Map<Term, Object> data = Maps.transformValues(Maps.filterKeys(functions, not(instanceOf(JsonLdTerm.class))), ArraySchemaFunctions.toDataMap(array));
        return ImmutableNode.of(id, types, Maps.filterValues(data, notNull()));
    }
}
