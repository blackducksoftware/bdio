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
package com.blackducksoftware.bom.model;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxType;
import com.blackducksoftware.bom.SpdxValue;

public class Relationship extends AbstractEmbeddedModel<Relationship> {
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

    @Nullable
    private String related;

    @Nullable
    private String relationshipType;

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
