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
package com.blackducksoftware.bdio2.tinkerpop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;

/**
 * BDIO specific configuration options.
 *
 * @author jgustie
 */
public class BlackDuckIoOptions {

    public static final String METADATA_LABEL = "metadataLabel";

    public static final String ROOT_LABEL = "rootLabel";

    public static final String IDENTIFIER_KEY = "identifierKey";

    public static final String UNKNOWN_KEY = "unknownKey";

    private final Optional<String> metadataLabel;

    private final Optional<String> rootLabel;

    // TODO Is this an alias of the "@id" in the frame context?!
    private final Optional<String> identifierKey;

    private final Optional<String> unknownKey;

    private BlackDuckIoOptions(Builder builder) {
        metadataLabel = Optional.ofNullable(builder.metadataLabel);
        rootLabel = Optional.ofNullable(builder.rootLabel);
        identifierKey = Optional.ofNullable(builder.identifierKey);
        unknownKey = Optional.ofNullable(builder.unknownKey);
    }

    /**
     * The vertex label used to persist JSON-LD named graph metadata.
     */
    public Optional<String> metadataLabel() {
        return metadataLabel;
    }

    /**
     * The edge label used to connect metadata to a root vertex.
     */
    public Optional<String> rootLabel() {
        return rootLabel;
    }

    /**
     * The property key used to persist JSON-LD node identifiers.
     */
    // TODO Can this be removed this in favor of an ElementIdStrategy that generates random "urn:uuid:..." URIs?
    public Optional<String> identifierKey() {
        return identifierKey;
    }

    /**
     * The property key used to persist "unknown" JSON-LD node properties.
     */
    public Optional<String> unknownKey() {
        return unknownKey;
    }

    public Optional<String> fileParentKey() {
        // TODO Only use this for some implementations?
        return Optional.of("_parent");
    }

    public Configuration getConfiguration() {
        Map<String, Object> config = new LinkedHashMap<>();
        metadataLabel.ifPresent(v -> config.put(METADATA_LABEL, v));
        rootLabel.ifPresent(v -> config.put(ROOT_LABEL, v));
        identifierKey.ifPresent(v -> config.put(IDENTIFIER_KEY, v));
        unknownKey.ifPresent(v -> config.put(UNKNOWN_KEY, v));
        return new MapConfiguration(config);
    }

    public static BlackDuckIoOptions create(Configuration config) {
        return build()
                .metadataLabel(config.getString(METADATA_LABEL))
                .rootLabel(config.getString(ROOT_LABEL))
                .identifierKey(config.getString(IDENTIFIER_KEY))
                .unknownKey(config.getString(UNKNOWN_KEY))
                .create();
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {
        private String metadataLabel;

        private String rootLabel;

        private String identifierKey;

        private String unknownKey;

        private Builder() {
        }

        public Builder metadataLabel(@Nullable String metadataLabel) {
            this.metadataLabel = metadataLabel;
            return this;
        }

        public Builder rootLabel(@Nullable String rootLabel) {
            this.rootLabel = rootLabel;
            return this;
        }

        public Builder identifierKey(@Nullable String identifierKey) {
            this.identifierKey = identifierKey;
            return this;
        }

        public Builder unknownKey(@Nullable String unknownKey) {
            this.unknownKey = unknownKey;
            return this;
        }

        public BlackDuckIoOptions create() {
            return new BlackDuckIoOptions(this);
        }
    }

}
