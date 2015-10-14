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

import java.net.URI;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.Term;
import com.blackducksoftware.bom.Type;
import com.google.common.hash.HashCode;

/**
 * A model used for testing. Follows the same pattern as the real models.
 */
public class TestModel extends AbstractModel<TestModel> {

    public enum TestType implements Type {
        TEST;

        @Override
        public String toString() {
            return "http://example.com/terms#Test";
        }

        @Override
        public URI toUri() {
            return URI.create(toString());
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

        @Override
        public URI toUri() {
            return URI.create(toString());
        }
    }

    @Nullable
    private String name;

    private static final ModelField<TestModel, String> NAME = new ModelField<TestModel, String>(TestTerm.NAME) {
        @Override
        protected String get(TestModel testModel) {
            return testModel.getName();
        }

        @Override
        protected void set(TestModel testModel, Object value) {
            testModel.setName(valueToString(value));
        }
    };

    @Nullable
    private Long count;

    private static final ModelField<TestModel, Long> COUNT = new ModelField<TestModel, Long>(TestTerm.COUNT) {
        @Override
        protected Long get(TestModel testModel) {
            return testModel.getCount();
        }

        @Override
        protected void set(TestModel testModel, Object value) {
            testModel.setCount(valueToLong(value));
        }
    };

    @Nullable
    private Boolean flag;

    private static final ModelField<TestModel, Boolean> FLAG = new ModelField<TestModel, Boolean>(TestTerm.FLAG) {
        @Override
        protected Boolean get(TestModel testModel) {
            return testModel.getFlag();
        }

        @Override
        protected void set(TestModel testModel, Object value) {
            testModel.setFlag(valueToBoolean(value));
        }
    };

    @Nullable
    private HashCode hashCode;

    private static final ModelField<TestModel, HashCode> HASH_CODE = new ModelField<TestModel, HashCode>(TestTerm.HASH_CODE) {
        @Override
        protected HashCode get(TestModel testModel) {
            return testModel.getHashCode();
        }

        @Override
        protected void set(TestModel testModel, Object value) {
            // NOTE: the incoming value can be null!
            testModel.setHashCode(value != null ? HashCode.fromString(valueToString(value)) : null);
        }
    };

    public TestModel() {
        super(TestType.TEST,
                NAME, COUNT, FLAG, HASH_CODE);
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
