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

import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.BlackDuckValue;
import com.blackducksoftware.bom.DoapTerm;
import com.blackducksoftware.bom.ImmutableNode;
import com.blackducksoftware.bom.Node;
import com.blackducksoftware.bom.SimpleTerm;
import com.blackducksoftware.bom.SimpleType;
import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.SpdxType;
import com.blackducksoftware.bom.SpdxValue;
import com.blackducksoftware.bom.Term;
import com.blackducksoftware.bom.Type;
import com.blackducksoftware.bom.XmlSchemaType;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * The linked data context defines how linked data should be serialized and deserialized.
 *
 * @author jgustie
 */
public class LinkedDataContext {

    /**
     * Matcher on the prefix/scheme delimiter.
     */
    private static final CharMatcher PREFIX_DELIMITER = CharMatcher.is(':');

    /**
     * Default base to use when {@code null} just isn't an option.
     */
    private static final URI DEFAULT_BASE = URI.create("http://example.com/");

    /**
     * Possible container types.
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
            if (this == SET) {
                // We need to return lists instead of sets, but we want set semantics
                return ImmutableList.copyOf(ImmutableSet.copyOf(source));
            } else {
                return ImmutableList.copyOf(source);
            }
        }
    }

    /**
     * A definition for a particular term.
     */
    private static class TermDefinition {
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
     * The base IRI to resolve identifiers against. Stored as a typed URI to simplify access to the resolution logic.
     */
    @Nullable
    private final URI base;

    /**
     * The vocabulary IRI to prepend to unqualified IRIs.
     */
    @Nullable
    private final String vocab;

    /**
     * Mapping of term names to definitions.
     */
    private final Map<String, TermDefinition> definitions = new LinkedHashMap<>();

    /**
     * A loading cache from typed term instances to their definitions.
     */
    private final LoadingCache<Term, TermDefinition> termDefinitions = CacheBuilder.newBuilder().build(new CacheLoader<Term, TermDefinition>() {
        @Override
        public TermDefinition load(Term term) {
            // Lookup the term definition or return a new one
            for (TermDefinition definition : definitions.values()) {
                if (term.equals(definition.getTerm())) {
                    return definition;
                }
            }
            return new TermDefinition(term, ImmutableSet.<Type> of(), null);
        }
    });

    /**
     * A function to perform expansion on a node. Gee it would be nice to have lambdas.
     */
    private final Function<Node, Map<String, Object>> expander = new Function<Node, Map<String, Object>>() {
        @Override
        public Map<String, Object> apply(Node node) {
            return expand(node);
        }
    };

    public LinkedDataContext() {
        this(null);
    }

    public LinkedDataContext(@Nullable String base) {
        // Store the base URI used to resolve identifiers
        this.base = base != null ? URI.create(base) : null;
        checkArgument(base == null || this.base.isAbsolute(), "base must be an absolute URI");

        // Load our definitions
        vocab = "http://blackducksoftware.com/rdf/terms#";
        addPrefixMapping("spdx", "http://spdx.org/rdf/terms#");
        addPrefixMapping("doap", "http://usefulinc.com/ns/doap#");
        addPrefixMapping("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        addPrefixMapping("xsd", "http://www.w3.org/2001/XMLSchema#");

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

        // Hard code some dummy definitions for the keywords
        termDefinitions.put(JsonLdTerm.ID, new TermDefinition(JsonLdTerm.ID, ImmutableSet.of(JsonLdType.ID), null));
        termDefinitions.put(JsonLdTerm.TYPE, new TermDefinition(JsonLdTerm.TYPE, ImmutableSet.of(JsonLdType.ID), Container.SET));
    }

    /**
     * Adds a prefix mapping to this context.
     */
    private void addPrefixMapping(String prefix, String identifier) {
        Term term = SimpleTerm.create(expandIri(identifier, false));
        definitions.put(prefix, new TermDefinition(term, ImmutableSet.<Type> of(), null));
    }

    /**
     * Adds a scalar term definition to this context.
     */
    private void addTerm(String alias, Term term) {
        addTerm(alias, term, null);
    }

    /**
     * Adds a scalar term definition to this context.
     */
    private void addTerm(String alias, Term term, @Nullable Type type) {
        addTerm(alias, term, type, null);
    }

    /**
     * Adds a scalar term definition to this context.
     */
    private void addTerm(String alias, Term term, @Nullable Type type, @Nullable Container container) {
        definitions.put(alias, new TermDefinition(term, Optional.fromNullable(type).asSet(), container));
    }

    /**
     * Adds an identifier value to this context.
     */
    private void addValue(String alias, Node value) {
        Term term = SimpleTerm.create(expandIri(value.id(), false));
        definitions.put(alias, new TermDefinition(term, ImmutableSet.<Type> of(), null));
    }

    /**
     * This is a keyword safe version of the {@code SimpleTerm.create}.
     */
    private static Term term(String iri) {
        if (iri.startsWith("@")) {
            for (JsonLdTerm keyword : JsonLdTerm.values()) {
                if (iri.equals(keyword.toString())) {
                    return keyword;
                }
            }
            throw new IllegalArgumentException("unknown keyword: " + iri);
        } else {
            return SimpleTerm.create(iri);
        }
    }

    @Nullable
    public URI getBase() {
        return base;
    }

    /**
     * Expands an entire graph.
     */
    public Map<String, Object> expandToGraph(String graphLabel, Iterable<Node> nodes) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put(JsonLdTerm.ID.toString(), graphLabel);

        // The JSON-LD library is picky about getting lists
        List<Map<String, Object>> graphNodes;
        if (nodes instanceof List<?>) {
            graphNodes = Lists.transform((List<Node>) nodes, expander);
        } else {
            graphNodes = FluentIterable.from(nodes).transform(expander).toList();
        }
        graph.put(JsonLdTerm.GRAPH.toString(), graphNodes);
        return graph;
    }

