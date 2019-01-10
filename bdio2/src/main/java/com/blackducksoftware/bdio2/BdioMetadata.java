/*
 * Copyright 2016 Black Duck Software, Inc.
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

import static com.blackducksoftware.common.base.ExtraStrings.ensureDelimiter;
import static com.blackducksoftware.common.base.ExtraStrings.ensurePrefix;
import static com.blackducksoftware.common.base.ExtraStrings.splitOnFirst;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import com.blackducksoftware.common.base.ExtraOptionals;
import com.blackducksoftware.common.base.ExtraStrings;
import com.blackducksoftware.common.value.ProductList;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.collect.ImmutableMap;

/**
 * Metadata used to describe a linked data graph.
 *
 * @author jgustie
 */
public final class BdioMetadata extends BdioObject {

    /**
     * The optional error that occurred while processing the entry corresponding to this metadata.
     */
    private Optional<Throwable> throwable = Optional.empty();

    /**
     * Creates a new, empty metadata instance.
     */
    public BdioMetadata() {
        this(ImmutableMap.of());
    }

    /**
     * Creates a new metadata instance populated from the supplied values.
     */
    public BdioMetadata(Map<String, Object> other) {
        super(other);
    }

    /**
     * Creates a new metadata instance that indicates a failure to process a BDIO entry. If partial results are
     * available (such as the identifier), they can be added to this metadata instance after construction.
     */
    public BdioMetadata(Throwable error) {
        this();
        throwable = Optional.of(error);
    }

    /**
     * Creates a new metadata instance with a random identifier.
     */
    public static BdioMetadata createRandomUUID() {
        return new BdioMetadata().id(randomId());
    }

    /**
     * Returns the optional failure that occurred while processing the BDIO entry (or document).
     */
    public Optional<Throwable> getThrowable() {
        return throwable;
    }

    /**
     * Returns a named graph using the specified keys from this metadata.
     *
     * @param graph
     *            the JSON-LD data to associate with the {@value JsonLdConsts#GRAPH} value.
     * @param keys
     *            the metadata keys to include in the named graph (all metadata is included by default).
     */
    public Map<String, Object> asNamedGraph(Object graph, String... keys) {
        Objects.requireNonNull(graph);
        Map<String, Object> namedGraph = new LinkedHashMap<>(this);
        if (keys.length > 0) {
            namedGraph.keySet().retainAll(Arrays.asList(keys));
        }
        namedGraph.put(JsonLdConsts.GRAPH, graph);
        return namedGraph;
    }

    /**
     * Returns a named graph that only contains metadata.
     *
     * @see #asNamedGraph(Object, String...)
     */
    public Map<String, Object> asNamedGraph() {
        // Use ArrayList to avoid problems with the JsonLdApi
        return asNamedGraph(new ArrayList<>(0));
    }

    /**
     * Merges additional metadata into this metadata instance.
     */
    public BdioMetadata merge(Map<String, Object> other) {
        // If the other instance is also metadata, merge in any failures
        if (other instanceof BdioMetadata) {
            throwable = ExtraOptionals.merge(throwable, ((BdioMetadata) other).throwable, (a, b) -> {
                a.addSuppressed(b);
                return a;
            });
        }

        // Merge properties
        other.forEach((key, value) -> {
            // TODO Keep the first occurrence of the creation time instead of the last?
            // TODO Merge creator username/hostname if one is missing
            if (key.equals(JsonLdConsts.ID)) {
                checkArgument(value instanceof String, "identifier must be mapped to a string");
                if (id() == null) {
                    // Establishes a new identifier
                    id((String) value);
                } else if (ExtraStrings.beforeLast((String) value, '#').equals(id())) {
                    // Discard non-matching fragments
                    if (!value.equals(get(JsonLdConsts.ID))) {
                        id(ExtraStrings.beforeLast((String) value, '#'));
                    }
                } else {
                    // Incompatible identifiers
                    throw new IllegalArgumentException("identifier mismatch: " + value + " (was expecting " + id() + ")");
                }
            } else if (key.equals(Bdio.DataProperty.publisher.toString())) {
                Object publisher = get(key);
                if (publisher != null) {
                    // Merges to create new producer
                    ProductList.Builder builder = new ProductList.Builder();
                    ProductList.from(fromFieldValue(key, publisher)).forEach(builder::mergeProduct);
                    ProductList.from(fromFieldValue(key, value)).forEach(builder::mergeProduct);
                    putFieldValue(Bdio.DataProperty.publisher, builder.build());
                } else {
                    // Establishes a new producer
                    put(key, value);
                }
            } else if (key.equals(Bdio.DataProperty.creator.toString())) {
                Object creator = get(key);
                if (creator != null) {
                    // Merges to create a new creator
                    put(key, splitOnFirst(fromFieldValue(key, creator).toString(), '@',
                            (user1, host1) -> splitOnFirst(fromFieldValue(key, value).toString(), '@', (user2, host2) -> {
                                return ensureDelimiter(isNullOrEmpty(user1) ? user2 : user1, "@", isNullOrEmpty(host1) ? host2 : host1);
                            })));
                } else {
                    // Establishes a new creator
                    put(key, value);
                }
            } else {
                put(key, value);
            }
        });
        return this;
    }

