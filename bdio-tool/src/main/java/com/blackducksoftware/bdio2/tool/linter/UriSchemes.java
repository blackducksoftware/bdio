/*
 * Copyright 2018 Synopsys, Inc.
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
package com.blackducksoftware.bdio2.tool.linter;

import com.google.common.base.Ascii;

/**
 * Various filters for URI schemes.
 */
final class UriSchemes {

    /**
     * Accepts for "base" schemes, i.e. non-archive schemes that can be used at the base of the HID.
     */
    public static boolean isBaseScheme(CharSequence scheme) {
        return isSchemeEquals(scheme, "file")
                || isSchemeEquals(scheme, "http")
                || isSchemeEquals(scheme, "https")
                || isSchemeEquals(scheme, "ftp");
    }

    /**
     * Accepts schemes that should include an authority.
     * <p>
     * <em>IMPORTANT:</em> This includes {@code file}.
     */
    public static boolean isAuthorityScheme(CharSequence scheme) {
        return isSchemeEquals(scheme, "file")
                || isSchemeEquals(scheme, "http")
                || isSchemeEquals(scheme, "https")
                || isSchemeEquals(scheme, "ftp");
    }

    /**
     * Accepts schemes that are not suitable for use as an identifier.
     */
    public static boolean isDegradedScheme(CharSequence scheme) {
        return isSchemeEquals(scheme, "about")
                || isSchemeEquals(scheme, "chrome")
                || isSchemeEquals(scheme, "data")
                || isSchemeEquals(scheme, "mailto");
    }

    /**
     * Internal ignore case helper.
     */
    private static boolean isSchemeEquals(CharSequence scheme1, CharSequence scheme2) {
        return Ascii.equalsIgnoreCase(scheme1, scheme2);
    }

    private UriSchemes() {
        assert false;
    }
}
