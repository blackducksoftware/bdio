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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

import org.apache.http.impl.client.CloseableHttpClient;

import com.github.jsonldjava.core.DocumentLoader;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.RemoteDocument;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.io.Resources;

/**
 * Extension to the standard document loader that does not use remote document loading to access BDIO contexts. This
 * means even if the {@value DocumentLoader#DISALLOW_REMOTE_CONTEXT_LOADING} property is set, the BDIO contexts are
 * still accessible.
 *
 * @author jgustie
 */
final class BdioDocumentLoader extends DocumentLoader {

    /**
     * The required delegate document loader to use when the requested document is not a BDIO context.
     */
    private final DocumentLoader delegate;

    public BdioDocumentLoader(DocumentLoader delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public RemoteDocument loadDocument(String url) throws JsonLdError {
        // Look for BDIO context URLs and intercept the request using the resource URL
        for (Bdio.Context context : Bdio.Context.values()) {
            if (context.toString().equals(url)) {
                try (Reader reader = Resources.asCharSource(context.resourceUrl(), UTF_8).openBufferedStream()) {
                    // TODO Implement some type of cache on this?
                    return new RemoteDocument(url, JsonUtils.fromReader(reader));
                } catch (IOException e) {
                    throw new JsonLdError(JsonLdError.Error.LOADING_REMOTE_CONTEXT_FAILED, url, e);
                }
            }
        }

        return super.loadDocument(url);
    }

    @Override
    public CloseableHttpClient getHttpClient() {
        return delegate.getHttpClient();
    }

    @Override
    public void setHttpClient(CloseableHttpClient nextHttpClient) {
        delegate.setHttpClient(nextHttpClient);
    }

}
