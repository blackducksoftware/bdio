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

import static com.github.jsonldjava.core.JsonLdError.Error.LOADING_INJECTED_CONTEXT_FAILED;
import static com.github.jsonldjava.shaded.com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
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
import com.github.jsonldjava.shaded.com.google.common.collect.Streams;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;

/**
 * Specialization of the JSON-LD context specific to BDIO.
 *
 * @author jgustie
 */
public class BdioContext {

    private static Predicate<String> STANDARD_EMBEDDED = Arrays.stream(Bdio.Class.values())
            .filter(Bdio.Class::embedded)
            .map(Object::toString)
            .collect(toImmutableSet())::contains;

    public interface AutoClearContext extends AutoCloseable {
        @Override
        void close();
    }

    private static final ThreadLocal<BdioContext> activeContext = new InheritableThreadLocal<>();

    public static BdioContext getActive() {
        BdioContext context = activeContext.get();
        checkState(context != null, "there is no active context");
        return context;
    }

    /**
     * The BDIO value mapper to use. The value mapper typically handles type issues when dealing with different graph
     * implementations.
     */
    private final BdioValueMapper valueMapper;

    /**
     * The JSON-LD options to use.
     */
    private final JsonLdOptions options;

    /**
     * The JSON-LD parsed context.
     */
    private final Context context;

    /**
     * We need to fall back to reflective hacks to access the type information from the context.
     *
     * @see <a href="https://github.com/jsonld-java/jsonld-java/issues/244">JSON-LD Java #244</a>
     */
    private final Method contextGetTypeMapping;

    private final Predicate<String> embeddedTypes;

    private BdioContext(Builder builder) {
        this.valueMapper = Objects.requireNonNull(builder.valueMapper);
        this.options = new JsonLdOptions(builder.base);

        options.setExpandContext(builder.expandContext);
        injectDocuments(options.getDocumentLoader(), builder.injectedDocuments);

        this.context = new Context(options).parse(builder.expandContext);

        try {
            contextGetTypeMapping = Context.class.getDeclaredMethod("getTypeMapping", String.class);
            contextGetTypeMapping.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("unable to access type mapping on JSON-LD context");
        }

        embeddedTypes = STANDARD_EMBEDDED;
    }

    public AutoClearContext activate() {
        activeContext.set(this);
        return () -> activeContext.remove();
    }

    public JsonLdOptions options() {
        return options;
    }

    /**
     * Converts a JSON-LD field value into a Java representation.
     */
    public Object fromFieldValue(String term, @Nullable Object input) {
        return _fromFieldValue(input).collect(valueMapper.getCollector(context.getContainer(term)));
    }

    private Stream<Object> _fromFieldValue(@Nullable Object input) {
        // The input will be coming out of the JSON-LD API so we should only be looking at lists, maps and literals
        if (input instanceof List<?>) {
            // Recursively process list object
            return ((List<?>) input).stream().flatMap(this::_fromFieldValue);
        } else if (input instanceof Map<?, ?>) {
            // Value object or relationship
            return Stream.of(valueMapper.fromFieldValue((Map<?, ?>) input));
        } else {
            // Scalar (including null)
            return Stream.of(input);
        }
    }

    /**
     * Produces value for serialization into the specified JSON-LD term.
     */
    public Object toFieldValue(String term, @Nullable Object input) {
        return valueMapper.split(input).map(e -> _toFieldValue(term, e)).collect(jsonLdCollector(context.getContainer(term)));
    }

    private Object _toFieldValue(String term, @Nullable Object input) {
        if (input == null || input instanceof Number || input instanceof Boolean) {
            // Scalar
            return input;
        } else {
            Object type = getTypeMapping(term);
            if (input instanceof String) {
                if (Objects.equals(type, JsonLdConsts.ID)) {
                    // IRI
                    input = ImmutableMap.of(type, input);
                } else {
                    // Scalar
                    return input;
                }
            }

            return valueMapper.toFieldValue(type, input, this::isEmbedded);
        }
    }

