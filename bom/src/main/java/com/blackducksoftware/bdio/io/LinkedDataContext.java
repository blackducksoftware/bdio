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
package com.blackducksoftware.bdio.io;

import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio.ImmutableNode;
import com.blackducksoftware.bdio.Node;
import com.blackducksoftware.bdio.SimpleTerm;
import com.blackducksoftware.bdio.SimpleType;
import com.blackducksoftware.bdio.Term;
import com.blackducksoftware.bdio.Type;
import com.blackducksoftware.bdio.XmlSchemaType;
import com.blackducksoftware.bdio.io.Specification.Container;
import com.blackducksoftware.bdio.io.Specification.TermDefinition;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
     * The current version of the specification this context is associated with.
     */
    private final String specVersion;

    /**
     * Mapping of term names to definitions.
     */
    private final Map<String, TermDefinition> definitions;

    /**
     * A loading cache from typed term instances to their definitions. This is really just a lazy map of all the
     * definitions in the specification plus any term we have encountered.
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
            return TermDefinition.defaultDefinition(term);
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
        this(base, Specification.latest());
    }

    private LinkedDataContext(@Nullable String base, Specification spec) {
        this(base != null ? URI.create(base) : null, spec.vocab(), spec.version(), spec.asTermDefinitions());
    }

    private LinkedDataContext(@Nullable URI base, @Nullable String vocab, String specVersion, Map<String, TermDefinition> definitions) {
        checkArgument(base == null || base.isAbsolute(), "base must be an absolute URI");
        this.base = base;
        this.vocab = vocab;
        this.specVersion = checkNotNull(specVersion);
        this.definitions = ImmutableMap.copyOf(definitions);

        // Hard code some dummy definitions for the keywords
        termDefinitions.put(JsonLdTerm.ID, TermDefinition.JSON_LD_ID);
        termDefinitions.put(JsonLdTerm.TYPE, TermDefinition.JSON_LD_TYPE);
    }

    /**
     * Creates a new linked data context which can be used to read an alternate version of the specification. Note that
     * you should only use this for reading: while it will map old values into the new values, it cannot perform the
     * reverse operation (producing the old values from the new values).
     */
    public LinkedDataContext newContextForReading(@Nullable String specVersion) {
        Specification spec = Specification.forVersion(specVersion);
        return new LinkedDataContext(getBase(), spec.vocab(), spec.version(), spec.importDefinitions());
    }

    /**
     * Returns the import frame.
     */
    public Map<String, Object> newImportFrame() {
        Specification spec = Specification.forVersion(specVersion);
        return spec.importFrame();
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

    /**
     * Returns the base IRI used to resolve relative references.
     */
    @Nullable
    public URI getBase() {
        return base;
    }

    /**
     * The vocabulary IRI to prepend to unqualified IRIs.
     */
    @Nullable
    public String getVocab() {
        return vocab;
    }

    /**
     * Returns the specification version used by this context.
     */
    public String getSpecVersion() {
        return specVersion;
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
    public Node expandToNode(Map<?, ?> nodeMap) {
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
        for (Entry<?, ?> entry : nodeMap.entrySet()) {
            Term term = term(expandIri(entry.getKey().toString(), false));
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
        // Maps.newLinkedHashMapWithExpectedSize(int) wasn't introduced until Guava 19.0
        Map<String, Object> result = new LinkedHashMap<>(((2 + node.data().size()) * 2) / 3);
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
            Map<String, Object> embeddedNode = expand(expandToNode((Map<?, ?>) value));
            Iterable<String> definitionTypes = FluentIterable.from(definition.getTypes()).transform(Functions.toStringFunction()).toList();
            if (embeddedNode.containsKey(JsonLdTerm.TYPE.toString())) {
                embeddedNode.put(JsonLdTerm.TYPE.toString(), ImmutableList.builder()
                        .addAll(definitionTypes)
                        .addAll(FluentIterable.from((Iterable<?>) embeddedNode.get(JsonLdTerm.TYPE.toString())).transform(Functions.toStringFunction()))
                        .build());
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
            return getBase() != null ? getBase().resolve(value).toString() : value;
        } else {
            return getVocab() != null ? getVocab() + value : value;
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
    private Map<String, Object> compactNode(@Nullable TermDefinition definition, Map<?, ?> expanded) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Entry<?, ?> entry : expanded.entrySet()) {
            Term term = term(entry.getKey().toString());
            Object value = entry.getValue();
            if (term == JsonLdTerm.TYPE && definition != null) {
                // Omit types specified by the definition
                Set<String> declaredTypes = FluentIterable.from((Iterable<?>) entry.getValue()).transform(Functions.toStringFunction()).toSet();
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
            return compactNode(definition, (Map<?, ?>) value);
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
        // Use all of the term definitions from the specification
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
        return getVocab() != null && value.startsWith(getVocab()) ? value.substring(getVocab().length()) : value;
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
        if (getBase() != null) {
            context.put("@base", getBase().toString());
        }
        if (getVocab() != null) {
            context.put("@vocab", getVocab());
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
            localContext.put(entry.getKey(), definition);
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
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(name.getMostSignificantBits());
        bb.putLong(name.getLeastSignificantBits());
        return firstNonNull(getBase(), DEFAULT_BASE).resolve("/.well-known/genid/" + new BigInteger(bb.array()));
    }

    /**
     * Checks to see if an identifier is one of the minted skolem identifiers used to replace a blank node.
     */
    public static boolean isSkolemIdentifier(URI identifier) {
        return !identifier.isOpaque() && identifier.getPath().startsWith("/.well-known/genid/");
    }

}
