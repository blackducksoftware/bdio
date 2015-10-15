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

import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.RdfsTerm;
import com.blackducksoftware.bom.SpdxTerm;

/**
 * The Bill of Materials itself.
 *
 * @author jgustie
 */
public class BillOfMaterials extends AbstractTopLevelModel<BillOfMaterials> {

    @Nullable
    private String name;

    private static final ModelField<BillOfMaterials, String> NAME = new ModelField<BillOfMaterials, String>(SpdxTerm.NAME) {
        @Override
        protected String get(BillOfMaterials billOfMaterials) {
            return billOfMaterials.getName();
        }

        @Override
        protected void set(BillOfMaterials billOfMaterials, Object value) {
            billOfMaterials.setName(valueToString(value));
        }
    };

    @Nullable
    private CreationInfo creationInfo;

    private static final ModelField<BillOfMaterials, CreationInfo> CREATION_INFO = new ModelField<BillOfMaterials, CreationInfo>(SpdxTerm.CREATION_INFO) {
        @Override
        protected CreationInfo get(BillOfMaterials billOfMaterials) {
            return billOfMaterials.getCreationInfo();
        }

        @Override
        protected void set(BillOfMaterials billOfMaterials, Object value) {
            billOfMaterials.setCreationInfo(valueToNodes(value).transformAndConcat(toModel(CreationInfo.class)).first().orNull());
        }
    };

    @Nullable
    private String comment;

    private static final ModelField<BillOfMaterials, String> COMMENT = new ModelField<BillOfMaterials, String>(RdfsTerm.COMMENT) {
        @Override
        protected String get(BillOfMaterials billOfMaterials) {
            return billOfMaterials.getComment();
        }

        @Override
        protected void set(BillOfMaterials billOfMaterials, Object value) {
            billOfMaterials.setComment(valueToString(value));
        }
    };

    public BillOfMaterials() {
        super(BlackDuckType.BILL_OF_MATERIALS,
                NAME, CREATION_INFO, COMMENT);
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Nullable
    public CreationInfo getCreationInfo() {
        return creationInfo;
    }

    public void setCreationInfo(@Nullable CreationInfo creationInfo) {
        this.creationInfo = creationInfo;
    }

    @Nullable
    public String getComment() {
        return comment;
    }

    public void setComment(@Nullable String comment) {
        this.comment = comment;
    }

}
