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

import java.util.Objects;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.BlackDuckValue;
import com.blackducksoftware.bom.SpdxTerm;

public class MatchDetail extends AbstractEmbeddedModel<MatchDetail> {
    private static final ModelField<MatchDetail, String> MATCH_TYPE = new ModelField<MatchDetail, String>(BlackDuckTerm.MATCH_TYPE) {
        @Override
        protected String get(MatchDetail matchDetail) {
            return matchDetail.getMatchType();
        }

        @Override
        protected void set(MatchDetail matchDetail, Object value) {
            matchDetail.setMatchType(valueToString(value));
        }
    };

    private static final ModelField<MatchDetail, String> CONTENT = new ModelField<MatchDetail, String>(BlackDuckTerm.CONTENT) {
        @Override
        protected String get(MatchDetail matchDetail) {
            return matchDetail.getContent();
        }

        @Override
        protected void set(MatchDetail matchDetail, Object value) {
            matchDetail.setContent(valueToString(value));
        }
    };

    private static final ModelField<MatchDetail, String> COMPONENT = new ModelField<MatchDetail, String>(SpdxTerm.ARTIFACT_OF) {
        @Override
        protected String get(MatchDetail matchDetail) {
            return matchDetail.getComponent();
        }

        @Override
        protected void set(MatchDetail matchDetail, Object value) {
            matchDetail.setComponent(valueToString(value));
        }
    };

    private static final ModelField<MatchDetail, String> LICENSE = new ModelField<MatchDetail, String>(SpdxTerm.LICENSE_CONCLUDED) {
        @Override
        protected String get(MatchDetail matchDetail) {
            return matchDetail.getLicense();
        }

        @Override
        protected void set(MatchDetail matchDetail, Object value) {
            matchDetail.setLicense(valueToString(value));
        }
    };

    // TODO Should match type be more fine grained (like Java Package, C include)?

    @Nullable
    private String matchType;

    @Nullable
    private String content;

    @Nullable
    private String component;

    @Nullable
    private String license;

    public MatchDetail() {
        super(BlackDuckType.MATCH_DETAIL,
                MATCH_TYPE, CONTENT, COMPONENT, LICENSE);
    }

    /**
     * Helper to create a new dependency match.
     */
    public static MatchDetail dependency(String dependency, @Nullable String componentId) {
        return componentId != null ? create(BlackDuckValue.MATCH_TYPE_DEPENDENCY.id(), dependency, componentId) : null;
    }

    /**
     * Helper to create a new partial match.
     */
    public static MatchDetail partial(@Nullable String componentId) {
        return componentId != null ? create(BlackDuckValue.MATCH_TYPE_PARTIAL.id(), null, componentId) : null;
    }

    /**
     * Helper to override (or explicitly specify) a component's license in a world where {@code null} exists.
     */
    @Nullable
    public static MatchDetail withLicense(@Nullable MatchDetail matchDetail, @Nullable String licenseId) {
        if (matchDetail != null) {
            matchDetail.setLicense(licenseId);
        }
        return matchDetail;
    }

    private static MatchDetail create(String matchType, @Nullable String content, @Nullable String component) {
        MatchDetail matchDetail = new MatchDetail();
        matchDetail.setMatchType(checkNotNull(matchType));
        matchDetail.setContent(content);
        matchDetail.setComponent(component);
        return matchDetail;
    }

    @Nullable
    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(@Nullable String matchType) {
        this.matchType = matchType;
    }

    public boolean isMatchTypeDependency() {
        return Objects.equals(getMatchType(), BlackDuckValue.MATCH_TYPE_DEPENDENCY.id());
    }

    public boolean isMatchTypePartial() {
        return Objects.equals(getMatchType(), BlackDuckValue.MATCH_TYPE_PARTIAL.id());
    }

    @Nullable
    public String getContent() {
        return content;
    }

    public void setContent(@Nullable String content) {
        this.content = content;
    }

    @Nullable
    public String getComponent() {
        return component;
    }

    public void setComponent(@Nullable String component) {
        this.component = component;
    }

    @Nullable
    public String getLicense() {
        return license;
    }

    public void setLicense(@Nullable String license) {
        this.license = license;
    }

}