    public boolean isDataProperty(String term) {
        //
        return false;
    }

    public boolean isObjectProperty(String term) {
        //
        return false;
    }

    /**
     * Get a field value from a map.
     */
    Stream<?> getFieldValue(String key, Map<?, ?> values) {
        return valueMapper.split(fromFieldValue(term(key), values.get(key)));
    }

    /**
     * Returns a field value to put in a map.
     */
    Object putFieldValue(String key, Object value) {
        return toFieldValue(term(key), value);
    }

    /**
     * Returns an operator for merging a non-{@literal null} value in a map.
     */
    BinaryOperator<Object> mergeFieldValue(String key) {
        return (oldValue, input) -> {
            Objects.requireNonNull(input);
            String term = term(key);
            String container = context.getContainer(term);
            if (oldValue == null || container == null || container.isEmpty() || container.equals(JsonLdConsts.NONE)) {
                return toFieldValue(term, input);
            } else {
                Object newValue = toFieldValue(term, input);
                Stream<?> newValues = newValue instanceof List<?> ? ((List<?>) newValue).stream() : Stream.of(newValue);
                Stream<?> oldValues = oldValue instanceof List<?> ? ((List<?>) oldValue).stream() : Stream.of(oldValue);
                return Streams.concat(newValues, oldValues).collect(jsonLdCollector(context.getContainer(term)));
            }
        };
    }

    /**
     * Returns the replacement value for a {@literal null} value in a map.
     */
    Object computeFieldValueIfPresent(String key, Object oldValue) {
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
        Object expandContext = options.getExpandContext();
        return Objects.equals(expandContext, Bdio.Context.VERSION_1_0.toString())
                || Objects.equals(expandContext, Bdio.Context.VERSION_1_1.toString())
                || Objects.equals(expandContext, Bdio.Context.VERSION_1_1_1.toString());
    }

    /**
     * Tests to see if the specified fully qualified type should be embedded.
     */
    private boolean isEmbedded(Object type) {
        if (type instanceof List<?>) {
            return ((List<?>) type).stream().anyMatch(this::isEmbedded);
        } else if (type instanceof String) {
            return embeddedTypes.test((String) type);
        } else {
            return false;
        }
    }

    /**
     * Performs a reverse lookup for a term on a given IRI.
     */
    private String term(String iri) {
        Object definition = context.getInverse().get(iri);
        if (definition instanceof Map<?, ?>) {
            definition = ((Map<?, ?>) definition).values().iterator().next();
            if (definition instanceof Map<?, ?>) {
                definition = ((Map<?, ?>) definition).get(JsonLdConsts.TYPE);
                if (definition instanceof Map<?, ?>) {
                    return ((Map<?, ?>) definition).values().iterator().next().toString();
                }
            }
        }
        throw new IllegalStateException("the current context does not support: " + iri);
    }

