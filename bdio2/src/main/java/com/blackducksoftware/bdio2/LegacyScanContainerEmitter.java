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
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.model.Project;
import com.blackducksoftware.common.base.ExtraStrings;
import com.blackducksoftware.common.base.HID;
import com.blackducksoftware.common.value.Digest;
import com.blackducksoftware.common.value.Product;
import com.blackducksoftware.common.value.ProductList;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * An adapter to convert a scan container (a.k.a. "dry run file") into BDIO.
 *
 * @author jgustie
 */
class LegacyScanContainerEmitter extends SpliteratorEmitter {

    /**
     * Internal representation of a legacy scan node used for conversion to BDIO.
     */
    public static final class LegacyScanNode {

        private static final String TYPE_DIRECTORY = "DIRECTORY";

        private static final String TYPE_ARCHIVE = "ARCHIVE";

        private static final String TYPE_FILE = "FILE";

        private static final String SIGNATURES_SHA1 = "FILE_SHA1";

        private static final String SIGNATURES_MD5 = "FILE_MD5";

        private static final String SIGNATURES_CLEAN_SHA1 = "FILE_CLEAN_SHA1";

        @Nullable
        private final String archiveUri;

        private final long id;

        @Nullable
        private final String name;

        @Nullable
        private final Long parentId;

        @Nullable
        private final String path;

        private final ImmutableMap<String, String> signatures;

        @Nullable
        private final Long size;

        @Nullable
        private final String type;

        @JsonCreator
        public LegacyScanNode(
                @Nullable @JsonProperty("archiveUri") String archiveUri,
                @Nullable @JsonProperty("id") Long id,
                @Nullable @JsonProperty("name") String name,
                @Nullable @JsonProperty("parentId") Long parentId,
                @Nullable @JsonProperty("path") String path,
                @Nullable @JsonProperty("signatures") Map<String, String> signatures,
                @Nullable @JsonProperty("size") Long size,
                @Nullable @JsonProperty("type") String type) {
            this.archiveUri = archiveUri;
            this.id = id != null ? id.longValue() : -1L;
            this.name = name;
            this.parentId = parentId;
            this.path = path;
            this.signatures = signatures != null ? ImmutableMap.copyOf(signatures) : ImmutableMap.of();
            this.size = size;
            this.type = type;
        }

    }

    /**
     * Internal representation of a legacy scan node used for conversion to BDIO.
     */
    public static final class LegacyScanContainer {

        @Nullable
        private final String baseDir;

        @Nullable
        private final ZonedDateTime createdOn;

        @Nullable
        private final String hostName;

        @Nullable
        private final String name;

        @Nullable
        private final String project;

        @Nullable
        private final String release;

        private final ImmutableMap<Long, LegacyScanNode> scanNodeList;

        @Nullable
        private final String scannerVersion;

        @Nullable
        private final String signatureVersion;

        @JsonCreator
        public LegacyScanContainer(
                @Nullable @JsonProperty("baseDir") String baseDir,
                @Nullable @JsonProperty("createdOn") Date createdOn,
                @Nullable @JsonProperty("hostName") String hostName,
                @Nullable @JsonProperty("name") String name,
                @Nullable @JsonProperty("project") String project,
                @Nullable @JsonProperty("release") String release,
                @Nullable @JsonProperty("scanNodeList") List<LegacyScanNode> scanNodeList,
                @Nullable @JsonProperty("scannerVersion") String scannerVersion,
                @Nullable @JsonProperty("signatureVersion") String signatureVersion) {
            this.baseDir = baseDir;
            this.createdOn = createdOn != null ? createdOn.toInstant().atZone(ZoneOffset.UTC) : null;
            this.hostName = hostName;
            this.name = name;
            this.project = project;
            this.release = release;
            this.scanNodeList = scanNodeList != null ? Maps.uniqueIndex(scanNodeList, scanNode -> scanNode.id) : ImmutableMap.of();
            this.scannerVersion = scannerVersion;
            this.signatureVersion = signatureVersion;
        }

        protected BdioMetadata metadata() {
            return new BdioMetadata()
                    .id(toFileUri(hostName, baseDir, null))
                    .name(name)
                    .creationDateTime(createdOn)
                    .producer(new ProductList.Builder()
                            .addProduct(new Product.Builder()
                                    .name("HubScanClient")
                                    .version(scannerVersion)
                                    .comment("(signature " + signatureVersion + ")")
                                    .build())
                            .addProduct(new Product.Builder()
                                    .simpleName(LegacyScanContainerEmitter.class)
                                    .implementationVersion(LegacyScanContainerEmitter.class)
                                    .build())
                            .build());
        }

