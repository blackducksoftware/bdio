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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;

class Specification {

    // NOTE: The specifications should be preserved in the state that they were released. If you change a constant that
    // the specification definition uses, you must change the definition back to the old value.

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
            addTerm("checksumValue", SpdxTerm.CHECKSUM_VALUE);
            addTerm("checksum", SpdxTerm.CHECKSUM, SpdxType.CHECKSUM);
            addTerm("algorithm", SpdxTerm.ALGORITHM, JsonLdType.ID);
            addTerm("artifactOf", SpdxTerm.ARTIFACT_OF, JsonLdType.ID);
            addTerm("licenseConcluded", SpdxTerm.LICENSE_CONCLUDED, JsonLdType.ID);
            addTerm("creationInfo", SpdxTerm.CREATION_INFO, SpdxType.CREATION_INFO);

            addValue("BD-Hub", "http://blackducksoftware.com/rdf/terms#externalIdentifier_BD-Hub");
            addValue("BD-Suite", "http://blackducksoftware.com/rdf/terms#externalIdentifier_BD-Suite");
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
    }, new ImportResolver(), new ImportFrame() {
        {
            addReferenceTerm(DoapTerm.LICENSE);
            addReferenceTerm(SpdxTerm.ARTIFACT_OF);
            addReferenceTerm(SpdxTerm.LICENSE_CONCLUDED);
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
    }, new ImportResolver(), new ImportFrame(v0));

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
            // Added relationships
            addTerm("relationshipType", SpdxTerm.RELATIONSHIP_TYPE, JsonLdType.ID);
            addTerm("relationship", SpdxTerm.RELATIONSHIP, SpdxType.RELATIONSHIP);
            addTerm("related", SpdxTerm.RELATED_SPDX_ELEMENT, JsonLdType.ID);
            addValue("DYNAMIC_LINK", SpdxValue.RELATIONSHIP_TYPE_DYNAMIC_LINK);

            // Added external identifiers
            addValue("anaconda", BlackDuckValue.EXTERNAL_IDENTIFIER_ANACONDA);
            addValue("bower", BlackDuckValue.EXTERNAL_IDENTIFIER_BOWER);
            addValue("cpan", BlackDuckValue.EXTERNAL_IDENTIFIER_CPAN);
            addValue("goget", BlackDuckValue.EXTERNAL_IDENTIFIER_GOGET);
            addValue("maven", BlackDuckValue.EXTERNAL_IDENTIFIER_MAVEN);
            addValue("npm", BlackDuckValue.EXTERNAL_IDENTIFIER_NPM);
            addValue("nuget", BlackDuckValue.EXTERNAL_IDENTIFIER_NUGET);
            addValue("rubygems", BlackDuckValue.EXTERNAL_IDENTIFIER_RUBYGEMS);

            // Changed the external identifiers for Black Duck products
            remove("BD-Hub");
            addValue("bdhub", BlackDuckValue.EXTERNAL_IDENTIFIER_BDHUB);
            remove("BD-Suite");
            addValue("bdsuite", BlackDuckValue.EXTERNAL_IDENTIFIER_BDSUITE);

            // Fixed typo
            remove("licence");
            addTerm("license", DoapTerm.LICENSE, JsonLdType.ID);
        }
    }, new ImportResolver() {
        @Override
        public TermDefinition removed(String alias, TermDefinition oldDefinition) {
            switch (alias) {
            case "BD-Hub":
                return TermDefinition.forValue(BlackDuckValue.EXTERNAL_IDENTIFIER_BDHUB);
            case "BD-Suite":
                return TermDefinition.forValue(BlackDuckValue.EXTERNAL_IDENTIFIER_BDSUITE);
            case "licence":
                return new TermDefinition(DoapTerm.LICENSE, ImmutableSet.of(JsonLdType.ID), null);
            default:
                return v1_0_0.importResolver().removed(alias, oldDefinition);
            }
        }
    }, new ImportFrame(v1_0_0) {
        {
            // Frame references
            addReferenceTerm(SpdxTerm.RELATED_SPDX_ELEMENT);
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

        /**
         * Returns a term definition for a value node.
         */
        public static TermDefinition forValue(Node value) {
            return defaultDefinition(SimpleTerm.create(value.id()));
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
            put(alias, TermDefinition.forValue(value));
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
     * A definition that helps build a JSON-LD "frame" used for importing data. The framed data should look like it was
     * generated using the model classes.
     */
    public static class ImportFrame {
        // This amounts to types represented by subclasses of AbstractTopLevelModel
        private static final List<Type> TOP_LEVEL_TYPES = ImmutableList.<Type> builder()
                .add(BlackDuckType.BILL_OF_MATERIALS)
                .add(BlackDuckType.COMPONENT)
                .add(BlackDuckType.FILE)
                .add(BlackDuckType.LICENSE)
                .add(BlackDuckType.PROJECT)
                .add(BlackDuckType.VULNERABILITY)
                .build();

        // There is really only one type which can be a reference type
        private static final Set<Type> REFERENCE_TYPES = ImmutableSet.<Type> of(JsonLdType.ID);

        /**
         * Filter used to match the reference terms.
         */
        private final Predicate<TermDefinition> referenceTermsFilter;

        /**
         * Mutable state representing the terms that should be framed as references instead of embedded objects.
         */
        private final Set<Term> referenceTerms = new HashSet<>();

        ImportFrame() {
            // Default behavior is to not accept anything
            this(Predicates.alwaysFalse());
        }

        ImportFrame(Specification specification) {
            // Combine the reference terms from the parent specification with this import frame
            this(specification.importFrame.referenceTerms());
        }

        private ImportFrame(Predicate<? super TermDefinition> referenceTermsFilter) {
            // Combine the supplied reference terms with our mutable set of terms
            this.referenceTermsFilter = Predicates.and(
                    // Makes sure we are only looking at term definitions with a type of "@id"
                    // TODO This is getting evaluated at every depth
                    Predicates.compose(Predicates.equalTo(REFERENCE_TYPES), new Function<TermDefinition, Set<Type>>() {
                        @Override
                        public Set<Type> apply(TermDefinition termDefinition) {
                            return termDefinition.getTypes();
                        }
                    }),

                    // OR together any parent conditions plus our allowed reference terms
                    Predicates.or(
                            referenceTermsFilter,
                            Predicates.compose(Predicates.in(referenceTerms), new Function<TermDefinition, Term>() {
                                @Override
                                public Term apply(TermDefinition termDefinition) {
                                    return termDefinition.getTerm();
                                }
                            })));
        }

        /**
         * Adds a term which should be framed as a reference (as opposed to an embedded object).
         */
        protected void addReferenceTerm(Term term) {
            referenceTerms.add(term);
        }

        /**
         * Returns the top-level types which should appear in the graph.
         */
        public List<Type> topLevelTypes() {
            return TOP_LEVEL_TYPES;
        }

        /**
         * Returns a predicate that matches references terms.
         */
        public Predicate<TermDefinition> referenceTerms() {
            return referenceTermsFilter;
        }
    }

    /**
     * An import resolver is used to reconcile differences with older versions of the specification. When you are
     * importing older data (data generated to a previous version of the specification), the import resolver can be used
     * to produce an alternate set of term definitions to ensure the old data is properly migrated to the current
     * version of the specification.
     * <p>
     * Technically only the latest version of the specification needs an import resolver, though it may be useful to
     * build off of the resolvers from a previous version.
     */
    public static class ImportResolver {

        /**
         * When the IRI mapped to an alias is changed, this method is used to determine which term definition should be
         * used during import. Typically you want the new definition to be used because the IRI from the latest
         * definition is used in model classes. For example, the alias "foo" might have been defined as
         * "http://example.com/foo" in a previous version of the specification; the current version defines "foo" as
         * "http://example.com/terms#foo" and that is the value used by the model classes when converting between nodes
         * and beans. The old data will contain values for "foo", and even though it was meant to be for
         * "http://example.com/foo", you want to select the new definition ("http://example.com/terms#foo") so the model
         * code will work as expected.
         * <p>
         * The default behavior is to always return the new definition.
         */
        public TermDefinition changed(String alias, TermDefinition oldDefinition, TermDefinition newDefinition) {
            return newDefinition;
        }

        /**
         * When an alias from a previous version no longer exists, either because it was removed or renamed, this method
         * is used to determine what the appropriate term definition should be during import. If the alias was dropped
         * completely this method should typically just return the old definition: you can avoid duck typing failures by
         * preserving the type information this way. If the alias was renamed, the resulting definition should point to
         * the new IRI (hopefully with the same type information).
         */
        public TermDefinition removed(String alias, TermDefinition oldDefinition) {
            return oldDefinition;
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

    /**
     * The import resolver used to adjust conflicts with older versions of the specification.
     */
    private final ImportResolver importResolver;

    /**
     * The information about how to frame the data for import.
     */
    private final ImportFrame importFrame;

    private Specification(String version, Map<String, TermDefinition> definitions, ImportResolver importResolver, ImportFrame importFrame) {
        this.version = checkNotNull(version);
        this.definitions = ImmutableMap.copyOf(definitions);
        this.importResolver = checkNotNull(importResolver);
        this.importFrame = checkNotNull(importFrame);
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
     * Returns the import resolver for this version of the specification.
     */
    private ImportResolver importResolver() {
        return importResolver;
    }

    /**
     * Returns a modified version of this specification whose term definitions can be used to map an older version of
     * the specification into a newer version.
     */
    public Map<String, TermDefinition> importDefinitions() {
        MapDifference<String, TermDefinition> diff = Maps.difference(asTermDefinitions(), latest().asTermDefinitions());
        if (diff.areEqual()) {
            return asTermDefinitions();
        } else {
            // Use the resolver from the latest version of the specification to generate term definitions
            ImportResolver resolver = latest().importResolver();
            Map<String, TermDefinition> termDefinitions = new LinkedHashMap<>();

            // Entries on the right did not exist when the current version of the specification was
            // released, therefore we can ignore them in our reconstructed definitions

            // All common entries are unchanged between both versions of the specification
            termDefinitions.putAll(diff.entriesInCommon());

            // Entries which have the same alias but different IRIs have been "re-mapped"; typically
            // (but not always) we want the alias to point to the latest IRI
            for (Entry<String, ValueDifference<TermDefinition>> e : diff.entriesDiffering().entrySet()) {
                String alias = e.getKey();
                termDefinitions.put(alias, resolver.changed(alias, e.getValue().leftValue(), e.getValue().rightValue()));
            }

            // Entries on the left have been removed or renamed (their alias changed). We must delegate
            // to the import resolver to decide what do here.
            for (Entry<String, TermDefinition> e : diff.entriesOnlyOnLeft().entrySet()) {
                String alias = e.getKey();
                termDefinitions.put(alias, resolver.removed(alias, e.getValue()));
            }

            return termDefinitions;
        }
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

    /**
     * Returns a JSON-LD frame for reconstructing the data as if it had been generated by serializing a list model
     * objects.
     */
    public final Map<String, Object> importFrame() {
        final Map<String, Object> frame = new LinkedHashMap<>();

        // Start by keeping only the top-level types
        frame.put("@type", Lists.transform(importFrame.topLevelTypes(), Functions.toStringFunction()));

        // Frame definition to turn of embedding
        final Map<String, Object> embedOff = ImmutableMap.of("@embed", (Object) Boolean.FALSE, "@omitDefault", (Object) Boolean.TRUE);

        // Add the term filters to disable embedding
        for (TermDefinition termDefinition : Iterables.filter(definitions.values(), importFrame.referenceTerms())) {
            frame.put(termDefinition.getTerm().toString(), embedOff);
        }

        return frame;
    }
}
