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
import static com.google.common.base.Strings.nullToEmpty;

import java.util.UUID;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio.BlackDuckValue;
import com.blackducksoftware.bdio.Node;
import com.google.common.base.Joiner;
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

    public ExternalIdentifierBuilder cpan(String... moduleNames) {
        // TODO Eliminate varargs and null ambiguity
        return formatId("%s", Joiner.on("::").join(moduleNames)).systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_CPAN).withoutRepo();
    }

    public ExternalIdentifierBuilder goget(@Nullable String packageImportPath) {
        return formatId("%s", packageImportPath).systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_GOGET).withoutRepo();
    }

    public ExternalIdentifierBuilder maven(@Nullable String groupId, @Nullable String artifactId, @Nullable String packaging, @Nullable String classifier,
            @Nullable String version) {
        StringBuilder id = new StringBuilder();
        id.append("%1$s:%2$s");
        if (packaging != null) {
            id.append(":%3$s");
            if (classifier != null) {
                id.append(":%4$s");
            }
        }
        if (packaging != null || version != null) {
            id.append(":%5$s");
        }
        return formatId(id.toString(), groupId, artifactId, nullToEmpty(packaging), nullToEmpty(classifier), nullToEmpty(version))
                .systemTypeId(BlackDuckValue.EXTERNAL_IDENTIFIER_MAVEN)
                .withoutRepo();
    }

    public ExternalIdentifierBuilder maven(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
        return maven(groupId, artifactId, null, null, version);
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