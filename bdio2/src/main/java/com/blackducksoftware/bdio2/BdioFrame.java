/*
 * Copyright 2018 Synopsys, Inc.
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

import static com.blackducksoftware.common.base.ExtraStreams.fromOptional;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.blackducksoftware.common.base.ExtraStreams;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableSet;

/**
 * A frame used to create an alternate presentation of BDIO data.
 *
 * @author jgustie
 */
public final class BdioFrame {

    private final BdioContext context;

    private final ImmutableSet<String> classes;

    private final ImmutableSet<String> classTerms;

    private BdioFrame(Builder builder) {
        this.context = Objects.requireNonNull(builder.context);
        this.classes = ImmutableSet.copyOf(builder.classes);

        // Keep the inverse term used for each of the classes
        this.classTerms = this.classes.stream().flatMap(fromOptional(context::lookupTerm)).collect(toImmutableSet());
    }

    public BdioContext context() {
        return context;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> result = context.serialize();
        result.put(JsonLdConsts.TYPE, classes.stream().filter(c -> !context.isEmbedded(c)).collect(toList()));

        context().getPrefixes(false).forEach((term, iri) -> {
            if (isObjectProperty(term)) {
                Map<String, Object> frameFlags = new LinkedHashMap<>();
                frameFlags.put(JsonLdConsts.EMBED, Boolean.FALSE);
                result.put(iri, frameFlags);
            }
        });

        return result;
    }

    /**
     * Tests to see if the specified term represents a data property.
     */
    public boolean isDataProperty(String term) {
        // TODO Is there false positives for common prefixes? e.g. `isDataProperty("xsd") == true`?
        // TODO There ARE data properties that have a type of "@id", e.g. the "AnyURI" types...
        // Have AnyURI be a BDIO type that is just "@id" so we can record them?
        return !isKeyword(term)
                && term.indexOf(':') < 0
                && !classTerms.contains(term)
                && !Objects.equals(context().getTypeMapping(term), JsonLdConsts.ID);
    }

    /**
     * Tests to see if the specified term represents an object property.
     */
    public boolean isObjectProperty(String term) {
        // TODO There ARE properties that have a type of "@id" that are not object properties
        return !isKeyword(term)
                && term.indexOf(':') < 0
                && !classTerms.contains(term)
                && Objects.equals(context().getTypeMapping(term), JsonLdConsts.ID);
    }

    public Builder newBuilder() {
        return new Builder().context(context).classes(classes);
    }

    public static final class Builder {

        @Nullable
        private BdioContext context;

        private final Set<String> classes = new LinkedHashSet<>();

        public Builder() {
            // Add all of the standard BDIO classes
            ExtraStreams.stream(Bdio.Class.class).map(Bdio.Class::toString).forEach(classes::add);
        }

        public Builder context(BdioContext context) {
            this.context = Objects.requireNonNull(context);
            return this;
        }

        public Builder classes(Collection<String> classes) {
            this.classes.addAll(classes);
            return this;
        }

        public BdioFrame build() {
            checkState(context != null, "must supply a frame context");
            return new BdioFrame(this);
        }
    }

    /**
     * Checks to see if something is a JSON-LD keyword.
     */
    private static boolean isKeyword(Object obj) {
        return obj instanceof String && ((String) obj).startsWith("@");
    }

}
