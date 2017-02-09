/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio2;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.http.impl.client.CloseableHttpClient;

import com.fasterxml.jackson.core.JsonParseException;
import com.github.jsonldjava.core.DocumentLoader;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.RemoteDocument;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

/**
 * Integration with the JSON-LD API to override document loading behavior.
 *
 * @author jgustie
 */
class RemoteDocumentLoader extends DocumentLoader {

    /**
     * A builder to construct a remote document loader. Callers can add "offline" documents stored as class loader
     * resources by assigning them a URL. Additionally, loading of remote URLs can be enabled by suppling a configured
     * HTTP client.
     */
    public static class Builder {

        @Nullable
        private CloseableHttpClient httpClient;

        private final Map<String, ByteSource> offlineDocuments = new HashMap<>();

        public RemoteDocumentLoader build() {
            return new RemoteDocumentLoader(this);
        }

        /**
         * Allows the document loader to load remote documents using the supplied HTTP client.
         * <p>
         * Note that it is recommended that the supplied client have caching enabled and properly configured.
         */
        public Builder allowRemoteLoading(CloseableHttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient);
            return this;
        }

        /**
         * Loads a resource from the class path and exposes it using the specified document URL.
         */
        public Builder withResource(String url, String resourceName) {
            return putOfflineDocument(url, Resources.asByteSource(Resources.getResource(resourceName)));
        }

        /**
         * Loads a resource from the class path and exposes it using the specified document URL.
         */
        public Builder withResource(String url, Class<?> contextClass, String resourceName) {
            return putOfflineDocument(url, Resources.asByteSource(Resources.getResource(contextClass, resourceName)));
        }

        /**
         * Adds an offline document to this builder.
         */
        private Builder putOfflineDocument(String url, ByteSource byteSource) {
            offlineDocuments.put(url, byteSource);
            return this;
        }
    }

    /**
     * A mapping of offline document URLs to their input stream supplier.
     */
    private final Map<String, ByteSource> offlineDocuments;

    /**
     * A filter which determines what documents may be loaded remotely.
     */
    private final Predicate<URL> allowedRemoteDocuments;

    private RemoteDocumentLoader(Builder builder) {
        offlineDocuments = ImmutableMap.copyOf(builder.offlineDocuments);
        if (builder.httpClient != null) {
            setHttpClient(builder.httpClient);
            allowedRemoteDocuments = Predicates.alwaysTrue();
        } else {
            allowedRemoteDocuments = Predicates.alwaysFalse();
        }
    }

    @Override
    public InputStream openStreamFromURL(URL url) throws IOException {
        ByteSource offlineDocument = offlineDocuments.get(url.toString());
        if (offlineDocument != null) {
            // Delegate to the byte source
            return offlineDocument.openBufferedStream();
        } else if (allowedRemoteDocuments.apply(url)) {
            // Fall through to the super for remote loading
            return super.openStreamFromURL(url);
        } else {
            // TODO Create a real exception for this...
            throw new IOException("remote blocked!");
        }
    }

    // Override methods from the super to force all code paths through the our openStreamFromURL

    @Override
    public final RemoteDocument loadDocument(String url) throws JsonLdError {
        // Implement this here because the super has some system properties we want to ignore
        try {
            return new RemoteDocument(url, fromURL(new URL(url)));
        } catch (Exception e) {
            throw (JsonLdError) new JsonLdError(JsonLdError.Error.LOADING_REMOTE_CONTEXT_FAILED, url).initCause(e);
        }
    }

    @Override
    public final Object fromURL(URL url) throws JsonParseException, IOException {
        // Delegate to our openStreamFromURL so we get access to offline documents
        try (InputStream in = openStreamFromURL(url)) {
            return JsonUtils.fromInputStream(in);
        }
    }

    @Override
    public final void setHttpClient(CloseableHttpClient httpClient) {
        // Don't allow a null client as that would bring JsonUtils.getDefaultHttpClient() back into play
        super.setHttpClient(Objects.requireNonNull(httpClient));
    }

}
