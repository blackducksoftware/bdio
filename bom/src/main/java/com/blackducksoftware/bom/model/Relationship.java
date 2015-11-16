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

import javax.annotation.Nullable;

import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxType;
import com.blackducksoftware.bom.SpdxValue;

public class Relationship extends AbstractEmbeddedModel<Relationship> {

    @Nullable
    private String related;

    private static final ModelField<Relationship, String> RELATED = new ModelField<Relationship, String>(SpdxTerm.RELATED_SPDX_ELEMENT) {
        @Override
        protected String get(Relationship relationship) {
            return relationship.getRelated();
        }

        @Override
        protected void set(Relationship relationship, Object value) {
            relationship.setRelated(valueToString(value));
        }
    };

    @Nullable
    private String relationshipType;

    private static final ModelField<Relationship, String> RELATIONSHIP_TYPE = new ModelField<Relationship, String>(SpdxTerm.RELATIONSHIP_TYPE) {
        @Override
        protected String get(Relationship relationship) {
            return relationship.getRelationshipType();
        }

        @Override
        protected void set(Relationship relationship, Object value) {
            relationship.setRelationshipType(valueToString(value));
        }
    };

    public Relationship() {
        super(SpdxType.RELATIONSHIP,
                RELATED, RELATIONSHIP_TYPE);
    }

    /**
     * Helper to create a new dynamic link relationship.
     */
    @Nullable
    public static Relationship dynamicLink(@Nullable String id) {
        return id != null ? create(id, SpdxValue.RELATIONSHIP_TYPE_DYNAMIC_LINK.id()) : null;
    }

    private static Relationship create(String related, String relationshipType) {
        Relationship relationship = new Relationship();
        relationship.setRelated(checkNotNull(related));
        relationship.setRelationshipType(checkNotNull(relationshipType));
        return relationship;
    }

    @Nullable
    public String getRelated() {
        return related;
    }

    public void setRelated(@Nullable String related) {
        this.related = related;
    }

    @Nullable
    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(@Nullable String relationshipType) {
        this.relationshipType = relationshipType;
    }

}
