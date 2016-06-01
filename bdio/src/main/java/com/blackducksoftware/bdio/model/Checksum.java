/*
 * Copyright 2015 Black Duck Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blackducksoftware.bdio.model;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio.SpdxTerm;
import com.blackducksoftware.bdio.SpdxType;
import com.blackducksoftware.bdio.SpdxValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;

public class Checksum extends AbstractEmbeddedModel<Checksum> {
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

    @Nullable
    private String algorithm;

    @Nullable
    private HashCode checksumValue;

    public Checksum() {
        super(SpdxType.CHECKSUM,
                ImmutableSet.of(ALGORITHM, CHECKSUM_VALUE));
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
