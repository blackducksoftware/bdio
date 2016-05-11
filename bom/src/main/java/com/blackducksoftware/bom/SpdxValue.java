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
package com.blackducksoftware.bom;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Node constants corresponding to the Software Package Data Exchange individuals.
 *
 * @author jgustie
 * @see <a href="http://spdx.org/rdf/ontology/spdx-2-0/">SPDX RDF Terms</a>
 */
public enum SpdxValue implements Node {

    ANNOTATION_TYPE_OTHER("http://spdx.org/rdf/terms#annotationType_other"),
    ANNOTATION_TYPE_REVIEW("http://spdx.org/rdf/terms#annotationType_review"),
    CHECKSUM_ALGORITHM_MD5("http://spdx.org/rdf/terms#checksumAlgorithm_md5"),
    CHECKSUM_ALGORITHM_SHA1("http://spdx.org/rdf/terms#checksumAlgorithm_sha1"),
    CHECKSUM_ALGORITHM_SHA256("http://spdx.org/rdf/terms#checksumAlgorithm_sha256"),
    FILE_TYPE_APPLICATION("http://spdx.org/rdf/terms#fileType_application"),
    FILE_TYPE_ARCHIVE("http://spdx.org/rdf/terms#fileType_archive"),
    FILE_TYPE_AUDIO("http://spdx.org/rdf/terms#fileType_audio"),
    FILE_TYPE_BINARY("http://spdx.org/rdf/terms#fileType_binary"),
    FILE_TYPE_DOCUMENTATION("http://spdx.org/rdf/terms#fileType_documentation"),
    FILE_TYPE_IMAGE("http://spdx.org/rdf/terms#fileType_image"),
    FILE_TYPE_OTHER("http://spdx.org/rdf/terms#fileType_other"),
    FILE_TYPE_SOURCE("http://spdx.org/rdf/terms#fileType_source"),
    FILE_TYPE_SPDX("http://spdx.org/rdf/terms#fileType_spdx"),
    FILE_TYPE_TEXT("http://spdx.org/rdf/terms#fileType_text"),
    FILE_TYPE_VIDEO("http://spdx.org/rdf/terms#fileType_video"),
    NOASSERTION("http://spdx.org/rdf/terms#noassertion"),
    NONE("http://spdx.org/rdf/terms#none"),
    RELATIONSHIP_TYPE_AMENDS("http://spdx.org/rdf/terms#relationshipType_amends"),
    RELATIONSHIP_TYPE_ANCESTOR_OF("http://spdx.org/rdf/terms#relationshipType_ancestorOf"),
    RELATIONSHIP_TYPE_BUILD_TOOL_OF("http://spdx.org/rdf/terms#relationshipType_buildToolOf"),
    RELATIONSHIP_TYPE_CONTAINED_BY("http://spdx.org/rdf/terms#relationshipType_containedBy"),
    RELATIONSHIP_TYPE_CONTAINS("http://spdx.org/rdf/terms#relationshipType_contains"),
    RELATIONSHIP_TYPE_COPY_OF("http://spdx.org/rdf/terms#relationshipType_copyOf"),
    RELATIONSHIP_TYPE_DATA_FILE("http://spdx.org/rdf/terms#relationshipType_dataFile"),
    RELATIONSHIP_TYPE_DESCENDANT_OF("http://spdx.org/rdf/terms#relationshipType_descendantOf"),
    RELATIONSHIP_TYPE_DESCRIBED_BY("http://spdx.org/rdf/terms#relationshipType_describedBy"),
    RELATIONSHIP_TYPE_DESCRIBES("http://spdx.org/rdf/terms#relationshipType_describes"),
    RELATIONSHIP_TYPE_DISTRIBUTION_ARTIFACT("http://spdx.org/rdf/terms#relationshipType_distributionArtifact"),
    RELATIONSHIP_TYPE_DOCUMENTATION("http://spdx.org/rdf/terms#relationshipType_documentation"),
    RELATIONSHIP_TYPE_DYNAMIC_LINK("http://spdx.org/rdf/terms#relationshipType_dynamicLink"),
    RELATIONSHIP_TYPE_EXPANDED_FROM_ARCHIVE("http://spdx.org/rdf/terms#relationshipType_expandedFromArchive"),
    RELATIONSHIP_TYPE_FILE_ADDED("http://spdx.org/rdf/terms#relationshipType_fileAdded"),
    RELATIONSHIP_TYPE_FILE_DELETED("http://spdx.org/rdf/terms#relationshipType_fileDeleted"),
    RELATIONSHIP_TYPE_FILE_MODIFIED("http://spdx.org/rdf/terms#relationshipType_fileModified"),
    RELATIONSHIP_TYPE_GENERATED_FROM("http://spdx.org/rdf/terms#relationshipType_generatedFrom"),
    RELATIONSHIP_TYPE_GENERATES("http://spdx.org/rdf/terms#relationshipType_generates"),
    RELATIONSHIP_TYPE_HAS_PREREQUISITE("http://spdx.org/rdf/terms#relationshipType_hasPrerequisite"),
    RELATIONSHIP_TYPE_METAFILE_OF("http://spdx.org/rdf/terms#relationshipType_metafileOf"),
    RELATIONSHIP_TYPE_OPTIONAL_COMPONENT_OF("http://spdx.org/rdf/terms#relationshipType_optionalComponentOf"),
    RELATIONSHIP_TYPE_OTHER("http://spdx.org/rdf/terms#relationshipType_other"),
    RELATIONSHIP_TYPE_PACKAGE_OF("http://spdx.org/rdf/terms#relationshipType_packageOf"),
    RELATIONSHIP_TYPE_PATCH_APPLIED("http://spdx.org/rdf/terms#relationshipType_patchApplied"),
    RELATIONSHIP_TYPE_PATCH_FOR("http://spdx.org/rdf/terms#relationshipType_patchFor"),
    RELATIONSHIP_TYPE_PREREQUISITE_FOR("http://spdx.org/rdf/terms#relationshipType_prerequisiteFor"),
    RELATIONSHIP_TYPE_STATIC_LINK("http://spdx.org/rdf/terms#relationshipType_staticLink"),
    RELATIONSHIP_TYPE_TESTCASE_OF("http://spdx.org/rdf/terms#relationshipType_testcaseOf"),
    RELATIONSHIP_TYPE_VARIANT_OF("http://spdx.org/rdf/terms#relationshipType_variantOf");

    private final String fullyQualifiedName;

    private SpdxValue(String fullyQualifiedName) {
        this.fullyQualifiedName = checkNotNull(fullyQualifiedName);
    }

    @Override
    public String id() {
        return fullyQualifiedName;
    }

    @Override
    public Set<Type> types() {
        return ImmutableSet.of();
    }

    @Override
    public Map<Term, Object> data() {
        return ImmutableMap.of();
    }
}