        protected Stream<Map<String, Object>> nodes() {
            return Stream.concat(Stream.of(project()), scanNodeList.values().stream().map(this::file));
        }

        private Project project() {
            return new Project(toFileUri(hostName, baseDir, "project"))
                    .name(project)
                    .version(release)
                    .base(toFileUri(hostName, baseDir, "scanNode-0"));
        }

        private File file(LegacyScanNode scanNode) {
            File bdioFile = new File(toFileUri(hostName, baseDir, "scanNode-" + scanNode.id))
                    .path(toFilePath(this, scanNode));
            if (scanNode.type == null
                    || scanNode.type.equals(LegacyScanNode.TYPE_FILE)
                    || scanNode.type.equals(LegacyScanNode.TYPE_ARCHIVE)) {
                bdioFile.byteCount(scanNode.size);
                scanNode.signatures.entrySet().stream()
                        .map(e -> new Digest.Builder()
                                .algorithm(algorithmName(e.getKey()))
                                .value(e.getValue())
                                .build())
                        .forEach(bdioFile::fingerprint);
            } else if (!scanNode.type.equals(LegacyScanNode.TYPE_DIRECTORY)) {
                throw new IllegalArgumentException("invalid file type: " + scanNode.type);
            }
            return bdioFile;
        }

    }

    /**
     * This module makes the configured object mapper behave like the one used to parse legacy scan container objects.
     */
    public static final class LegacyScanContainerModule extends Module {
        public static final LegacyScanContainerModule INSTANCE = new LegacyScanContainerModule();

        private LegacyScanContainerModule() {
        }

        @Override
        public String getModuleName() {
            return getClass().getSimpleName();
        }

        @Override
        public com.fasterxml.jackson.core.Version version() {
            return com.fasterxml.jackson.core.Version.unknownVersion();
        }

        @Override
        public void setupModule(SetupContext context) {
            ((ObjectMapper) context.getOwner()).enable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
            ((ObjectMapper) context.getOwner()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }
    }

    public LegacyScanContainerEmitter(InputStream scanContainerData) {
        super(parse(scanContainerData).flatMap(LegacyScanContainerEmitter::toBdioEntries).spliterator());
    }

