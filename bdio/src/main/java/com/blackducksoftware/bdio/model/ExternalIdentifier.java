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
package com.blackducksoftware.bdio.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio.BlackDuckTerm;
import com.blackducksoftware.bdio.BlackDuckType;
import com.blackducksoftware.bdio.BlackDuckValue;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * An external identifier.
 *
 * @author jgustie
 */
public class ExternalIdentifier extends AbstractEmbeddedModel<ExternalIdentifier> {

    /**
     * Functions for converting/extracting strings.
     */
    private enum ToStringFunction implements Function<ExternalIdentifier, String> {
        EXTERNAL_ID {
            @Override
            @Nullable
            public String apply(@Nullable ExternalIdentifier externalIdentifier) {
                return externalIdentifier != null ? externalIdentifier.getExternalId() : null;
            }
        }
    }

    /**
     * Filter for external identifiers by their system type identifier.
     */
    private static final class ExternalSystemTypeIdFilter implements Predicate<ExternalIdentifier> {
        private static final Predicate<ExternalIdentifier> BDSUITE = new ExternalSystemTypeIdFilter(BlackDuckValue.EXTERNAL_IDENTIFIER_BDSUITE.id());

        private static final Predicate<ExternalIdentifier> BDHUB = new ExternalSystemTypeIdFilter(BlackDuckValue.EXTERNAL_IDENTIFIER_BDHUB.id());

        private final String externalSystemTypeId;

        private ExternalSystemTypeIdFilter(String externalSystemTypeId) {
            this.externalSystemTypeId = checkNotNull(externalSystemTypeId);
        }

        @Override
        public boolean apply(ExternalIdentifier externalIdentifier) {
            return externalIdentifier != null
                    && externalIdentifier.getExternalId() != null
                    && Objects.equals(externalIdentifier.getExternalSystemTypeId(), externalSystemTypeId);
        }
    }

    private static final ModelField<ExternalIdentifier, String> EXTERNAL_SYSTEM_TYPE_ID = new ModelField<ExternalIdentifier, String>(
            BlackDuckTerm.EXTERNAL_SYSTEM_TYPE_ID) {
        @Override
        protected String get(ExternalIdentifier externalIdentifier) {
            return externalIdentifier.getExternalSystemTypeId();
        }

        @Override
        protected void set(ExternalIdentifier externalIdentifier, Object value) {
            externalIdentifier.setExternalSystemTypeId(valueToString(value));
        }
    };

    private static final ModelField<ExternalIdentifier, String> EXTERNAL_ID = new ModelField<ExternalIdentifier, String>(BlackDuckTerm.EXTERNAL_ID) {
        @Override
        protected String get(ExternalIdentifier externalIdentifier) {
            return externalIdentifier.getExternalId();
        }

        @Override
        protected void set(ExternalIdentifier externalIdentifier, Object value) {
            externalIdentifier.setExternalId(valueToString(value));
        }
    };

    private static final ModelField<ExternalIdentifier, String> EXTERNAL_REPOSITORY_LOCATION = new ModelField<ExternalIdentifier, String>(
            BlackDuckTerm.EXTERNAL_REPOSITORY_LOCATION) {
        @Override
        protected String get(ExternalIdentifier externalIdentifier) {
            return externalIdentifier.getExternalRepositoryLocation();
        }

        @Override
        protected void set(ExternalIdentifier externalIdentifier, Object value) {
            externalIdentifier.setExternalRepositoryLocation(valueToString(value));
        }
    };

    @Nullable
    private String externalSystemTypeId;

    // TODO Should there be externalId and externalSubId (for versions)

    @Nullable
    private String externalId;

    @Nullable
    private String externalRepositoryLocation;

    public ExternalIdentifier() {
        super(BlackDuckType.EXTERNAL_IDENTIFIER,
                ImmutableSet.<ModelField<ExternalIdentifier, ?>> of(EXTERNAL_SYSTEM_TYPE_ID, EXTERNAL_ID, EXTERNAL_REPOSITORY_LOCATION));
    }

    /**
     * Returns a filter that only excepts external identifiers with a specific system type ID that have non-{@code null}
     * external ID fields.
     */
    public static Predicate<ExternalIdentifier> bySystem(String externalSystemTypeId) {
        return new ExternalSystemTypeIdFilter(externalSystemTypeId);
    }

    /**
     * Returns a predicate that matches Black Duck Suite external identifiers.
     */
    public static Predicate<ExternalIdentifier> bdSuite() {
        return ExternalSystemTypeIdFilter.BDSUITE;
    }

    /**
     * Returns a predicate that matches Black Duck Suite external identifiers.
     */
    public static Predicate<ExternalIdentifier> bdHub() {
        return ExternalSystemTypeIdFilter.BDHUB;
    }

    /**
     * Returns a function to extract the actual external identifier.
     */
    public static Function<ExternalIdentifier, String> toExternalId() {
        return ToStringFunction.EXTERNAL_ID;
    }

    /**
     * Helper to create a new Black Duck Suite identifier.
     *
     * @deprecated Use {@link ExternalIdentifierBuilder#blackDuckSuite(String)} instead.
     */
    @Deprecated
    @Nullable
    public static ExternalIdentifier blackDuckSuite(@Nullable String id) {
        return ExternalIdentifierBuilder.create().blackDuckSuite(id).build().orNull();
    }

    /**
     * Helper to create a new Black Duck Hub identifier.
     *
     * @deprecated Use {@link ExternalIdentifierBuilder#blackDuckHub(String, UUID)} instead.
     */
    @Deprecated
    @Nullable
    public static ExternalIdentifier blackDuckHub(String entityType, @Nullable UUID id) {
        return ExternalIdentifierBuilder.create().blackDuckHub(entityType, id).build().orNull();
    }

    @Nullable
    public String getExternalSystemTypeId() {
        return externalSystemTypeId;
    }

    public void setExternalSystemTypeId(@Nullable String externalSystemTypeId) {
        this.externalSystemTypeId = externalSystemTypeId;
    }

    @Nullable
    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(@Nullable String externalId) {
        this.externalId = externalId;
    }

    @Nullable
    public String getExternalRepositoryLocation() {
        return externalRepositoryLocation;
    }

    public void setExternalRepositoryLocation(@Nullable String externalRepositoryLocation) {
        this.externalRepositoryLocation = externalRepositoryLocation;
    }

    // These are helper methods dependent on specific state

    @Nullable
    public String getSuiteComponentId() {
        return Iterables.getFirst(getBdSuiteId(), null);
    }

    @Nullable
    public String getSuiteReleaseTag() {
        return Iterables.getFirst(Iterables.skip(getBdSuiteId(), 1), null);

    }

    private Iterable<String> getBdSuiteId() {
        checkState(Objects.equals(getExternalSystemTypeId(), BlackDuckValue.EXTERNAL_IDENTIFIER_BDSUITE.id()), "not a bdsuite identifier");
        return Splitter.on('#').limit(2).split(getExternalId());
    }

}