    // TODO Replace with `context.getTypeMapping(property)`
    @Nullable
    private Object getTypeMapping(String property) {
        try {
            return contextGetTypeMapping.invoke(context, property);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    // Basically we need a way to look at the context and say which values are classes, object properties or data
    // properties...the BDIO elements are easy, but it needs to be configurable as well...
    // That also lets us export a frame....
    // Which has the embedded state...

    // Right now we generate a frame using: `"@type" : [ "Foo", "Bar", "Gus" ]`
    // But we should not include embedded types in that list!

    // This should also have it's own value object mapper because we can use the context to populate everything in it

    // This is also a replacement for BdioOptions, not something that co-exists

    public static final class Builder {

        private BdioValueMapper valueMapper;

        @Nullable
        private String base;

        @Nullable
        private Object expandContext;

        private final Map<String, CharSource> injectedDocuments = new LinkedHashMap<>();

        public Builder() {
            valueMapper = StandardJavaValueMapper.getInstance();
        }

        /**
         * Overrides the default value mapper.
         */
        public Builder valueMapper(BdioValueMapper valueMapper) {
            this.valueMapper = Objects.requireNonNull(valueMapper);
            return this;
        }

        /**
         * Specifies the base URI as a string. The base URI is used to relavitize identifiers.
         */
        public Builder base(@Nullable String base) {
            if (base != null) {
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
            this.expandContext = normalizeExpandContext(expandContext);
            return this;
        }

        /**
         * Injects a document at the specified URL for offline use.
         */
        public Builder injectDocument(String url, CharSequence content) {
            checkArgument(!injectedDocuments.containsKey(url), "already injected URL: %s", url);
            injectedDocuments.put(url, CharSource.wrap(content));
            return this;
        }

        /**
         * Supports "reverse injection" from an existing remote document.
         */
        public Builder injectDocument(RemoteDocument document) {
            String url = document.getDocumentUrl();
            CharSource content;
            if (document.getDocument() instanceof CharSequence) {
                // Avoid unnecessary JSON serialization
                content = CharSource.wrap((CharSequence) document.getDocument());
            } else {
                content = new CharSource() {
                    @Override
                    public Reader openStream() throws IOException {
                        return new StringReader(JsonUtils.toString(document.getDocument()));
                    }
                };
            }
            injectedDocuments.put(url, content);
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
        if (container == null || container.isEmpty() || container.equals(JsonLdConsts.NONE)) {
            return Collectors.reducing(null, (a, b) -> a != null ? a : b);
        } else {
            return Collectors.collectingAndThen(Collectors.toList(), l -> {
                l.removeIf(Objects::isNull);
                return l;
            });
        }
    }

    /**
     * Normalizes an expansion context.
     */
    @Nullable
    private static Object normalizeExpandContext(@Nullable Object expandContext) {
        if (expandContext == null || expandContext instanceof String || expandContext instanceof Map<?, ?> || expandContext instanceof List<?>) {
            // An explicit expand context, should only be used with plain JSON input
            return expandContext;
        } else if (expandContext instanceof Bdio.Context) {
            // A known BDIO context, perhaps the version number was sniffed from the content
            return expandContext.toString();
        } else if (expandContext instanceof Bdio.ContentType) {
            // Defined by content type or file extension, cannot be JSON since we can't imply an actual context
            checkArgument(expandContext != Bdio.ContentType.JSON, "the JSON content type leaves the expansion context undefined");
            if (expandContext == Bdio.ContentType.JSONLD || expandContext == Bdio.ContentType.BDIO_ZIP) {
                // Any context information should be defined inside the document, no external context necessary
                return null;
            } else if (expandContext == Bdio.ContentType.BDIO_JSON) {
                // Plain JSON with the assumption of the default BDIO context
                return Bdio.Context.DEFAULT.toString();
            } else {
                throw new IllegalArgumentException("unknown content type: " + expandContext);
            }
        } else {
            throw new IllegalArgumentException("expandContext must be a Bdio.ContentType, Bdio.Context, String, Map<String, Object> or a List<Object>");
        }
    }

    /**
     * Injects custom and standard remote documents into the supplied document loader.
     */
    private static void injectDocuments(DocumentLoader documentLoader, Map<String, CharSource> injectedDocuments) throws JsonLdError {
        for (Map.Entry<String, CharSource> injectedDoc : injectedDocuments.entrySet()) {
            try {
                documentLoader.addInjectedDoc(injectedDoc.getKey(), injectedDoc.getValue().read());
            } catch (IOException e) {
                throw new JsonLdError(LOADING_INJECTED_CONTEXT_FAILED, injectedDoc.getKey(), e);
            }
        }

        for (Bdio.Context context : Bdio.Context.values()) {
            try {
                documentLoader.addInjectedDoc(context.toString(), Resources.toString(context.resourceUrl(), UTF_8));
            } catch (IOException e) {
                throw new JsonLdError(LOADING_INJECTED_CONTEXT_FAILED, context.toString(), e);
            }
        }
    }

}
