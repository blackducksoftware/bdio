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

import static com.github.jsonldjava.core.JsonLdError.Error.LOADING_INJECTED_CONTEXT_FAILED;
import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

    @Nullable
    private final Object expandContext;

    private final ImmutableMap<String, CharSource> injectedDocs;

    private BdioOptions(Builder builder) {
        base = Objects.requireNonNull(builder.base);
        expandContext = builder.expandContext;
        injectedDocs = ImmutableMap.copyOf(builder.injectedDocs);
    }

    /**
     * Test to see if the supplied context is applicable to these options.
     */
    public boolean hasContext(@Nullable Object expandContext) {
        return Objects.equals(this.expandContext, expandContext);
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
                throw new JsonLdError(LOADING_INJECTED_CONTEXT_FAILED, injectedDoc.getKey(), e);
            }
        }
        return result;
    }

    public static class Builder {

        private String base;

        @Nullable
        private Object expandContext;

        private final Map<String, CharSource> injectedDocs = new LinkedHashMap<>();

        public Builder() {
            base = "";
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
        public Builder expandContext(Object expandContext) {
            if (expandContext instanceof String || expandContext instanceof Map<?, ?> || expandContext instanceof List<?>) {
                // An explicit expand context, should only be used with plain JSON input
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
