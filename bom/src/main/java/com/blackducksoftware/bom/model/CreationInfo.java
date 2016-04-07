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
import java.util.Map.Entry;
import java.util.Objects;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

public class CreationInfo extends AbstractEmbeddedModel<CreationInfo> {
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

    @Nullable
    private List<String> creator;

    @Nullable
    private DateTime created;

    public CreationInfo() {
        super(SpdxType.CREATION_INFO, CREATOR, CREATED);
    }

    public static CreationInfo currentTool() {
        Class<?> currentToolClass;
        try {
            currentToolClass = currentToolClass();
        } catch (ClassNotFoundException e) {
            currentToolClass = CreationInfo.class;
        }

        CreationInfo result = new CreationInfo();
        result.setCreated(DateTime.now().withMillisOfSecond(0).withZone(DateTimeZone.UTC));
        result.setCreator(ImmutableList.of(Joiner.on('-').skipNulls().appendTo(new StringBuilder("Tool: "),
                firstNonNull(currentToolClass.getPackage().getImplementationTitle(), currentToolClass.getSimpleName()),
                currentToolClass.getPackage().getImplementationVersion()).toString()));
        return result;
    }

    @VisibleForTesting
    protected static Class<?> currentToolClass() throws ClassNotFoundException {
        // The "current tool" should be the owner of the "main" method:
        for (Entry<Thread, StackTraceElement[]> stackTrace : Thread.getAllStackTraces().entrySet()) {
            StackTraceElement[] stack = stackTrace.getValue();
            if (stack.length > 0 && Objects.equals(stack[stack.length - 1].getMethodName(), "main")) {
                return stackTrace.getKey().getContextClassLoader().loadClass(stack[stack.length - 1].getClassName());
            }
        }
        throw new IllegalStateException("could not locate owner of 'main'");
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
