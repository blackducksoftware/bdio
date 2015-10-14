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

import java.util.Objects;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxType;
import com.blackducksoftware.bom.SpdxValue;
import com.google.common.hash.HashCode;

public class Checksum extends AbstractEmbeddedModel<Checksum> {

    @Nullable
    private String algorithm;

    private static final ModelField<Checksum, String> ALGORITHM = new ModelField<Checksum, String>(SpdxTerm.ALGORITHM) {
        @Override
        protected String get(Checksum checksum) {
            return checksum.getAlgorithm();
        }

        @Override
        protected void set(Checksum checksum, Object value) {
            checksum.setAlgorithm(valueToString(value));
        }
    };

    @Nullable
    private HashCode checksumValue;

    private static final ModelField<Checksum, HashCode> CHECKSUM_VALUE = new ModelField<Checksum, HashCode>(SpdxTerm.CHECKSUM_VALUE) {
        @Override
        protected HashCode get(Checksum checksum) {
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
        return hashCode != null ? create(SpdxValue.CHECKSUM_ALGORITHM_MD5.id(), hashCode) : null;
    }

    /**
     * Helper to create a new SHA-1 checksum.
     */
    public static Checksum sha1(HashCode hashCode) {
        return hashCode != null ? create(SpdxValue.CHECKSUM_ALGORITHM_SHA1.id(), hashCode) : null;
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

    public boolean isAlgorithmSha1() {
        return Objects.equals(getAlgorithm(), SpdxValue.CHECKSUM_ALGORITHM_SHA1.id());
    }

    public boolean isAlgorithmMd5() {
        return Objects.equals(getAlgorithm(), SpdxValue.CHECKSUM_ALGORITHM_MD5.id());
    }

    public HashCode getChecksumValue() {
        return checksumValue;
    }

    public void setChecksumValue(HashCode checksumValue) {
        this.checksumValue = checksumValue;
    }

}
