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

import com.blackducksoftware.bom.Type;

/**
 * Base class for embedded models. Embedded models are used as values for properties on other models.
 *
 * @author jgustie
 */
public class AbstractEmbeddedModel<M extends AbstractEmbeddedModel<M>> extends AbstractModel<M> {

    protected AbstractEmbeddedModel(Type type, ModelField<M>... fields) {
        super(type, fields);
    }

}
