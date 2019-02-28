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

import static com.blackducksoftware.common.base.ExtraThrowables.illegalState;
import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.github.jsonldjava.core.Context;
import com.github.jsonldjava.core.DocumentLoader;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.RemoteDocument;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.io.Resources;

/**
 * Specialization of the JSON-LD context specific to BDIO.
 *
 * @author jgustie
 */
public final class BdioContext {

    /**
     * Holder for context instances to defer initialization.
     */
    private static final class ContextHolder {
        private static final BdioContext DEFAULT_CONTEXT = new BdioContext.Builder().expandContext(Bdio.Context.DEFAULT).build();
    }

    /**
     * Returns a context for the current version of BDIO.
     */
    public static BdioContext getDefault() {
        return ContextHolder.DEFAULT_CONTEXT;
    }

    /**
     * The BDIO value mapper to use for transforming between JSON-LD and Java objects.
     */
    private final BdioValueMapper valueMapper;

    /**
     * The document loader used to retrieve URL references found in JSON-LD.
     */
    private final DocumentLoader documentLoader;

    /**
     * The set of IRIs representing types which should be embedded.
     */
    private final ImmutableSet<String> embeddedTypes;

    /**
     * The base URL.
     */
    @Nullable
    private final String base;

    /**
     * The unparsed context.
     */
    @Nullable
    private final Object expandContext;

    /**
     * The parsed context.
     */
    private final Context context;

    private BdioContext(Builder builder) {
        this.valueMapper = Objects.requireNonNull(builder.valueMapper);
        this.documentLoader = Objects.requireNonNull(builder.documentLoader);
        this.embeddedTypes = ImmutableSet.copyOf(builder.embeddedTypes);
        this.base = builder.base;
        this.expandContext = builder.expandContext;

        // TODO We should have a different path for legacy BDIO contexts
        this.context = new Context(jsonLdOptions()).parse(expandContext);
    }

    Map<String, Object> serialize() {
        return context.serialize();
    }

    String getTypeMapping(String property) {
        return context.getTypeMapping(property);
    }

    Map<String, String> getPrefixes(boolean onlyCommonPrefixes) {
        return context.getPrefixes(onlyCommonPrefixes);
    }

    /**
     * Returns a new (mutable) JSON-LD options instance based on this context.
     */
    public JsonLdOptions jsonLdOptions() {
        // TODO Technically both the documentLoader and potentially expandContext are mutable
        JsonLdOptions options = new JsonLdOptions();
        options.setDocumentLoader(documentLoader);
        options.setBase(base);
        options.setExpandContext(expandContext);
        options.setOmitDefault(Boolean.TRUE);
        return options;
    }

    /**
     * Converts a JSON-LD field value into a Java representation.
     */
    public Object fromFieldValue(String term, @Nullable Object input) {
        return _fromFieldValue(term, input).collect(valueMapper.getCollector(context.getContainer(term)));
    }

    private Stream<Object> _fromFieldValue(String term, @Nullable Object input) {
        // The input will be coming out of the JSON-LD API so we should only be looking at lists, maps and literals
        if (input instanceof List<?>) {
            // Recursively process list object
            return ((List<?>) input).stream().flatMap(e -> _fromFieldValue(term, e));
        } else if (input instanceof Map<?, ?>) {
            // Value object or relationship
            return Stream.of(valueMapper.fromFieldValue((Map<?, ?>) input));
        } else {
            String type = context.getTypeMapping(term);
            if (type != null && input != null) {
                // Compacted value
                return _fromFieldValue(term, ImmutableMap.of(JsonLdConsts.TYPE, type, JsonLdConsts.VALUE, input));
            } else {
                // Scalar (including null)
                return Stream.of(input);
            }
        }
    }

    /**
     * Produces value for serialization into the specified JSON-LD term.
     */
    public Object toFieldValue(String term, @Nullable Object input) {
        return valueMapper.split(input).map(e -> _toFieldValue(term, e)).collect(jsonLdCollector(context.getContainer(term)));
    }

    private Object _toFieldValue(String term, @Nullable Object input) {
        Object type = context.getTypeMapping(term);
        if (input instanceof String && Objects.equals(type, JsonLdConsts.ID)) {
            // IRI
            input = ImmutableMap.of(type, input);
        }

        if (input != null) {
            return valueMapper.toFieldValue(type, input, this::isEmbedded);
        } else {
            // Scalar null
            return input;
        }
    }

    /**
     * Tests to see if the specified term or fully qualified type should be embedded.
     */
    public boolean isEmbedded(@Nullable Object type) {
        if (type instanceof List<?>) {
            return ((List<?>) type).stream().anyMatch(this::isEmbedded);
        } else if (embeddedTypes.contains(type)) {
            return true;
        } else {
            String fullyQualified = context.getPrefixes(false).get(type);
            if (fullyQualified != null) {
                return isEmbedded(fullyQualified);
            } else {
                return false;
            }
        }
    }

