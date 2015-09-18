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
 * Term constants corresponding to the Software Package Data Exchange properties.
 *
 * @author jgustie
 * @see <a href="http://spdx.org/rdf/ontology/spdx-2-0/">SPDX RDF Terms</a>
 */
public enum SpdxTerm implements Term {

    // NOTE: Not sure what the difference between object vs. data properties is, but this file is organized in two
    // groups preserving that distinction (some are considered both and are commented out the second time they appear).

    AGENT("http://spdx.org/rdf/terms#agent"),
    ALGORITHM("http://spdx.org/rdf/terms#algorithm"),
    ANNOTATION("http://spdx.org/rdf/terms#annotation"),
    ANNOTATION_TYPE("http://spdx.org/rdf/terms#annotationType"),
    ARTIFACT_OF("http://spdx.org/rdf/terms#artifactOf"),
    CHECKSUM("http://spdx.org/rdf/terms#checksum"),
    COPYRIGHT_TEXT("http://spdx.org/rdf/terms#copyrightText"),
    CREATION_INFO("http://spdx.org/rdf/terms#creationInfo"),
    DATA_LICENSE("http://spdx.org/rdf/terms#dataLicense"),
    DESCRIBES_FILE("http://spdx.org/rdf/terms#describesFile"),
    DESCRIBES_PACKAGE("http://spdx.org/rdf/terms#describesPackage"),
    DOWNLOAD_LOCATION("http://spdx.org/rdf/terms#downloadLocation"),
    EXTERNAL_DOCUMENT_REF("http://spdx.org/rdf/terms#externalDocumentRef"),
    FILE_TYPE("http://spdx.org/rdf/terms#fileType"),
    FROM_FILE("http://spdx.org/rdf/terms#fromFile"),
    HAS_EXTRACTED_LICENSING_INFO("http://spdx.org/rdf/terms#hasExtractedLicensingInfo"),
    HAS_FILE("http://spdx.org/rdf/terms#hasFile"),
    LICENSE_CONCLUDED("http://spdx.org/rdf/terms#licenseConcluded"),
    LICENSE_DECLARED("http://spdx.org/rdf/terms#licenseDeclared"),
    LICENSE_EXCEPTION("http://spdx.org/rdf/terms#licenseException"),
    LICENSE_INFO_FROM_FILES("http://spdx.org/rdf/terms#licenseInfoFromFiles"),
    LICENSE_INFO_IN_FILE("http://spdx.org/rdf/terms#licenseInfoInFile"),
    MEMBER("http://spdx.org/rdf/terms#member"),
    ORIGINATOR("http://spdx.org/rdf/terms#originator"),
    PACKAGE_VERIFICATION_CODE("http://spdx.org/rdf/terms#packageVerificationCode"),
    REFERENCES_FILE("http://spdx.org/rdf/terms#referencesFile"),
    RELATED_SPDX_ELEMENT("http://spdx.org/rdf/terms#relatedSpdxElement"),
    RELATIONSHIP("http://spdx.org/rdf/terms#relationship"),
    RELATIONSHIP_TYPE("http://spdx.org/rdf/terms#relationshipType"),
    REVIEWED("http://spdx.org/rdf/terms#reviewed"),
    SPDX_DOCUMENT("http://spdx.org/rdf/terms#spdxDocument"),
    SUPPLIER("http://spdx.org/rdf/terms#supplier"),
    USED_BY("http://spdx.org/rdf/terms#usedBy"),

    ANNOTATION_DATE("http://spdx.org/rdf/terms#annotationDate"),
    ANNOTATOR("http://spdx.org/rdf/terms#annotator"),
    BYTE_RANGE("http://spdx.org/rdf/terms#byteRange"),
    CHECKSUM_VALUE("http://spdx.org/rdf/terms#checksumValue"),
    // COPYRIGHT_TEXT ("http://spdx.org/rdf/terms#copyrightText"),
    CREATED("http://spdx.org/rdf/terms#created"),
    CREATOR("http://spdx.org/rdf/terms#creator"),
    DATE("http://spdx.org/rdf/terms#date"),
    DESCRIPTION("http://spdx.org/rdf/terms#description"),
    // DOWNLOAD_LOCATION ("http://spdx.org/rdf/terms#downloadLocation"),
    EXAMPLE("http://spdx.org/rdf/terms#example"),
    EXTERNAL_DOCUMENT_ID("http://spdx.org/rdf/terms#externalDocumentId"),
    EXTRACTED_TEXT("http://spdx.org/rdf/terms#extractedText"),
    FILE_CONTRIBUTOR("http://spdx.org/rdf/terms#fileContributor"),
    FILE_NAME("http://spdx.org/rdf/terms#fileName"),
    IS_OSI_APPROVED("http://spdx.org/rdf/terms#isOsiApproved"),
    LICENSE_COMMENTS("http://spdx.org/rdf/terms#licenseComments"),
    LICENSE_EXCEPTION_ID("http://spdx.org/rdf/terms#licenseExceptionId"),
    LICENSE_EXCEPTION_TEXT("http://spdx.org/rdf/terms#licenseExceptionText"),
    LICENSE_ID("http://spdx.org/rdf/terms#licenseId"),
    LICENSE_LIST_VERSION("http://spdx.org/rdf/terms#licenseListVersion"),
    LICENSE_TEXT("http://spdx.org/rdf/terms#licenseText"),
    NAME("http://spdx.org/rdf/terms#name"),
    NOTICE_TEXT("http://spdx.org/rdf/terms#noticeText"),
    // ORIGINATOR ("http://spdx.org/rdf/terms#originator"),
    PACKAGE_FILE_NAME("http://spdx.org/rdf/terms#packageFileName"),
    PACKAGE_NAME("http://spdx.org/rdf/terms#packageName"),
    PACKAGE_VERIFICATION_CODE_VALUE("http://spdx.org/rdf/terms#packageVerificationCodeValue"),
    REVIEW_DATE("http://spdx.org/rdf/terms#reviewDate"),
    REVIEWER("http://spdx.org/rdf/terms#reviewer"),
    SOURCE_INFO("http://spdx.org/rdf/terms#sourceInfo"),
    SPEC_VERSION("http://spdx.org/rdf/terms#specVersion"),
    STANDARD_LICENSE_HEADER("http://spdx.org/rdf/terms#standardLicenseHeader"),
    STANDARD_LICENSE_TEMPLATE("http://spdx.org/rdf/terms#standardLicenseTemplate"),
    SUMMARY("http://spdx.org/rdf/terms#summary"),
    // SUPPLIER ("http://spdx.org/rdf/terms#supplier"),
    VERIFICATION_CODE_EXCLUDED_FILE("http://spdx.org/rdf/terms#verificationCodeExcludedFile"),
    VERSION_INFO("http://spdx.org/rdf/terms#versionInfo");

    private final String fullyQualifiedName;

    private SpdxTerm(String fullyQualifiedName) {
        this.fullyQualifiedName = checkNotNull(fullyQualifiedName);
    }

    @Override
    public String toString() {
        return fullyQualifiedName;
    }
}