    /**
     * Returns a stream that parses a legacy scan container object from an input stream.
     */
    private static Stream<LegacyScanContainer> parse(InputStream inputStream) {
        return Stream.of(inputStream).map(in -> {
            try {
                return new ObjectMapper()
                        .registerModule(LegacyScanContainerModule.INSTANCE)
                        .readValue(in, LegacyScanContainer.class);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Converts a scan container in BDIO entries. The first entry in the stream will be a "header" entry, the remaining
     * entries will produced as necessary to stay below the serialization size limit.
     */
    private static Stream<Object> toBdioEntries(LegacyScanContainer scanContainer) {
        // Extract the metadata once, we will reuse it for every entry
        BdioMetadata metadata = scanContainer.metadata();

        // The per-entry overhead is 20 bytes plus the size of the identifier: `{"@id":<ID>,"@graph":[<NODES>]}`
        int maxSize = Bdio.MAX_ENTRY_SIZE - (20 + estimateSize(metadata.id()));
        Spliterator<List<Map<String, Object>>> graphNodes = partition(scanContainer.nodes().spliterator(), maxSize, LegacyScanContainerEmitter::estimateSize);

        return Stream.concat(
                Stream.of(metadata.asNamedGraph()),
                StreamSupport.stream(graphNodes, false).map(graph -> metadata.asNamedGraph(graph, JsonLdConsts.ID)));
    }

    /**
     * Attempts to <em>estimate</em> the serialized JSON size of an object without incurring too much overhead.
     */
    private static int estimateSize(@Nullable Object obj) {
        // NOTE: It is better to over estimate then under estimate. String sizes are inflated 10% to account for UTF-8
        // encoding and we count a delimiter for every collection element (even the last).
        if (obj == null) {
            return 4; // "null"
        } else if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString().length(); // Accounts for things like negative numbers
        } else if (obj instanceof List<?>) {
            int size = 2; // "[]"
            for (Object item : (List<?>) obj) {
                size += 1 + estimateSize(item); // <item> ","
            }
            return size;
        } else if (obj instanceof Map<?, ?>) {
            int size = 2; // "{}"
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                size += 1 + estimateSize(entry.getKey()); // <key> ":"
                size += 1 + estimateSize(entry.getValue()); // <value> ","
            }
            return size;
        } else {
            // `StandardCharsets.UTF_8.newEncoder().averageBytesPerChar() == 1.1`
            return 2 + (int) (1.1 * obj.toString().length()); // '"' <obj> '"'
        }
    }

    /**
     * Attempt to construct a file URI using the supplied parts, falling back to the base directory.
     */
    private static String toFileUri(@Nullable String hostName, @Nullable String baseDir, @Nullable String fragment) {
        try {
            return new URI("file", hostName, baseDir, fragment).toString();
        } catch (URISyntaxException e) {
            return fragment != null ? baseDir + '#' + fragment : baseDir;
        }
    }

    /**
     * Attempts to reconstruct a HID from the supplied scan node. The archive type is lost in the legacy
     * representation.
     */
    private static String toFilePath(LegacyScanContainer scanContainer, LegacyScanNode scanNode) {
        try {
            String scheme = "file";
            String ssp = null;
            String fragment = null;
            for (LegacyScanNode node : listArchives(scanContainer, scanNode)) {
                // TODO Eliminating intermediate URI construction would be a significant optimization
                if (ssp == null) {
                    // The file system node should have the host name and base directory
                    String path = ExtraStrings.ensureDelimiter(scanContainer.baseDir, "/", node.path);
                    ssp = new URI(scheme, scanContainer.hostName, path, null).getRawSchemeSpecificPart();
                } else {
                    // Nest the scheme specific part
                    ssp = new URI(scheme, ssp, fragment).toString();
                    fragment = ExtraStrings.ensurePrefix("/", node.path);
                    scheme = guessScheme(ssp);
                }
            }
            return HID.from(new URI(scheme, ssp, fragment)).toUri().toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns a sequence of nesting archive nodes leading up to (and including) the supplied scan node.
     */
    private static Iterable<LegacyScanNode> listArchives(LegacyScanContainer scanContainer, LegacyScanNode scanNode) {
        Deque<LegacyScanNode> result = new LinkedList<>();
        result.add(scanNode);

        // Follow the scan nodes up to root
        LegacyScanNode parent = scanNode;
        while (parent != null) {
            // Add the parent (to the front) if the type is archive
            if (Objects.equals(parent.type, LegacyScanNode.TYPE_ARCHIVE)) {
                result.addFirst(parent);
            }

            // This is why we store the scan nodes in a map
            parent = scanContainer.scanNodeList.get(parent.parentId);
        }
        return result;
    }

    /**
     * Returns the BDIO fingerprint algorithm name given the legacy signature type.
     */
    private static String algorithmName(String signatureType) {
        switch (signatureType) {
        case LegacyScanNode.SIGNATURES_SHA1:
            return "sha1";
        case LegacyScanNode.SIGNATURES_MD5:
            return "md5";
        case LegacyScanNode.SIGNATURES_CLEAN_SHA1:
            return "sha1-ascii";
        default:
            return "unknown";
        }
    }

    /**
     * Guesses a scheme based on a file name. We need to attempt this mapping because the original scheme is lost in the
     * legacy encoding, our only chance of reconstructing it is through extension matching.
     */
    private static String guessScheme(String filename) {
        // Scan backwards through the file name trying to match extensions (case-insensitively)
        // NOTE: we do not differentiate the extension from the base name, e.g. "zip.foo" WILL match as "zip"
        int start, end;
        start = end = filename.length();
        while (start > 0) {
            char c = filename.charAt(--start);
            if (c == '/') {
                // We've hit the end of the filename
                break;
            } else if (c == '.') {
                switch (Ascii.toLowerCase(filename.substring(start + 1, end))) {
                // This list was taken from the old-old legacy code which used to only match extensions
                case "zip":
                case "bz":
                case "z":
                case "nupg":
                case "xpi":
                case "egg":
                case "jar":
                case "war":
                case "rar":
                case "apk":
                case "ear":
                case "car":
                case "nbm":
                    return "zip";
                case "rpm":
                    return "rpm";
                case "tar":
                case "tgz":
                case "txz":
                case "tbz":
                case "tbz2":
                    return "tar";
                case "ar":
                case "lib":
                    return "ar";
                case "arj":
                    return "arj";
                case "7z":
                    return "sevenZ";
                default:
                    // Keep looking
                    end = start;
                }
            }
        }

        // Hopefully this won't break anything downstream that is depending on a specific scheme...
        return "unknown";
    }

}
