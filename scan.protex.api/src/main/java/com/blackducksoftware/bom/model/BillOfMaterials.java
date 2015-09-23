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

import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.Term;

/**
 * The Bill of Materials itself.
 *
 * @author jgustie
 */
public class BillOfMaterials extends AbstractModel {

    public BillOfMaterials() {
        super(BlackDuckType.BILL_OF_MATERIALS);
    }

    @Override
    protected Object lookup(Term term) {
        return null;
    }

    @Override
    protected Object store(Term term, Object value) {
        return null;
    }

}
