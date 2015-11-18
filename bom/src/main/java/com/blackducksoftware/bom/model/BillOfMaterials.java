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
import com.blackducksoftware.bom.RdfsTerm;
import com.blackducksoftware.bom.SpdxTerm;

/**
 * The Bill of Materials itself.
 * <p>
 * Note that because the specification version itself is stored within this node, only backwards compatible changes can
 * be made; essentially the parser will always use the latest version of the specification to parse this node.
 *
 * @author jgustie
 */
public class BillOfMaterials extends AbstractTopLevelModel<BillOfMaterials> {
    private static final ModelField<BillOfMaterials, String> SPEC_VERSION = new ModelField<BillOfMaterials, String>(BlackDuckTerm.SPEC_VERSION) {
        @Override
        protected String get(BillOfMaterials billOfMaterials) {
            return billOfMaterials.getSpecVersion();
        }

        @Override
        protected void set(BillOfMaterials billOfMaterials, Object value) {
            billOfMaterials.setSpecVersion(valueToString(value));
        }
    };

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

    @Nullable
    private String specVersion;

    @Nullable
    private String name;

    @Nullable
    private CreationInfo creationInfo;

    @Nullable
    private String comment;

    public BillOfMaterials() {
        super(BlackDuckType.BILL_OF_MATERIALS,
                SPEC_VERSION, NAME, CREATION_INFO, COMMENT);
    }

    @Nullable
    public String getSpecVersion() {
        return specVersion;
    }

    public void setSpecVersion(@Nullable String specVersion) {
        this.specVersion = specVersion;
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
