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
import com.blackducksoftware.bom.DoapTerm;
import com.blackducksoftware.bom.Term;

/**
 * A project in a Bill of Materials.
 *
 * @author jgustie
 */
public class Project extends AbstractModel {

    @Nullable
    private String name;

    public Project() {
        super(BlackDuckType.PROJECT,
                DoapTerm.NAME);
    }

    @Override
    protected Object lookup(Term term) {
        if (term.equals(DoapTerm.NAME)) {
            return getName();
        } else {
            return null;
        }
    }

    @Override
    protected Object store(Term term, Object value) {
        Object original = null;
        if (term.equals(DoapTerm.NAME)) {
            original = getName();
            setName(valueToString(value));
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

}
