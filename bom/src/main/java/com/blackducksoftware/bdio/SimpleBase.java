/*
 * Copyright 2015 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * Base type for the "simple" implementations.
 *
 * @author jgustie
 */
abstract class SimpleBase implements Serializable {

    private final URI uri;

    SimpleBase(String fullyQualifiedName) {
        // Verify that the supplied type is a valid non-empty IRI (URI)
        checkArgument(!fullyQualifiedName.isEmpty());
        uri = URI.create(fullyQualifiedName);
    }

    /**
     * Applies a function that can potentially throw an {@code UncheckedExecutionException} (like a cache).
     */
    protected static <T> T apply(Function<String, T> converter, String fullyQualifiedName) {
        try {
            return converter.apply(fullyQualifiedName);
        } catch (UncheckedExecutionException e) {
            Throwables.propagateIfPossible(e.getCause());
            throw e;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleBase) {
            return uri.equals(((SimpleBase) obj).uri);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return uri.toString();
    }

    public URI toUri() {
        return uri;
    }
}
