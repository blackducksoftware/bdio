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

import static com.google.common.base.Objects.firstNonNull;

import java.util.List;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.SpdxTerm;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * A license in a Bill of Materials.
 *
 * @author jgustie
 */
public class License extends AbstractModel<License> {

    /**
     * The name of the license.
     */
    @Nullable
    private String name;

    private static final ModelField<License> NAME = new ModelField<License>(SpdxTerm.NAME) {
        @Override
        protected Object get(License license) {
            return license.getName();
        }

        @Override
        protected void set(License license, Object value) {
            license.setName(valueToString(value));
        }
    };

    @Nullable
    private List<ExternalIdentifier> externalIdentifier;

    private static final ModelField<License> EXTERNAL_IDENTIFIER = new ModelField<License>(BlackDuckTerm.EXTERNAL_IDENTIFIER) {
        @Override
        protected Object get(License license) {
            return license.getExternalIdentifier();
        }

        @Override
        protected void set(License license, Object value) {
            license.setExternalIdentifier(emptyToNull(valueToNodes(value).transformAndConcat(toModel(ExternalIdentifier.class)).toList()));
        }
    };

    public License() {
        super(BlackDuckType.LICENSE,
                NAME, EXTERNAL_IDENTIFIER);
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Nullable
    public List<ExternalIdentifier> getExternalIdentifier() {
        return externalIdentifier;
    }

    public void setExternalIdentifier(@Nullable List<ExternalIdentifier> externalIdentifier) {
        this.externalIdentifier = externalIdentifier;
    }

    public License addExternalIdentifier(ExternalIdentifier externalIdentifier) {
        if (externalIdentifier != null) {
            List<ExternalIdentifier> externalIdentifiers = getExternalIdentifier();
            if (externalIdentifiers != null) {
                externalIdentifiers.add(externalIdentifier);
            } else {
                setExternalIdentifier(Lists.newArrayList(externalIdentifier));
            }
        }
        return this;
    }

    public FluentIterable<ExternalIdentifier> externalIdentifiers() {
        return FluentIterable.from(firstNonNull(getExternalIdentifier(), ImmutableList.<ExternalIdentifier> of()));
    }

}
