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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.Term;
import com.blackducksoftware.bom.Type;
import com.google.common.hash.HashCode;

/**
 * A model used for testing. Follows the same pattern as the real models.
 */
public class TestModel extends AbstractModel {

    public enum TestType implements Type {
        TEST;

        @Override
        public String toString() {
            return "http://example.com/terms#Test";
        }
    }

    public enum TestTerm implements Term {
        NAME,
        COUNT,
        FLAG,
        HASH_CODE;

        @Override
        public String toString() {
            return "http://example.com/terms#" + UPPER_UNDERSCORE.to(LOWER_CAMEL, name());
        }
    }

    @Nullable
    private String name;

    @Nullable
    private Long count;

    @Nullable
    private Boolean flag;

    @Nullable
    private HashCode hashCode;

    public TestModel() {
        super(TestType.TEST,
                TestTerm.NAME,
                TestTerm.COUNT,
                TestTerm.FLAG,
                TestTerm.HASH_CODE);
    }

    @Override
    protected Object lookup(Term term) {
        if (term.equals(TestTerm.NAME)) {
            return getName();
        } else if (term.equals(TestTerm.COUNT)) {
            return getCount();
        } else if (term.equals(TestTerm.FLAG)) {
            return getFlag();
        } else if (term.equals(TestTerm.HASH_CODE)) {
            return getHashCode();
        }
        return null;
    }

    @Override
    protected Object store(Term term, Object value) {
        Object original = null;
        if (term.equals(TestTerm.NAME)) {
            original = getName();
            setName(valueToString(value));
        } else if (term.equals(TestTerm.COUNT)) {
            original = getCount();
            setCount(valueToLong(value));
        } else if (term.equals(TestTerm.FLAG)) {
            original = getFlag();
            setFlag(valueToBoolean(value));
        } else if (term.equals(TestTerm.HASH_CODE)) {
            original = getHashCode();
            setHashCode(HashCode.fromString(valueToString(value)));
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

    @Nullable
    public Long getCount() {
        return count;
    }

    public void setCount(@Nullable Long count) {
        this.count = count;
    }

    @Nullable
    public Boolean getFlag() {
        return flag;
    }

    public void setFlag(@Nullable Boolean flag) {
        this.flag = flag;
    }

    @Nullable
    public HashCode getHashCode() {
        return hashCode;
    }

    public void setHashCode(@Nullable HashCode hashCode) {
        this.hashCode = hashCode;
    }

}