    /**
     * Sets the named graph label.
     */
    public BdioMetadata id(@Nullable String id) {
        put(JsonLdConsts.ID, id);
        return this;
    }

    /**
     * Sets the display name for the named graph.
     */
    public BdioMetadata name(@Nullable String name) {
        putFieldValue(Bdio.DataProperty.name, name);
        return this;
    }

    public BdioMetadata captureOptions(@Nullable List<String> captureOptions) {
        putFieldValue(Bdio.DataProperty.captureOptions, captureOptions);
        return this;
    }

    /**
     * Sets the interval over which the named graph was created.
     */
    public BdioMetadata captureInterval(@Nullable String captureInterval) {
        putFieldValue(Bdio.DataProperty.captureInterval, captureInterval);
        return this;
    }

    /**
     * Sets the capture interval based on start and end times.
     *
     * @see #captureInterval(String)
     */
    public BdioMetadata captureInterval(@Nullable ZonedDateTime start, @Nullable ZonedDateTime end) {
        return captureInterval(start != null && end != null ? start + "/" + end : null);
    }

    /**
     * Sets the time at which the named graph was created.
     */
    public BdioMetadata creationDateTime(@Nullable ZonedDateTime creation) {
        putFieldValue(Bdio.DataProperty.creationDateTime, creation);
        return this;
    }

    /**
     * Sets the identifier of the user and/or host who created the named graph.
     */
    public BdioMetadata creator(@Nullable String creator) {
        putFieldValue(Bdio.DataProperty.creator, creator);
        return this;
    }

    /**
     * Helper method to set the creator using individual user and host parts.
     *
     * @see #creator(String)
     */
    public BdioMetadata creator(@Nullable String user, @Nullable String host) {
        if (user != null || host != null) {
            return creator(nullToEmpty(user) + nullToEmpty(ensurePrefix("@", host)));
        } else {
            return creator(null);
        }
    }

    /**
     * Sets the platform (e.g. operating system) this named graph was captured from.
     */
    public BdioMetadata platform(@Nullable ProductList platform) {
        putFieldValue(Bdio.DataProperty.platform, platform);
        return this;
    }

    /**
     * Sets the publisher of the tool (or tools) that created the named graph.
     */
    public BdioMetadata publisher(@Nullable ProductList publisher) {
        putFieldValue(Bdio.DataProperty.publisher, publisher);
        return this;
    }

    /**
     * Sets the build details URL captured from the build environment.
     */
    public BdioMetadata buildDetails(@Nullable String buildDetails) {
        putFieldValue(Bdio.DataProperty.buildDetails, buildDetails);
        return this;
    }

    /**
     * Sets the build number captured from the build environment.
     */
    public BdioMetadata buildNumber(@Nullable String buildNumber) {
        putFieldValue(Bdio.DataProperty.buildNumber, buildNumber);
        return this;
    }

    /**
     * Sets the source repository URL captured from the build environment.
     */
    public BdioMetadata sourceRepository(@Nullable String sourceRepository) {
        putFieldValue(Bdio.DataProperty.sourceRepository, sourceRepository);
        return this;
    }

    /**
     * Sets the source repository revision identifier captured from the build environment.
     */
    public BdioMetadata sourceRevision(@Nullable String sourceRevision) {
        putFieldValue(Bdio.DataProperty.sourceRevision, sourceRevision);
        return this;
    }

    /**
     * Sets the source repository branch name captured from the build environment.
     */
    public BdioMetadata sourceBranch(@Nullable String sourceBranch) {
        putFieldValue(Bdio.DataProperty.sourceBranch, sourceBranch);
        return this;
    }

    /**
     * Sets the source repository tag captured from the build environment.
     */
    public BdioMetadata sourceTag(@Nullable String sourceTag) {
        putFieldValue(Bdio.DataProperty.sourceTag, sourceTag);
        return this;
    }

}