    /**
     * Returns the term used by this context for the supplied IRI.
     */
    public Optional<String> lookupTerm(String iri) {
        // Special case for keywords
        if (isKeyword(iri)) {
            return Optional.of(iri);
        }

        Object definition = context.getInverse().get(iri);
        if (definition instanceof Map<?, ?>) {
            definition = ((Map<?, ?>) definition).values().iterator().next();
            if (definition instanceof Map<?, ?>) {
                definition = ((Map<?, ?>) definition).get(JsonLdConsts.TYPE);
                if (definition instanceof Map<?, ?>) {
                    return Optional.of(((Map<?, ?>) definition).values().iterator().next().toString());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Get a field value from a map.
     */
    public Stream<?> getFieldValue(Object field, Map<?, ?> values) {
        String key = field.toString();
        return valueMapper.split(fromFieldValue(term(key), values.get(key)));
    }

    /**
     * Returns a field value to put in a map.
     */
    public Object putFieldValue(Map<String, Object> map, Object field, @Nullable Object value) {
        String key = field.toString();
        if (value == null) {
            return map.computeIfPresent(key, this::computeFieldValueIfPresent);
        } else if (map.containsKey(key)) {
            return map.merge(key, value, mergeFieldValue(key));
        } else {
            return map.put(key, toFieldValue(term(key), value));
        }
    }

    /**
     * Returns an operator for merging a non-{@literal null} value in a map.
     */
    private BinaryOperator<Object> mergeFieldValue(String key) {
        return (oldValue, input) -> {
            Objects.requireNonNull(input);
            String term = term(key);
            String container = context.getContainer(term);

            // For the purpose of merging, object properties implicitly create a list
            if (container == null && Objects.equals(context.getTypeMapping(term), JsonLdConsts.ID)) {
                container = JsonLdConsts.LIST;
            }

            if (oldValue == null || container == null || container.isEmpty() || container.equals(JsonLdConsts.NONE)) {
                return toFieldValue(term, input);
            } else {
                Object newValue = toFieldValue(term, input);
                Stream<?> newValues = newValue instanceof List<?> ? ((List<?>) newValue).stream() : Stream.of(newValue);
                Stream<?> oldValues = oldValue instanceof List<?> ? ((List<?>) oldValue).stream() : Stream.of(oldValue);
                return Streams.concat(newValues, oldValues).collect(jsonLdCollector(container));
            }
        };
    }

    /**
     * Returns the replacement value for a {@literal null} value in a map.
     */
    private Object computeFieldValueIfPresent(String key, Object oldValue) {
        String term = term(key);
        String container = context.getContainer(term);
        if (container == null || container.isEmpty() || container.equals(JsonLdConsts.NONE)) {
            return null;
        } else {
            return oldValue;
        }
    }

    /**
     * Determines if this context is actually for a BDIO 1.x legacy format.
     */
    boolean isLegacyBdio() {
        return Objects.equals(expandContext, Bdio.Context.VERSION_1_0.toString())
                || Objects.equals(expandContext, Bdio.Context.VERSION_1_1.toString())
                || Objects.equals(expandContext, Bdio.Context.VERSION_1_1_1.toString());
    }

    /**
     * Performs a reverse lookup for a term on a given IRI, failing if the term is not mapped.
     */
    private String term(String iri) {
        return lookupTerm(iri).orElseThrow(illegalState("the current context does not support: %s", iri));
    }

    /**
     * Returns a new builder from this context.
     */
    public Builder newBuilder() {
        Builder builder = new Builder();
        builder.valueMapper = valueMapper;
        builder.documentLoader = documentLoader;
        builder.embeddedTypes.addAll(embeddedTypes);
        builder.base = base;
        builder.expandContext = expandContext;
        return builder;
    }

    public static final class Builder {

        /**
         * In memory standard document representations. Currently the standard documents consume ~20KB of memory.
         */
        private static ImmutableMap<String, String> STANDARD_DOCUMENTS;
        static {
            ImmutableMap.Builder<String, String> standardDocuments = ImmutableMap.builder();
            for (Bdio.Context context : Bdio.Context.values()) {
                try {
                    standardDocuments.put(context.toString(), Resources.toString(context.resourceUrl(), UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to inject standard context", e);
                }
            }
            STANDARD_DOCUMENTS = standardDocuments.build();
        }

        private BdioValueMapper valueMapper;

        private DocumentLoader documentLoader;

        private final Set<String> embeddedTypes = new LinkedHashSet<>();

        @Nullable
        private String base;

        @Nullable
        private Object expandContext;

        public Builder() {
            valueMapper = StandardJavaValueMapper.getInstance();

            documentLoader = new DocumentLoader();
            STANDARD_DOCUMENTS.forEach(documentLoader::addInjectedDoc);

            for (Bdio.Class bdioClass : Bdio.Class.values()) {
                if (bdioClass.embedded()) {
                    embeddedTypes.add(bdioClass.toString());
                }
            }
        }

        /**
         * Overrides the default value mapper.
         */
        public Builder valueMapper(BdioValueMapper valueMapper) {
            this.valueMapper = Objects.requireNonNull(valueMapper);
            return this;
        }

        /**
         * Injects a document at the specified URL for offline use.
         */
        public Builder injectDocument(String url, CharSequence content) {
            documentLoader.addInjectedDoc(url, content.toString());
            return this;
        }

        /**
         * Supports "reverse injection" from an existing remote document.
         * <p>
         * Generally the document contents are already parsed JSON content, however this method will also accept
         * document contents represented as a string of JSON data.
         */
        public Builder injectDocument(RemoteDocument document) {
            String url = document.getDocumentUrl();
            if (document.getDocument() instanceof CharSequence) {
                // Avoid unnecessary JSON serialization
                return injectDocument(url, (CharSequence) document.getDocument());
            } else {
                try {
                    documentLoader.addInjectedDoc(url, JsonUtils.toString(document.getDocument()));
                    return this;
                } catch (IOException e) {
                    throw new JsonLdError(JsonLdError.Error.LOADING_INJECTED_CONTEXT_FAILED, url, e);
                }
            }
        }

        /**
         * Includes the specified fully qualified IRIs as "embedded types". Embedded types will not be included in
         * framing, i.e. they more like complex values rather then types used on nodes in the "@graph" node list.
         */
        public Builder embeddedTypes(Collection<String> embeddedTypes) {
            this.embeddedTypes.addAll(embeddedTypes);
            return this;
        }

        /**
         * Specifies the base URI as a string. The base URI is used to relavitize identifiers.
         */
        public Builder base(@Nullable String base) {
            if (base != null && !base.isEmpty()) {
                URI baseUri = URI.create(base);
                checkArgument(baseUri.isAbsolute() && !baseUri.isOpaque(), "base must be an absolute hierarchical URI: %s", base);
            }
            this.base = base;
            return this;
        }

        /**
         * Sets the JSON-LD expansion context for processing input.
         * <p>
         * An expand context can be a map or list representing the JSON-LD context or it can be the JSON-LD context URL
         * (represented as a string or {@link Bdio.Context}). Note that the URL form may require remote access for
         * resolution, however you can use {@linkplain #injectDocument(String, CharSequence) document injection} to
         * allow the use of arbitrary URIs.
         * <p>
         * Finally note that a {@link Bdio.ContentType} can also be used to set the expand context, though it should be
         * noted that use of the {@link Bdio.ContentType#JSON} content type is not permitted as it would leave the
         * expansion context undefined (plain JSON should not have any JSON-LD constructs indicating the expansion
         * context, therefore requiring an explicit context).
         */
        public Builder expandContext(@Nullable Object expandContext) {
            if (expandContext == null || expandContext instanceof String || expandContext instanceof Map<?, ?> || expandContext instanceof List<?>) {
                // An explicit expand context, should only be used with plain JSON input
                // TODO Recursively normalize, e.g. allow a list with a Bdio.Context instance?
                this.expandContext = expandContext;
            } else if (expandContext instanceof Bdio.Context) {
                // A known BDIO context, perhaps the version number was sniffed from the content
                this.expandContext = expandContext.toString();
            } else if (expandContext instanceof Bdio.ContentType) {
                // Defined by content type or file extension, cannot be JSON since we can't imply an actual context
                checkArgument(expandContext != Bdio.ContentType.JSON, "the JSON content type leaves the expansion context undefined");
                if (expandContext == Bdio.ContentType.JSONLD || expandContext == Bdio.ContentType.BDIO_ZIP) {
                    // Any context information should be defined inside the document, no external context necessary
                    this.expandContext = null;
                } else if (expandContext == Bdio.ContentType.BDIO_JSON) {
                    // Plain JSON with the assumption of the default BDIO context
                    this.expandContext = Bdio.Context.DEFAULT.toString();
                } else {
                    throw new IllegalArgumentException("unknown content type: " + expandContext);
                }
            } else {
                throw new IllegalArgumentException("expandContext must be a Bdio.ContentType, Bdio.Context, String, Map<String, Object> or a List<Object>");
            }
            return this;
        }

        public BdioContext build() {
            return new BdioContext(this);
        }
    }

    /**
     * Returns a collector for JSON-LD serialization.
     */
    private static Collector<? super Object, ?, ?> jsonLdCollector(String container) {
        if (container == null || container.isEmpty() || container.equals(JsonLdConsts.NONE) || container.equals(JsonLdConsts.ID)) {
            return Collectors.reducing(null, (a, b) -> a != null ? a : b);
        } else {
            return Collectors.collectingAndThen(Collectors.toList(), l -> {
                l.removeIf(Objects::isNull);
                return l;
            });
        }
    }

    /**
     * Checks to see if something is a JSON-LD keyword.
     */
    private static boolean isKeyword(Object obj) {
        return obj instanceof String && ((String) obj).startsWith("@");
    }

}
