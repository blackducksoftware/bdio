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

import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxType;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

public class CreationInfo extends AbstractEmbeddedModel<CreationInfo> {

    @Nullable
    private List<String> creator;

    private static final ModelField<CreationInfo, List<String>> CREATOR = new ModelField<CreationInfo, List<String>>(SpdxTerm.CREATOR) {
        @Override
        protected List<String> get(CreationInfo creationInfo) {
            return creationInfo.getCreator();
        }

        @Override
        protected void set(CreationInfo creationInfo, Object value) {
            creationInfo.setCreator(valueToStrings(value).toList());
        }
    };

    @Nullable
    private DateTime created;

    private static final ModelField<CreationInfo, DateTime> CREATED = new ModelField<CreationInfo, DateTime>(SpdxTerm.CREATED) {
        @Override
        protected DateTime get(CreationInfo creationInfo) {
            return creationInfo.getCreated();
        }

        @Override
        protected void set(CreationInfo creationInfo, Object value) {
            creationInfo.setCreated(valueToDateTime(value));
        }
    };

    public CreationInfo() {
        super(SpdxType.CREATION_INFO,
                CREATOR, CREATED);
    }

    public static CreationInfo currentTool() {
        CreationInfo result = new CreationInfo();
        result.setCreated(DateTime.now().withMillisOfSecond(0).withZone(DateTimeZone.UTC));
        result.setCreator(ImmutableList.of(Joiner.on('-').skipNulls().appendTo(new StringBuilder("Tool: "),
                CreationInfo.class.getPackage().getImplementationTitle(),
                CreationInfo.class.getPackage().getImplementationVersion()).toString()));
        return result;
    }

    @Nullable
    public List<String> getCreator() {
        return creator;
    }

    public void setCreator(@Nullable List<String> creator) {
        this.creator = creator;
    }

    @Nullable
    public DateTime getCreated() {
        return created;
    }

    public void setCreated(@Nullable DateTime created) {
        this.created = created;
    }

}