    /**
     * Alternate version of expand that produces a node from a map.
     */
    public Node expandToNode(Map<String, Object> nodeMap) {
        ImmutableNode.Builder result = ImmutableNode.builder();
        Object id = nodeMap.get("@id");
        if (id != null) {
            result.id(expandIri(id.toString(), true));
        }
        Object type = nodeMap.get("@type");
        if (type instanceof Iterable<?>) {
            for (Object obj : (Iterable<?>) type) {
                result.addType(SimpleType.create(expandIri(obj.toString(), false)));
            }
        } else if (type != null) {
            result.addType(SimpleType.create(expandIri(type.toString(), false)));
        }
        for (Entry<String, Object> entry : nodeMap.entrySet()) {
            Term term = term(expandIri(entry.getKey(), false));
            if (term != JsonLdTerm.ID && term != JsonLdTerm.TYPE) {
                TermDefinition definition = termDefinitions.getUnchecked(term);
                Object value = expandValue(definition, entry.getValue());
                if (value != null) {
                    result.put(term, value);
                }
            }
        }
        return result.build();
    }

    /**
     * Expand a node such that all known identifiers are fully expanded.
     */
    public Map<String, Object> expand(Node node) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (node.id() != null) {
            // Expand the identifier against the base IRI
            result.put(JsonLdTerm.ID.toString(), expandIri(node.id(), true));
        }
        if (!node.types().isEmpty()) {
            // Type instances are already fully qualified IRI, no need to expand
            result.put(JsonLdTerm.TYPE.toString(), FluentIterable.from(node.types()).transform(Functions.toStringFunction()).toList());
        }
        for (Entry<Term, Object> entry : node.data().entrySet()) {
            // Expand the value, term instances are already fully qualified
            TermDefinition definition = termDefinitions.getUnchecked(entry.getKey());
            Object value = expandValue(definition, entry.getValue());
            if (value != null) {
                result.put(entry.getKey().toString(), value);
            }
        }
        return result;
    }

