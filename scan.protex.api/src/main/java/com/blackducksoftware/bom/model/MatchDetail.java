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

    // TODO Should match type be more fine grained (like Java Package, C include)?

    @Nullable
    private String matchType;

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

    @Nullable
    private String content;

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

    @Nullable
    private String component;

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

    public MatchDetail() {
        super(BlackDuckType.MATCH_DETAIL,
                MATCH_TYPE, CONTENT, COMPONENT);
    }

    /**
     * Helper to create a new dependency match.
     */
    public static MatchDetail dependency(String dependency, @Nullable String componentId) {
        return dependency != null ? create(BlackDuckValue.MATCH_TYPE_DEPENDENCY.id(), dependency, componentId) : null;
    }

    private static MatchDetail create(String matchType, String content, @Nullable String component) {
        MatchDetail matchDetail = new MatchDetail();
        matchDetail.setMatchType(checkNotNull(matchType));
        matchDetail.setContent(checkNotNull(content));
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

}