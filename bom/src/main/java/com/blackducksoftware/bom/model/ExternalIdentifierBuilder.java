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

import java.util.UUID;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckValue;
import com.blackducksoftware.bom.Node;
import com.google.common.base.Optional;

/**
 * A helper for building external identifiers.
 *
 * @author jgustie
 */
public class ExternalIdentifierBuilder {

    private boolean present;

    @Nullable
    private String externalSystemTypeId;

    @Nullable
    private String externalId;

    @Nullable
    private String externalRepositoryLocation;

    private ExternalIdentifierBuilder() {
    }

    /**
     * Creates a new external identifier builder.
     */
    public static ExternalIdentifierBuilder create() {
        return new ExternalIdentifierBuilder();
    }

    /**
     * Builds an optional external identifier from the current state of this builder.
     */
    public Optional<ExternalIdentifier> build() {
        ExternalIdentifier result = null;
        if (present) {
            result = new ExternalIdentifier();
            result.setExternalSystemTypeId(externalSystemTypeId);
            result.setExternalId(externalId);
            result.setExternalRepositoryLocation(externalRepositoryLocation);
        }
        return Optional.fromNullable(result);
    }

    /**
     * Conditionally adds an external identifier representing the current state of this builder to the optionally
     * supplied model.
     */
    public ExternalIdentifierBuilder append(@Nullable AbstractTopLevelModel<?> model) {
        if (present && model != null) {
            model.addExternalIdentifier(build().get());
        }
        return this;
    }

    // Internal helpers

    protected ExternalIdentifierBuilder present() {
        present = true;
        return this;
    }

    protected ExternalIdentifierBuilder absent() {
        present = false;
        return this;
    }

    // "Smart" state

    /**
     * Updates the external identifier of this builder. Unlike {@link #id(String)}, a non-{@code null} value will
     * trigger this builder to be in a "present" state (i.e. the {@link #build()} will no longer return absent).
     */
    public ExternalIdentifierBuilder fromId(@Nullable String externalId) {
        if (externalId != null) {
            return id(externalId).present();
        } else {
            return id(null).absent();
        }
    }

    /**
     * Creates a new identifier by formatting the supplied arguments to a specified pattern. If any of the arguments are
     * {@code null} the external identifier is cleared and this builder is put into an "absent" state.
     */
    public ExternalIdentifierBuilder formatId(String format, Object... args) {
        checkNotNull(format);
        for (Object arg : args) {
            if (arg == null) {
                return fromId(null);
            }
        }
        return fromId(String.format(format, args));
    }

    /**
     * Clears the external repository location of this builder.
     */
    public ExternalIdentifierBuilder withoutRepo() {
        externalRepositoryLocation = null;
        return this;
    }

    // "Dumb" state

    public ExternalIdentifierBuilder id(@Nullable String externalId) {
        this.externalId = externalId;
        return this;
    }

    public ExternalIdentifierBuilder systemTypeId(@Nullable String externalSystemTypeId) {
        this.externalSystemTypeId = externalSystemTypeId;
        return this;
    }

    public ExternalIdentifierBuilder systemTypeId(@Nullable Node externalSystemType) {
        externalSystemTypeId = externalSystemType != null ? externalSystemType.id() : null;
        return this;
    }

    public ExternalIdentifierBuilder repositoryLocation(@Nullable String externalRepositoryLocation) {
        this.externalRepositoryLocation = externalRepositoryLocation;
        return this;
    }

    // Repository specific formats

    public ExternalIdentifierBuilder anaconda(@Nullable String packageName, @Nullable String version) {
        return formatId("%s=%s", packageName, version).systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_ANACONDA).withoutRepo();
    }

    public ExternalIdentifierBuilder bower(@Nullable String packageName, @Nullable String version) {
        return formatId("%s#%s", packageName, version).systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_BOWER).withoutRepo();
    }

    public ExternalIdentifierBuilder cpan() {
        // TODO
        return systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_CPAN).withoutRepo();
    }

    public ExternalIdentifierBuilder goget() {
        // TODO
        return systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_GOGET).withoutRepo();
    }

    // TODO Maven classifier? Packaging?
    public ExternalIdentifierBuilder maven(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
        systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_MAVEN).withoutRepo();
        if (version != null) {
            return formatId("%s:%s:%s", groupId, artifactId, version);
        } else {
            return formatId("%s:%s", groupId, artifactId);
        }
    }

    public ExternalIdentifierBuilder npm(@Nullable String packageName, @Nullable String version) {
        return formatId("%s@%s", packageName, version).systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_NPM).withoutRepo();
    }

    public ExternalIdentifierBuilder nuget(@Nullable String packageName, @Nullable String version) {
        return formatId("%s/%s", packageName, version).systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_NUGET).withoutRepo();
    }

    public ExternalIdentifierBuilder rubygem(@Nullable String gem, @Nullable String version) {
        return formatId("%s=%s", gem, version).systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_RUBYGEMS).withoutRepo();
    }

    public ExternalIdentifierBuilder blackDuckSuite(@Nullable String id) {
        return fromId(id).systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_BDSUITE).withoutRepo();
    }

    public ExternalIdentifierBuilder blackDuckSuite(@Nullable String id, @Nullable String tag) {
        return (tag != null ? formatId("%s#%s", id, tag) : fromId(id)).systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_BDSUITE).withoutRepo();
    }

    public ExternalIdentifierBuilder blackDuckHub(String entityType, @Nullable UUID id) {
        checkNotNull(entityType);
        return formatId("%s~%s", entityType, id).systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_BDHUB).withoutRepo();
    }

    public ExternalIdentifierBuilder blackDuckOpenHub(@Nullable String id) {
        return fromId(id).systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_OPENHUB).withoutRepo();
    }

}