    /**
     * Expand a single value given a specific definition.
     */
    @Nullable
    private Object expandValue(TermDefinition definition, @Nullable Object value) {
        if (value instanceof Iterable<?>) {
            // Accumulate expanded values into the appropriate collection
            Collection<Object> values = new LinkedList<>();
            for (Object obj : (Iterable<?>) value) {
                values.add(expandValue(definition, obj));
            }
            return definition.getContainer().copyOf(values);
        } else if (value instanceof Node) {
            // Expand the node and merge the types from the definition
            // TODO Isn't this dead code?
            Map<String, Object> embeddedNode = expand((Node) value);
            embeddedNode.put(JsonLdTerm.TYPE.toString(),
                    FluentIterable.from(Iterables.concat(definition.getTypes(), ((Node) value).types()))
                            .transform(Functions.toStringFunction()).toList());
            return embeddedNode;
        } else if (value instanceof Map<?, ?>) {
            // TODO Is there a better way to do this?
            Map<String, Object> embeddedNode = expand(expandToNode((Map<String, Object>) value));
            Iterable<String> definitionTypes = FluentIterable.from(definition.getTypes()).transform(Functions.toStringFunction()).toList();
            if (embeddedNode.containsKey(JsonLdTerm.TYPE.toString())) {
                embeddedNode.put(JsonLdTerm.TYPE.toString(),
                        FluentIterable.from(Iterables.concat(definitionTypes, (Iterable<String>) embeddedNode.get(JsonLdTerm.TYPE.toString()))).toList());
            } else {
                embeddedNode.put(JsonLdTerm.TYPE.toString(), definitionTypes);
            }
            return embeddedNode;
        } else if (value != null) {
            // Scalar, convert based on type
            return convertExpand(value, definition.getTypes());
        }
        return null;
    }

    /**
     * Scalar value conversion that expands IRIs.
     */
    private Object convertExpand(Object value, Set<Type> types) {
        if (types.contains(JsonLdType.ID)) {
            return expandIri(value.toString(), false);
        } else {
            return convert(value, types);
        }
    }

    /**
     * Expand an identifier.
     */
    @Nullable
    private String expandIri(@Nullable String value, boolean relative) {
        if (value == null || value.startsWith("@")) {
            return value;
        }
        TermDefinition definition = definitions.get(value);
        if (definition != null) {
            return definition.getTerm().toString();
        }
        int pos = PREFIX_DELIMITER.indexIn(value);
        if (pos > 0) {
            TermDefinition prefixDefinition = definitions.get(value.substring(0, pos));
            if (prefixDefinition != null) {
                return prefixDefinition.getTerm() + value.substring(pos + 1);
            } else {
                return value;
            }
        }
        if (relative) {
            return base != null ? base.resolve(value).toString() : value;
        } else {
            return vocab != null ? vocab + value : value;
        }
    }

    /**
     * Compact a node such that all identifiers are compacted.
     */
    public Map<String, Object> compact(Node node) {
        return compactNode(null, expand(node));
    }

