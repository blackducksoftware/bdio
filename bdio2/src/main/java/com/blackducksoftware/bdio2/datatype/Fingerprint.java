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
package com.blackducksoftware.bdio2.datatype;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

/**
 * It is not generally desirable to include complete file contents in a BDIO document, instead file identity can
 * be established using a fingerprint of the file. Typically, the fingerprint will be a SHA-1 hash of the file
 * contents. The fingerprint object is meant to include both the value of the fingerprint and the algorithm used
 * to compute it.
 *
 * @author jgustie
 */
public final class Fingerprint {

    private static final CharMatcher ALPHA = CharMatcher.inRange('A', 'Z').or(CharMatcher.inRange('a', 'z'));

    private static final CharMatcher DIGIT = CharMatcher.inRange('0', '9');

    private static final CharMatcher ALGORITHM_CHARS = ALPHA.or(DIGIT).or(CharMatcher.anyOf("+-."));

    private final String algorithm;

    private final String value;

    private Fingerprint(String algorithm, String value) {
        this.algorithm = Objects.requireNonNull(algorithm);
        this.value = Objects.requireNonNull(value);

        checkArgument(!algorithm.isEmpty(), "algorithm must not be empty");
        checkArgument(ALGORITHM_CHARS.matchesAllOf(algorithm), "algorithm contains illegal characters: '%s;", algorithm);
        checkArgument(!value.isEmpty(), "value must not be empty");
        // TODO Limitations on value characters?
    }

    @JsonCreator
    public static Fingerprint valueOf(String str) {
        List<String> parts = Splitter.on(':').limit(2).splitToList(str);
        checkArgument(parts.size() == 2, "invalid fingerprint, missing algorithm: %s", str);
        return new Fingerprint(parts.get(0), parts.get(1));
    }

    public static Fingerprint create(String algorithm, String value) {
        return new Fingerprint(algorithm, value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(algorithm, value);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof Fingerprint) {
            Fingerprint other = (Fingerprint) obj;
            return algorithm.equals(other.algorithm) && value.equals(other.value);
        } else {
            return false;
        }
    }

    @JsonValue
    @Override
    public String toString() {
        return algorithm + ':' + value;
    }

}
