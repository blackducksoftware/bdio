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
package com.blackducksoftware.bom;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Type constants corresponding to the Software Package Data Exchange classes.
 *
 * @author jgustie
 * @see <a href="http://spdx.org/rdf/ontology/spdx-2-0/">SPDX RDF Terms</a>
 */
public enum SpdxType implements Type {

    ANNOTATION("http://spdx.org/rdf/terms#Annotation"),
    ANY_LICENSE_INFO("http://spdx.org/rdf/terms#AnyLicenseInfo"),
    CHECKSUM("http://spdx.org/rdf/terms#Checksum"),
    CONJUNCTIVE_LICENSE_SET("http://spdx.org/rdf/terms#ConjunctiveLicenseSet"),
    CREATION_INFO("http://spdx.org/rdf/terms#CreationInfo"),
    DISJUNCTIVE_LICENSE_SET("http://spdx.org/rdf/terms#DisjunctiveLicenseSet"),
    EXTERNAL_DOCUMENT_REF("http://spdx.org/rdf/terms#ExternalDocumentRef"),
    EXTRACTED_LICENSE_INFO("http://spdx.org/rdf/terms#ExtractedLicenseInfo"),
    FILE("http://spdx.org/rdf/terms#File"),
    LICENSE("http://spdx.org/rdf/terms#License"),
    LICENSE_EXCEPTION("http://spdx.org/rdf/terms#LicenseException"),
    LISTED_LICENSE("http://spdx.org/rdf/terms#ListedLicense"),
    OR_LATER_OPERATOR("http://spdx.org/rdf/terms#OrLaterOperator"),
    PACKAGE("http://spdx.org/rdf/terms#Package"),
    PACKAGE_VERIFICATION_CODE("http://spdx.org/rdf/terms#PackageVerificationCode"),
    RELATIONSHIP("http://spdx.org/rdf/terms#Relationship"),
    REVIEW("http://spdx.org/rdf/terms#Review"),
    SIMPLE_LICENSEING_INFO("http://spdx.org/rdf/terms#SimpleLicenseingInfo"),
    SNIPPET("http://spdx.org/rdf/terms#Snippet"),
    SPDX_DOCUMENT("http://spdx.org/rdf/terms#SpdxDocument"),
    SPDX_ELEMENT("http://spdx.org/rdf/terms#SpdxElement"),
    SPDX_ITEM("http://spdx.org/rdf/terms#SpdxItem"),
    WITH_EXCEPTION_OPERATOR("http://spdx.org/rdf/terms#WithExceptionOperator");

    private final String fullyQualifiedName;

    private SpdxType(String fullyQualifiedName) {
        this.fullyQualifiedName = checkNotNull(fullyQualifiedName);
    }

    @Override
    public String toString() {
        return fullyQualifiedName;
    }
}