    /**
     * Unlike expand, we need an intermediate "compact" operation to allow for recursion and handling of input from the
     * expansion operation. Embedded nodes should have an associated term definition, the top level term definition can
     * be omitted.
     */
    private Map<String, Object> compactNode(@Nullable TermDefinition definition, Map<String, Object> expanded) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Entry<String, Object> entry : expanded.entrySet()) {
            Term term = term(entry.getKey());
            Object value = entry.getValue();
            if (term == JsonLdTerm.TYPE && definition != null) {
                // Omit types specified by the definition
                Set<String> declaredTypes = ImmutableSet.copyOf((Iterable<String>) entry.getValue());
                Set<String> definedTypes = FluentIterable.from(definition.getTypes()).transform(Functions.toStringFunction()).toSet();
                value = ImmutableList.copyOf(Sets.difference(declaredTypes, definedTypes));
            }

            value = compactValue(termDefinitions.getUnchecked(term), value);
            if (value != null) {
                // Compact the key as well
                result.put(compactIri(term.toString()), value);
            }
        }
        return result;
    }

    /**
     * Compact a single value given a specific definition.
     */
    @Nullable
    private Object compactValue(TermDefinition definition, @Nullable Object value) {
        if (value instanceof Iterable<?>) {
            // Accumulate compacted values into the appropriate collection
            Collection<Object> values = new LinkedList<>();
            for (Object obj : (Iterable<?>) value) {
                values.add(compactValue(definition, obj));
            }

            // Omit collection dingus if it isn't necessary
            if (values.isEmpty()) {
                return null;
            } else if (values.size() == 1) {
                return Iterables.getOnlyElement(values);
            } else {
                return definition.getContainer().copyOf(values);
            }
        } else if (value instanceof Node) {
            // All node instances should have been expanded to maps by now
            throw new IllegalStateException("Compact did go through expand first");
        } else if (value instanceof Map<?, ?>) {
            // Recurse to map compaction method
            return compactNode(definition, (Map<String, Object>) value);
        } else if (value != null) {
            // Scalar, convert based on type
            return convertCompact(value, definition.getTypes());
        }
        return null;
    }

    /**
     * Scalar value conversion that compacts IRIs.
     */
    private Object convertCompact(Object value, Set<Type> types) {
        if (types.contains(JsonLdType.ID)) {
            return compactIri(value.toString());
        } else {
            return convert(value, types);
        }
    }

    /**
     * Compact an identifier.
     */
    @Nullable
    private String compactIri(@Nullable String value) {
        return compactIri(value, definitions);
    }

    /**
     * Compact an identifier with a subset of the actual definitions.
     */
    @Nullable
    private String compactIri(@Nullable String value, Map<String, TermDefinition> definitions) {
        if (value == null || value.startsWith("@")) {
            return value;
        }
        String prefix = null;
        int prefixMatchLen = -1;
        for (Entry<String, TermDefinition> definition : definitions.entrySet()) {
            String id = definition.getValue().getTerm().toString();
            if (value.equals(id)) {
                return definition.getKey();
            } else if (value.startsWith(id) && id.length() > prefixMatchLen) {
                prefix = definition.getKey();
                prefixMatchLen = id.length();
            }
        }
        if (prefix != null) {
            return prefix + ":" + value.substring(prefixMatchLen);
        }
        return vocab != null && value.startsWith(vocab) ? value.substring(vocab.length()) : value;
    }

    /**
     * Convert a value to a scalar value.
     */
    private Object convert(Object value, Set<Type> types) {
        if (types.contains(XmlSchemaType.LONG)) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return value.toString();
            }
        }

        // TODO Other types?

        return value.toString();
    }

    /**
     * Generates a serialized version of the context.
     */
    public Map<String, Object> serialize() {
        final Map<String, Object> context = new LinkedHashMap<>();
        final Map<String, TermDefinition> localContext = new LinkedHashMap<>();
        if (base != null) {
            context.put("@base", base.toString());
        }
        if (vocab != null) {
            context.put("@vocab", vocab);
        }
        for (Entry<String, TermDefinition> entry : definitions.entrySet()) {
            TermDefinition definition = entry.getValue();
            Object serializedDefinition = compactIri(definition.getTerm().toString(), localContext);
            if (!definition.getTypes().isEmpty() || definition.getContainer() != Container.UNKNOWN) {
                Map<String, Object> definitionMap = new LinkedHashMap<>(3);
                if (!entry.getKey().equals(serializedDefinition)) {
                    definitionMap.put("@id", serializedDefinition);
                }
                if (definition.getTypes().size() == 1) {
                    definitionMap.put("@type", compactIri(Iterables.getOnlyElement(definition.getTypes()).toString(), localContext));
                } else if (!definition.getTypes().isEmpty()) {
                    definitionMap.put("@type", FluentIterable.from(definition.getTypes())
                            .transform(Functions.toStringFunction())
                            .transform(new Function<String, String>() {
                                @Override
                                public String apply(String input) {
                                    return compactIri(input, localContext);
                                }
                            }).toList());
                }
                if (definition.getContainer() != Container.UNKNOWN) {
                    definitionMap.put("@container", "@" + UPPER_UNDERSCORE.to(LOWER_HYPHEN, definition.getContainer().toString()));
                }
                serializedDefinition = definitionMap;
            }
            context.put(entry.getKey(), serializedDefinition);
            localContext.put(entry.getKey(), entry.getValue());
        }
        return ImmutableMap.<String, Object> of("@context", context);
    }

    /**
     * Creates a new globally unique identifier.
     */
    public URI mintSkolemIdentifier() {
        // http://www.w3.org/TR/rdf11-concepts/#section-skolemization
        // http://manu.sporny.org/2013/rdf-identifiers/
        UUID name = UUID.randomUUID();
        BigInteger number = BigInteger.valueOf(name.getMostSignificantBits()).shiftLeft(64).and(BigInteger.valueOf(name.getLeastSignificantBits()));
        return firstNonNull(getBase(), DEFAULT_BASE).resolve("/.well-known/genid/" + number);
    }

    /**
     * Checks to see if an identifier is one of the minted skolem identifiers used to replace a blank node.
     */
    public static boolean isSkolemIdentifier(URI identifier) {
        return !identifier.isOpaque() && identifier.getPath().startsWith("/.well-known/genid/");
    }

}
