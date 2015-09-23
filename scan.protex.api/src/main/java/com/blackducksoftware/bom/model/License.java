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

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.Term;
import com.blackducksoftware.bom.Type;

/**
 * A license in a Bill of Materials.
 *
 * @author jgustie
 */
public class License extends AbstractModel {

    @Nullable
    private String name;

    @Nullable
    private String legacyId;

    @Nullable
    private String knowledgeBaseId;

    public License(Type type, Term... terms) {
        super(BlackDuckType.LICENSE,
                SpdxTerm.NAME,
                BlackDuckTerm.LEGACY_ID,
                BlackDuckTerm.KNOWLEDGE_BASE_ID);
    }

    @Override
    protected Object lookup(Term term) {
        if (term.equals(SpdxTerm.NAME)) {
            return getName();
        } else if (term.equals(BlackDuckTerm.LEGACY_ID)) {
            return getLegacyId();
        } else if (term.equals(BlackDuckTerm.KNOWLEDGE_BASE_ID)) {
            return getKnowledgeBaseId();
        } else {
            return null;
        }
    }

    @Override
    protected Object store(Term term, Object value) {
        Object original = null;
        if (term.equals(SpdxTerm.NAME)) {
            original = getName();
            setName(valueToString(value));
        } else if (term.equals(BlackDuckTerm.LEGACY_ID)) {
            original = getLegacyId();
            setLegacyId(valueToString(value));
        } else if (term.equals(BlackDuckTerm.KNOWLEDGE_BASE_ID)) {
            original = getKnowledgeBaseId();
            setKnowledgeBaseId(valueToString(value));
        }
        return original;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Nullable
    public String getLegacyId() {
        return legacyId;
    }

    public void setLegacyId(@Nullable String legacyId) {
        this.legacyId = legacyId;
    }

    @Nullable
    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(@Nullable String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

}
