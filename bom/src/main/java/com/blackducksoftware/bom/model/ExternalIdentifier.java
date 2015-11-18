/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 * 
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bom.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.BlackDuckValue;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
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
        private static final Predicate<ExternalIdentifier> BD_SUITE = new ExternalSystemTypeIdFilter(BlackDuckValue.EXTERNAL_IDENTIFIER_BD_SUITE.id());

        private static final Predicate<ExternalIdentifier> BD_HUB = new ExternalSystemTypeIdFilter(BlackDuckValue.EXTERNAL_IDENTIFIER_BD_HUB.id());

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
                EXTERNAL_SYSTEM_TYPE_ID, EXTERNAL_ID, EXTERNAL_REPOSITORY_LOCATION);
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
        return ExternalSystemTypeIdFilter.BD_SUITE;
    }

    /**
     * Returns a predicate that matches Black Duck Suite external identifiers.
     */
    public static Predicate<ExternalIdentifier> bdHub() {
        return ExternalSystemTypeIdFilter.BD_HUB;
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
        checkState(Objects.equals(getExternalSystemTypeId(), BlackDuckValue.EXTERNAL_IDENTIFIER_BD_SUITE.id()), "not a BD-Suite identifier");
        return Splitter.on('#').limit(2).split(getExternalId());
    }

}
