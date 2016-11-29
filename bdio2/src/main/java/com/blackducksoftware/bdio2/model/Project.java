/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio2.model;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioObject;

public class Project extends BdioObject {

    public Project(String id) {
        this(id, Bdio.Class.Project);
    }

    // Pass through constructor for subclasses
    protected Project(String id, Bdio.Class bdioClass) {
        super(id, bdioClass);
    }

    public String name() {
        return getString(Bdio.DataProperty.name);
    }

    public Project name(String name) {
        putString(Bdio.DataProperty.name, name);
        return this;
    }

}
