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

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxType;
import com.google.common.hash.HashCode;

public class Checksum extends AbstractModel<Checksum> {

    @Nullable
    private String algorithm;

    private static final ModelField<Checksum> ALGORITHM = new ModelField<Checksum>(SpdxTerm.ALGORITHM) {
        @Override
        protected Object get(Checksum checksum) {
            return checksum.getAlgorithm();
        }

        @Override
        protected void set(Checksum checksum, Object value) {
            checksum.setAlgorithm(valueToString(value));
        }
    };

    @Nullable
    private HashCode checksumValue;

    private static final ModelField<Checksum> CHECKSUM_VALUE = new ModelField<Checksum>(SpdxTerm.CHECKSUM_VALUE) {
        @Override
        protected Object get(Checksum checksum) {
            return checksum.getChecksumValue();
        }

        @Override
        protected void set(Checksum checksum, Object value) {
            checksum.setChecksumValue(HashCode.fromString(valueToString(value)));
        }
    };

    public Checksum() {
        super(SpdxType.CHECKSUM,
                ALGORITHM, CHECKSUM_VALUE);
    }

    /**
     * Helper to create a new MD5 checksum.
     */
    public static Checksum md5(HashCode hashCode) {
        return hashCode != null ? create("http://spdx.org/rdf/terms#checksumAlgorithm_md5", hashCode) : null;
    }

    /**
     * Helper to create a new SHA-1 checksum.
     */
    public static Checksum sha1(HashCode hashCode) {
        return hashCode != null ? create("http://spdx.org/rdf/terms#checksumAlgorithm_sha1", hashCode) : null;
    }

    private static Checksum create(String algorithm, HashCode checksumValue) {
        Checksum checksum = new Checksum();
        checksum.setAlgorithm(checkNotNull(algorithm));
        checksum.setChecksumValue(checkNotNull(checksumValue));
        return checksum;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public HashCode getChecksumValue() {
        return checksumValue;
    }

    public void setChecksumValue(HashCode checksumValue) {
        this.checksumValue = checksumValue;
    }

}
