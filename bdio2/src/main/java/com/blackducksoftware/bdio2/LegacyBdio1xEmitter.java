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
package com.blackducksoftware.bdio2;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.datatype.Fingerprint;
import com.blackducksoftware.bdio2.datatype.Products;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.model.Project;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

/**
 * An adaptor to convert BDIO 1.x data into 2.x data. The main reason this isn't done strictly with a JSON-LD context
 * (e.g. compress using the BDIO 1.x context, then expand with a special context that produces 2.x properties) is the
 * switch from the {@code BillOfMaterials} node to using graph metadata. The other reason is that we have seen 600MB
 * BDIO 1.x files and we don't necessarily want to load them using the full JSON-LD APIs.
 *
 * @author jgustie
 */
class LegacyBdio1xEmitter extends SpliteratorEmitter {

    /**
     * The BDIO 1.x vocabulary. This is the default prefix taken away from term names.
     */
    private static final String VOCAB = "http://blackducksoftware.com/rdf/terms#";

    /**
     * The prefixes supported in BDIO 1.x.
     */
    private static final Map<String, String> PREFIXES = ImmutableMap.<String, String> builder()
            .put("", VOCAB)
            .put("spdx", "http://spdx.org/rdf/terms#")
            .put("doap", "http://usefulinc.com/ns/doap#")
            .put("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
            .put("xsd", "http://www.w3.org/2001/XMLSchema#")
            .build();

    /**
     * The mapping of BDIO 1.x checksum algorithms to fingerprint algorithms. Only the qualified input is necessary as
     * the values are otherwise identical.
     */
    private static final Map<String, String> FINGERPRINT_ALGORITHMS = ImmutableMap.<String, String> builder()
            .put("http://spdx.org/rdf/terms#checksumAlgorithm_md5", "md5")
            .put("http://spdx.org/rdf/terms#checksumAlgorithm_sha1", "sha1")
            .build();

    /**
     * Regular expression for parsing SPDX creators.
     *
     * @see <a href="https://spdx.org/spdx-specification-21-web-version#h.i0jy297kwqcm">SPDX Creator</a>
     */
    private static Pattern SPDX_CREATOR = Pattern.compile("(?:Person: (?<personName>.*))"
            + "|(?:Tool: (?<toolName>.*?)(?:-(?<toolVersion>.+))?)"
            + "|(?:Organization: (?<organizationName>.*))");

    /**
     * The value object mapper to use for converting expanded JSON-LD values back to Java objects.
     */
    private static final ValueObjectMapper valueObjectMapper = new ValueObjectMapper();

    public LegacyBdio1xEmitter(InputStream bdioData) {
        super(streamLazyFromJson(bdioData, List.class)
                .map(jsonld -> {
                    // THIS IS GOING TO BE SLOW AND USE A LOT OF MEMORY.
                    try {
                        JsonLdOptions options = new JsonLdOptions();

                        // Detect and set the expansion context
                        String contextResourceName = Bdio.Context.forSpecVersion(specVersion(jsonld)).resourceName();
                        ByteSource context = Resources.asByteSource(Resources.getResource(Bdio.class, contextResourceName));
                        try (InputStream contextInputStream = context.openBufferedStream()) {
                            options.setExpandContext(JsonUtils.fromInputStream(contextInputStream));
                        }

                        // Expand the JSON-LD
                        return JsonLdProcessor.expand(jsonld, options);
                    } catch (JsonLdError e) {
                        // TODO Auto-generated catch block
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .flatMap(bdio1x -> {
                    // Scan for BillOfMaterials node to populate metadata
                    BdioMetadata metadata = bdio1x.stream().limit(10)
                            .filter(obj -> checkType(obj, "BillOfMaterials"))
                            .map(bom -> {
                                BdioMetadata md = new BdioMetadata();
                                getString(bom, "@id").ifPresent(md::id);
                                getString(bom, "spdx:name").ifPresent(md::name);
                                creationInfo(get(bom, "spdx:creationInfo"), md);
                                return md;
                            })
                            .reduce(new BdioMetadata(), BdioMetadata::merge);
                    // TODO Rewrite the metadata identifier to remove the "#SPDXRef-DOCUMENT"

                    // This is a guess. 20,000 should avoid the 16MB limit.
                    Stream<Object> entries = StreamSupport.stream(Spliterators.spliteratorUnknownSize(Iterators.partition(bdio1x.iterator(), 20_000), 0), false)

                            // Convert each partition of BDIO 1.x nodes into a list of BDIO nodes
                            .map(nodes -> nodes.stream()
                                    .flatMap(LegacyBdio1xEmitter::toBdio2Node)
                                    .collect(Collectors.toList()))

                            // The flat mapping in the previous step could have ignored all the nodes
                            .filter(nodes -> !nodes.isEmpty())

                            // Wrap each list with the identifier
                            .map(nodes -> metadata.asNamedGraph(nodes, JsonLdConsts.ID));

                    // Include the "header" entry along with the rest of the entries
                    return Stream.concat(Stream.of(metadata.asNamedGraph()), entries);
                })
                .spliterator());
    }

    public static Stream<Object> toBdio2Node(Object node) {
        Objects.requireNonNull(node);
        if (checkType(node, "File")) {
            File file = toBdio2File(node);
            return file.size() > 2 ? Stream.of(file) : Stream.empty();
        } else if (checkType(node, "Component")) {
            // TODO Convert components
            return Stream.empty();
        } else if (checkType(node, "License")) {
            // TODO Convert licenses
            return Stream.empty();
        } else if (checkType(node, "Project")) {
            // TODO Handle current version, returns two nodes...
            return Stream.of(toBdio2Project(node));
        } else {
            return Stream.empty();
        }
    }

    public static File toBdio2File(Object node) {
        File file = new File(getString(node, "@id").get())
                .byteCount(getNumber(node, "size").map(Number::longValue).orElse(null));
        checksums(get(node, "spdx:checksum"), file::fingerprint);
        return file;
    }

    public static Project toBdio2Project(Object node) {
        return new Project(getString(node, "@id").get())
                .name(getString(node, "doap:name").orElse(null));
    }

    /**
     * Scans the supplied list of nodes and attempts to extract the specification version. If the specification version
     * is missing, this method returns an empty string (which is the v0 string since we didn't initially have a
     * specification version).
     */
    private static String specVersion(List<?> input) {
        for (Object obj : input) {
            if (checkType(obj, "BillOfMaterials")) {
                return getString(obj, "specVersion").orElse("");
            }
        }
        return "";
    }

    /**
     * Copies a creation info object into a metadata instance.
     */
    private static void creationInfo(Optional<Object> obj, BdioMetadata metadata) {
        // Map the creator product code
        // TODO Creator can be a list...
        obj.flatMap(o -> getString(o, "spdx:creator"))
                .map(SPDX_CREATOR::matcher)
                .filter(Matcher::matches)
                .ifPresent(m -> {
                    if (m.group("personName") != null) {
                        metadata.creator(m.group("personName"));
                    } else if (m.group("toolName") != null) {
                        StringBuilder producer = new StringBuilder();
                        producer.append(m.group("toolName").replace(' ', '-'));
                        if (m.group("toolVersion") != null) {
                            producer.append('/').append(m.group("toolVersion"));
                        }
                        metadata.producer(Products.valueOf(producer.toString()));
                    } else if (m.group("organizationName") != null) {
                        // TODO
                    }
                });

        // Map the created time
        obj.flatMap(o -> getString(o, "spdx:created")).flatMap(created -> {
            try {
                return Optional.of(Instant.parse(created));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }).ifPresent(metadata::creation);
    }

    /**
     * Copies a checksums list into a fingerprint consumer.
     */
    private static void checksums(Optional<Object> obj, Consumer<Fingerprint> consumer) {
        Object checksums = obj.orElse(null);
        if (checksums instanceof Map<?, ?>) {
            checksums = Arrays.asList(checksums);
        }
        if (checksums instanceof List<?>) {
            for (Object checksum : (List<?>) checksums) {
                getString(checksum, "spdx:algorithm")
                        .map(x -> FINGERPRINT_ALGORITHMS.getOrDefault(x, x))
                        .ifPresent(algorithm -> getString(checksum, "spdx:checksumValue")
                                .ifPresent(checksumValue -> consumer.accept(Fingerprint.create(algorithm, checksumValue))));
            }
        }
    }

    /**
     * Attempts to get a value using a BDIO 1.x term or JSON-LD keyword. If the term is not found directly, another
     * attempt is made by resolving the terms prefix (using the vocabulary by default). In BDIO 1.x we never officially
     * supported using your own context and the supported context just used the {@code @vocab} feature of JSON-LD so
     * this should be a safe way to extract a value from a node.
     *
     * @param obj
     *            an arbitrary object that is expected to a be a {@code Map<String, Object>}.
     * @param termOrKeyword
     *            an optionally prefixed term or keyword, e.g. {@code spdx:name}, {@code size} or {@code @id}.
     */
    private static Optional<Object> get(@Nullable Object obj, String termOrKeyword) {
        if (obj instanceof Map<?, ?>) {
            Object value = null;

            // If it's not a keyword, try the fully expanded key first
            if (termOrKeyword.charAt(0) != '@') {
                int pos = termOrKeyword.indexOf(':');
                value = ((Map<?, ?>) obj).get(PREFIXES.get(termOrKeyword.substring(0, Math.max(0, pos))) + termOrKeyword.substring(pos + 1));
            }

            // Keyword or somehow not expanded
            if (value == null) {
                value = ((Map<?, ?>) obj).get(termOrKeyword);
            }

            // Convert '@value' objects
            return Optional.ofNullable(valueObjectMapper.fromFieldValue(value));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Helper to get string values.
     *
     * @see #get(Object, String)
     */
    private static Optional<String> getString(@Nullable Object obj, String termOrKeyword) {
        return get(obj, termOrKeyword).flatMap(str -> str instanceof String ? Optional.of((String) str) : Optional.empty());
    }

    /**
     * Helper to get numeric values.
     *
     * @see #get(Object, String)
     */
    private static Optional<Number> getNumber(@Nullable Object obj, String termOrKeyword) {
        return get(obj, termOrKeyword).flatMap(num -> num instanceof Number ? Optional.of((Number) num) : Optional.empty());
    }

    /**
     * Checks to see if a node has a specific type. All BDIO 1.x classes were defined using the vocabulary so only the
     * type's term and vocabulary prefiexed term are tested.
     */
    private static boolean checkType(Object obj, String type) {
        Object actualType = get(obj, "@type").orElse(null);
        if (actualType instanceof Collection<?>) {
            for (Object actualIndividualType : (Collection<?>) actualType) {
                if (Objects.equals(actualIndividualType, type) || Objects.equals(actualIndividualType, VOCAB + type)) {
                    return true;
                }
            }
            return false;
        } else {
            return Objects.equals(actualType, type) || Objects.equals(actualType, VOCAB + type);
        }
    }

}
