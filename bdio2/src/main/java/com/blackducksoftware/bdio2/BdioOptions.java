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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;

/**
 * Specialization of the JSON-LD options specific to BDIO.
 *
 * @author jgustie
 */
public class BdioOptions {

    private final String base;

    private final Object expandContext;

    private final ImmutableMap<String, CharSource> injectedDocs;

    private BdioOptions(Builder builder) {
        base = Objects.requireNonNull(builder.base);
        expandContext = expandContext(builder.contentTypeContext, builder.applicationContext);
        injectedDocs = ImmutableMap.copyOf(builder.injectedDocs);
    }

    /**
     * Test to see if the supplied context is applicable to these options.
     */
    public boolean hasContext(@Nullable Object expandContext) {
        if (this.expandContext instanceof List<?>) {
            for (Object ctx : (List<?>) this.expandContext) {
                if (Objects.equals(ctx, expandContext)) {
                    return true;
                }
            }
            return false;
        } else {
            return Objects.equals(this.expandContext, expandContext);
        }
    }

    /**
     * Creates a new JSON-LD configuration.
     */
    public JsonLdOptions jsonLdOptions() throws JsonLdError {
        JsonLdOptions result = new JsonLdOptions(base);
        result.setExpandContext(expandContext);
        for (Map.Entry<String, CharSource> injectedDoc : injectedDocs.entrySet()) {
            try {
                result.getDocumentLoader().addInjectedDoc(injectedDoc.getKey(), injectedDoc.getValue().read());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return result;
    }

    /**
     * Generate an expand context from multiple, possibly {@code null}, sources.
     */
    private static Object expandContext(Object... contexts) {
        Object expandContext = null;
        for (Object context : contexts) {
            if (context != null && !context.equals(expandContext)) {
                if (expandContext == null) {
                    expandContext = context;
                } else if (expandContext instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Object> contextList = (List<Object>) expandContext;
                    contextList.add(context);
                } else {
                    List<Object> contextList = new ArrayList<>();
                    contextList.add(expandContext);
                    contextList.add(context);
                    expandContext = contextList;
                }
            }
        }
        return expandContext;
    }

    public static class Builder {

        private String base;

        private Object contentTypeContext;

        private Object applicationContext;

        private final Map<String, CharSource> injectedDocs = new LinkedHashMap<>();

        public Builder() {
            base = "";
            contentTypeContext = Bdio.Context.DEFAULT.toString();
            for (Bdio.Context context : Bdio.Context.values()) {
                // TODO Memoize these CharSources (with a soft ref?) so we only read them once...
                injectedDocs.put(context.toString(), Resources.asCharSource(context.resourceUrl(), UTF_8));
            }
        }

        public BdioOptions build() {
            return new BdioOptions(this);
        }

        /**
         * Specifies the base URI as a string. The base URI is used to relavitize identifiers.
         */
        public Builder base(@Nullable String base) {
            if (Strings.isNullOrEmpty(base)) {
                this.base = Strings.nullToEmpty(base);
                return this;
            } else {
                try {
                    return base(new URI(base));
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("base URI must be well formed", e);
                }
            }
        }

        /**
         * Specifies the base URI. The base URI is used to relavitize identifiers.
         */
        public Builder base(@Nullable URI base) {
            checkArgument(base == null || base.isAbsolute(), "base URI must be absolute");
            checkArgument(base == null || !base.isOpaque(), "base URI must be hierarchical");
            this.base = Objects.toString(base, "");
            return this;
        }

        /**
         * In a addition to the primary expansion context determined by the content type, applications may include a
         * secondary "application" context for extensibility.
         */
        public Builder applicationContext(Object applicationContext) {
            checkArgument(applicationContext == null
                    || applicationContext instanceof String
                    || applicationContext instanceof Map<?, ?>
                    || applicationContext instanceof List<?>,
                    "applicationContext must be a String, Map<String, Object> or a List<Object>");
            this.applicationContext = applicationContext;
            return this;
        }

        /**
         * Prepares this document for processing documents based on their detected or declared content type.
         * <p>
         * Note that the supplied expansion context is only used with the {@linkplain Bdio.ContentType#JSON JSON} type.
         */
        public Builder forContentType(@Nullable Bdio.ContentType contentType, @Nullable Object expandContext) {
            checkArgument(expandContext == null
                    || expandContext instanceof String
                    || expandContext instanceof Map<?, ?>
                    || expandContext instanceof List<?>,
                    "expandContext must be a String, Map<String, Object> or a List<Object>");

            if (contentType == null) {
                contentTypeContext = Bdio.Context.DEFAULT.toString();
            } else if (contentType.equals(Bdio.ContentType.JSON)) {
                // TODO Warn if expandContext is null? Require non-null?
                contentTypeContext = expandContext;
            } else if (contentType.equals(Bdio.ContentType.JSONLD)) {
                contentTypeContext = null;
            } else if (contentType.equals(Bdio.ContentType.BDIO_JSON) || contentType.equals(Bdio.ContentType.BDIO_ZIP)) {
                contentTypeContext = Bdio.Context.DEFAULT.toString();
            } else {
                throw new IllegalArgumentException("unknown content type: " + contentType);
            }
            return this;
        }

        /**
         * Prepares the document for processing BDIO loaded from plain JSON. The {@code expandContext} is typically a
         * {@code String} representation of the {@code http://www.w3.org/ns/json-ld#context} link relationship (a URI
         * identifying the context), however it can also be a {@code Map<String, Object>} representing an already parsed
         * JSON-LD context. Note that, while accepted, a {@code null} context will only produce meaningful results if
         * the JSON contains fully qualified IRIs.
         */
        public Builder forJson(@Nullable Object expandContext) {
            return forContentType(Bdio.ContentType.JSON, expandContext);
        }

        /**
         * Prepares this document for processing BDIO loaded from JSON-LD. The JSON-LD contexts must be explicitly
         * defined within the document itself.
         */
        public Builder forJsonLd() {
            return forContentType(Bdio.ContentType.JSONLD, null);
        }

        /**
         * Prepares this document for processing BDIO documents. This assumes the default BDIO context will be used for
         * processing plain JSON or JSON-LD input (Zip forms should already be fully expanded internally).
         */
        public Builder forBdio() {
            return forContentType(null, null);
        }

        /**
         * Injects a document at the specified URI for offline use.
         */
        public Builder injectDocument(String uri, CharSequence content) {
            checkArgument(!injectedDocs.containsKey(uri), "already injected URI: %s", uri);
            injectedDocs.put(uri, CharSource.wrap(content));
            return this;
        }

    }

}
