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
package com.blackducksoftware.bdio;

import java.util.LinkedHashMap;

import javax.annotation.Nullable;

import org.joda.time.Instant;

import com.github.jsonldjava.core.JsonLdConsts;

/**
 * Metadata used to describe a linked data graph.
 *
 * @author jgustie
 */
public final class BdioMetadata extends LinkedHashMap<String, Object> {

    // TODO CI environment

    BdioMetadata(@Nullable String id) {
        if (id != null) {
            put(JsonLdConsts.ID, id);
        }
    }

    public String id() {
        return (String) get(JsonLdConsts.ID);
    }

    public void setCreation(Instant creation) {
        put(Bdio.DataProperty.creation.toString(), creation.toString());
    }

    public void setCreator(String creator) {
        put(Bdio.DataProperty.creator.toString(), creator);
    }

    public void setProducer(String producer) {
        put(Bdio.DataProperty.producer.toString(), producer);
    }

}
