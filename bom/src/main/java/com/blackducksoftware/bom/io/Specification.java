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
package com.blackducksoftware.bom.io;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.BlackDuckValue;
import com.blackducksoftware.bom.DoapTerm;
import com.blackducksoftware.bom.Node;
import com.blackducksoftware.bom.SimpleTerm;
import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxType;
import com.blackducksoftware.bom.SpdxValue;
import com.blackducksoftware.bom.Term;
import com.blackducksoftware.bom.Type;
import com.blackducksoftware.bom.XmlSchemaType;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

class Specification {

    /**
     * The initial version of the specification. Uses an empty string as the version number to indicate that there was
     * no specification version data stored in the initial version.
     */
    private static final Specification v0 = new Specification("", new TermDefinitionMap() {
        {
            addPrefixMapping("spdx", "http://spdx.org/rdf/terms#");
            addPrefixMapping("doap", "http://usefulinc.com/ns/doap#");
            addPrefixMapping("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
            addPrefixMapping("xsd", "http://www.w3.org/2001/XMLSchema#");

            // Because the vocabulary includes BlackDuckTerms, only type overrides need to be specified
            addTerm("size", BlackDuckTerm.SIZE, XmlSchemaType.LONG);
            addTerm("externalIdentifier", BlackDuckTerm.EXTERNAL_IDENTIFIER, BlackDuckType.EXTERNAL_IDENTIFIER);
            addTerm("externalSystemTypeId", BlackDuckTerm.EXTERNAL_SYSTEM_TYPE_ID, JsonLdType.ID);
            addTerm("matchDetail", BlackDuckTerm.MATCH_DETAIL, BlackDuckType.MATCH_DETAIL);
            addTerm("matchType", BlackDuckTerm.MATCH_TYPE, JsonLdType.ID);

            addTerm("name", DoapTerm.NAME);
            addTerm("homepage", DoapTerm.HOMEPAGE);
            addTerm("revision", DoapTerm.REVISION);
            addTerm("licence", DoapTerm.LICENSE, JsonLdType.ID);

            addTerm("fileName", SpdxTerm.FILE_NAME);
            addTerm("fileType", SpdxTerm.FILE_TYPE, JsonLdType.ID, Container.SET);
            addTerm("checksum", SpdxTerm.CHECKSUM, SpdxType.CHECKSUM);
            addTerm("checksumValue", SpdxTerm.CHECKSUM_VALUE);
            addTerm("algorithm", SpdxTerm.ALGORITHM, JsonLdType.ID);
            addTerm("artifactOf", SpdxTerm.ARTIFACT_OF, JsonLdType.ID);
            addTerm("licenseConcluded", SpdxTerm.LICENSE_CONCLUDED, JsonLdType.ID);
            addTerm("creationInfo", SpdxTerm.CREATION_INFO, SpdxType.CREATION_INFO);

            addValue("BD-Hub", BlackDuckValue.EXTERNAL_IDENTIFIER_BD_HUB);
            addValue("BD-Suite", BlackDuckValue.EXTERNAL_IDENTIFIER_BD_SUITE);
            addValue("DEPENDENCY", BlackDuckValue.MATCH_TYPE_DEPENDENCY);
            addValue("PARTIAL", BlackDuckValue.MATCH_TYPE_PARTIAL);
            addValue("DIRECTORY", BlackDuckValue.FILE_TYPE_DIRECTORY);
            addValue("ARCHIVE", SpdxValue.FILE_TYPE_ARCHIVE);
            addValue("BINARY", SpdxValue.FILE_TYPE_BINARY);
            addValue("OTHER", SpdxValue.FILE_TYPE_OTHER);
            addValue("SOURCE", SpdxValue.FILE_TYPE_SOURCE);
            addValue("sha1", SpdxValue.CHECKSUM_ALGORITHM_SHA1);
            addValue("md5", SpdxValue.CHECKSUM_ALGORITHM_MD5);
        }
    });

    /**
     * Version 1.0.0 of the specification.
     * <p>
     * <b>Change log</b>
     * <ul>
     * <li>Added {@linkplain BlackDuckTerm#SPEC_VERSION specVersion}</li>
     * </ul>
     */
    private static final Specification v1_0_0 = new Specification("1.0.0", new TermDefinitionMap(v0) {
        {
            // There were no changes to the context in this version of the specification, it basically is just a stop
            // gap to address the fact we released the initial version before formally defining a mechanism to record
            // changes in the specification.
        }
    });

    /**
     * Version 1.1.0 of the specification.
     * <p>
     * <b>Change log</b>
     * <ul>
     * <li>Added {@linkplain com.blackducksoftware.bom.model.Relationship relationships}</li>
     * <li>Added external identifier system type aliases</li>
     * <li>Changed the external identifier system type mapping for Black Duck products</li>
     * </ul>
     */
    private static final Specification v1_1_0 = new Specification("1.1.0", new TermDefinitionMap(v1_0_0) {
        {
        }
    });

    /**
     * Possible container types for term values.
     */
    public enum Container {
        /**
         * Placeholder to use instead of {@code null}.
         */
        UNKNOWN,

        // From the JSON-LD spec...
        LIST, SET, LANGUAGE, INDEX;

        /**
         * Create an immutable copy of the supplied input appropriate for this container type.
         */
        public <T> Iterable<T> copyOf(Iterable<T> source) {
            Iterable<T> elements = source;
            if (this == SET) {
                elements = ImmutableSet.copyOf(elements);
            }

            // The result always needs to be a List or the JSON-LD library will choke
            return ImmutableList.copyOf(elements);
        }
    }

    /**
     * A definition for a particular term.
     */
    public static final class TermDefinition {

        /**
         * A special term definition used for the JSON-LD "@id" keyword.
         */
        public static TermDefinition JSON_LD_ID = new TermDefinition(JsonLdTerm.ID, ImmutableSet.of(JsonLdType.ID), null);

        /**
         * A special term definition used for the JSON-LD "@type" keyword.
         */
        public static TermDefinition JSON_LD_TYPE = new TermDefinition(JsonLdTerm.TYPE, ImmutableSet.of(JsonLdType.ID), Container.SET);

        /**
         * The fully qualified term identifier.
         */
        private final Term term;

        /**
         * The types associated with this term.
         */
        private final Set<Type> types;

        /**
         * The container type associated with this term.
         */
        private final Container container;

        private TermDefinition(Term term, Set<? extends Type> types, @Nullable Container container) {
            this.term = checkNotNull(term);
            this.types = ImmutableSet.copyOf(types);
            this.container = firstNonNull(container, Container.UNKNOWN);
        }

        /**
         * Returns a simple term definition with no types and no container type.
         */
        public static TermDefinition defaultDefinition(Term term) {
            return new TermDefinition(term, ImmutableSet.<Type> of(), null);
        }

        public Term getTerm() {
            return term;
        }

        public Set<Type> getTypes() {
            return types;
        }

        public Container getContainer() {
            return container;
        }
    }

    /**
     * A specialized map used to help building term definitions.
     */
    private static class TermDefinitionMap extends LinkedHashMap<String, TermDefinition> {
        TermDefinitionMap() {
        }

        TermDefinitionMap(Specification specification) {
            putAll(specification.definitions);
        }

        protected void addPrefixMapping(String prefix, String identifier) {
            put(prefix, TermDefinition.defaultDefinition(SimpleTerm.create(identifier)));
        }

        protected void addValue(String alias, Node value) {
            addValue(alias, value.id());
        }

        protected void addValue(String alias, String value) {
            put(alias, TermDefinition.defaultDefinition(SimpleTerm.create(value)));
        }

        protected void addTerm(String alias, Term term, @Nullable Type type, @Nullable Container container) {
            put(alias, new TermDefinition(term, Optional.fromNullable(type).asSet(), container));
        }

        protected void addTerm(String alias, Term term) {
            addTerm(alias, term, null);
        }

        protected void addTerm(String alias, Term term, @Nullable Type type) {
            addTerm(alias, term, type, null);
        }
    }

    /**
     * The map of available specification versions.
     */
    private static Map<String, Specification> VERSIONS = Maps.uniqueIndex(
            // The ordering here is important for the latest() call to work
            ImmutableList.of(v0, v1_0_0, v1_1_0),

            // Gee, it sure would be nice to have a lambda here
            new Function<Specification, String>() {
                @Override
                public String apply(Specification spec) {
                    return spec.version();
                }
            });

    /**
     * Returns the specification for a version number. If the specification version number is not recognized or is
     * {@code null}, the initial specification is returned.
     */
    public static Specification forVersion(@Nullable String specVersion) {
        return firstNonNull(VERSIONS.get(nullToEmpty(specVersion)), VERSIONS.get(""));
    }

    /**
     * Returns the latest version of the specification.
     */
    public static Specification latest() {
        return Iterables.getLast(VERSIONS.values());
    }

    /**
     * The version of this specification.
     */
    private final String version;

    /**
     * The vocabulary for this specification.
     */
    // Don't pretend like you think you can change this...
    private final String vocab = "http://blackducksoftware.com/rdf/terms#";

    /**
     * The term definitions for this specification.
     */
    private final Map<String, TermDefinition> definitions;

    private Specification(String version, Map<String, TermDefinition> definitions) {
        this.version = checkNotNull(version);
        this.definitions = ImmutableMap.copyOf(definitions);
    }

    /**
     * Returns the version number of this specification.
     */
    public final String version() {
        return version;
    }

    /**
     * Returns the vocabulary for this version of the specification.
     */
    public final String vocab() {
        return vocab;
    }

    /**
     * Returns all of the term definition aliases in this specification.
     */
    public final Iterable<String> aliases() {
        return definitions.keySet();
    }

    /**
     * Returns the term definition for an alias;
     */
    public final Optional<TermDefinition> get(String alias) {
        return Optional.fromNullable(definitions.get(alias));
    }

    /**
     * Returns the term definition for a term.
     */
    public final Optional<TermDefinition> get(Term term) {
        for (TermDefinition definition : definitions.values()) {
            if (definition.getTerm().equals(term)) {
                return Optional.of(definition);
            }
        }
        return Optional.absent();
    }

    /**
     * Returns all of the alias to term definition mappings.
     */
    final Map<String, TermDefinition> asTermDefinitions() {
        // Definitions is immutable
        return definitions;
    }

}
