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

import java.util.Objects;

import javax.annotation.Nullable;

import com.google.common.base.CharMatcher;

/**
 * A single product specifier. Includes a name and optional version along with an optional comment (which is not
 * considered for equality checks). Product specifiers must use a limited subset of ASCII characters: they must not
 * contain whitespace or any delimiter characters (<code>"(),/:;<=>?@[\]{}</code>).
 *
 * @author jgustie
 */
public final class Product {

    private static final CharMatcher ALPHA = CharMatcher.inRange('A', 'Z').or(CharMatcher.inRange('a', 'z'));

    private static final CharMatcher DIGIT = CharMatcher.inRange('0', '9');

    private static final CharMatcher OBS_TEXT = CharMatcher.inRange((char) 0x80, (char) 0xFF);

    private static final CharMatcher TOKEN_CHAR = CharMatcher.anyOf("!#$%&'*+-.^_`|~").or(ALPHA).or(DIGIT);

    private static final CharMatcher COMMENT_CHAR = CharMatcher.inRange(' ', '~').or(CharMatcher.is('\t')).or(OBS_TEXT);

    private static final CharMatcher WS = CharMatcher.is(' ').or(CharMatcher.is('\t'));

    private final String name;

    @Nullable
    private final String version;

    // TODO Technically the comment belongs to the list...
    @Nullable
    private final String comment;

    private Product(String name, @Nullable String version, @Nullable String comment) {
        this.name = Objects.requireNonNull(name);
        this.version = version;
        this.comment = comment;

        checkArgument(!name.isEmpty(), "name must not be empty");
        checkArgument(TOKEN_CHAR.matchesAllOf(name), "name contains illegal characters: '%s'", name);
        if (version != null) {
            checkArgument(!version.isEmpty(), "version must not be empty");
            checkArgument(version.indexOf('(') < 0, "possible missing whitespace between version and comment: '%s'", version);
            checkArgument(TOKEN_CHAR.matchesAllOf(version), "version contains illegal characters: '%s;", version);
        }
        if (comment != null) {
            checkArgument(comment.charAt(0) == '(', "comment must start with '('");
            checkArgument(comment.charAt(comment.length() - 1) == ')', "comment must end with ')'");
            checkArgument(COMMENT_CHAR.matchesAllOf(comment), "comment contains illegal characters: '%s'", comment);
        }
    }

    public static Product valueOf(String value) {
        String name = Objects.requireNonNull(value);
        String version = null;
        String comment = null;

        int versionStart = name.indexOf('/');
        if (versionStart > 0) {
            version = name.substring(versionStart + 1);
            name = name.substring(0, versionStart);
        }

        String last = version != null ? version : name;
        int commentStart = last.indexOf('(');
        if (commentStart > 1) { // ( product RWS ) cannot be less then 2 characters
            comment = last.substring(commentStart);
            last = WS.trimTrailingFrom(last.substring(0, commentStart));
            if (version != null) {
                version = last;
            } else {
                name = last;
            }
        }

        return new Product(name, version, comment);
    }

    public static Product create(String name) {
        return new Product(name, null, null);
    }

    public static Product create(String name, @Nullable String version) {
        return new Product(name, version, null);
    }

    public String name() {
        return name;
    }

    @Nullable
    public String version() {
        return version;
    }

    @Nullable
    public String comment() {
        return comment;
    }

    public Product withComment(String comment, Object... args) {
        return new Product(name, version, '(' + String.format(comment, args) + ')');
    }

    public Product appendComment(String comment, Object... args) {
        return this.comment != null ? new Product(name, version, this.comment + " (" + String.format(comment, args) + ")") : withComment(comment);
    }

    public Product withoutComment() {
        return comment != null ? new Product(name, version, null) : this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof Product) {
            Product other = (Product) obj;
            return name.equals(other.name) && Objects.equals(version, other.version);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder().append(name);
        if (version != null) {
            result.append('/').append(version);
        }
        if (comment != null) {
            result.append(' ').append(comment);
        }
        return result.toString();
    }

}
