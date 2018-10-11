/*
 * Copyright 2016 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2;

import static com.blackducksoftware.bdio2.LegacyUtilities.guessScheme;
import static com.blackducksoftware.bdio2.LegacyUtilities.partitionNodes;
import static com.blackducksoftware.bdio2.LegacyUtilities.scanContainerObjectMapper;
import static com.blackducksoftware.bdio2.LegacyUtilities.toFileUri;
import static com.blackducksoftware.common.base.ExtraStrings.afterLast;
import static com.blackducksoftware.common.base.ExtraStrings.beforeLast;
import static com.blackducksoftware.common.base.ExtraStrings.ensureDelimiter;
import static com.blackducksoftware.common.base.ExtraStrings.ensurePrefix;
import static com.blackducksoftware.common.base.ExtraStrings.removeSuffix;
import static com.google.common.collect.Iterables.getLast;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.model.FileCollection;
import com.blackducksoftware.bdio2.model.Project;
import com.blackducksoftware.common.base.ExtraStrings;
import com.blackducksoftware.common.value.Digest;
import com.blackducksoftware.common.value.HID;
import com.blackducksoftware.common.value.Product;
import com.blackducksoftware.common.value.ProductList;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * An adapter to convert a scan container (a.k.a. "dry run file") into BDIO.
 *
 * @author jgustie
 */
class LegacyScanContainerEmitter implements Emitter {

    /**
     * Internal representation of a legacy scan node used for conversion to BDIO.
     */
    public static final class LegacyScanNode {

        private static final String TYPE_DIRECTORY = "DIRECTORY";

        private static final String TYPE_ARCHIVE = "ARCHIVE";

        private static final String TYPE_FILE = "FILE";

        private static final String TYPE_PLACEHOLDER = "PLACEHOLDER";

        private static final String TYPE_DECLARED_COMPONENT = "DECLARED_COMPONENT";

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

        @Nullable
        private final String uri;

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
                @Nullable @JsonProperty("uri") String uri,
                @Nullable @JsonProperty("signatures") Map<String, String> signatures,
                @Nullable @JsonProperty("size") Long size,
                @Nullable @JsonProperty("type") String type) {
            this.archiveUri = archiveUri;
            this.id = id != null ? id.longValue() : -1L;
            this.name = name;
            this.parentId = parentId;
            this.path = path;
            this.uri = uri;
            this.signatures = signatures != null ? ImmutableMap.copyOf(signatures) : ImmutableMap.of();
            this.size = size;
            this.type = type;
        }

        @Override
        public String toString() {
            return "scanNode-" + id;
        }

        public String path(String baseDir, @Nullable Function<Long, LegacyScanNode> lookup) {
            if (uri != null) {
                // New versions of the scan client include a BDIO 2 compatible definition of the path
                return uri;
            } else {
                // We need to recreate the path, this will not be 100% accurate:
                // 1. We need to resort to filename matching to determine the archive scheme
                // 2. If we fall back to the archive URI, it is ambiguous if any path segment ended with "!"
                HID.Builder builder = new HID.Builder();
                String scheme = "file";
                if (lookup != null) {
                    // Recreate by looking up ancestor archive nodes
                    Deque<LegacyScanNode> scanNodes = new ArrayDeque<>();
                    LegacyScanNode scanNode = this;
                    while (scanNode != null) {
                        if (Objects.equals(scanNode.type, TYPE_ARCHIVE) || scanNodes.isEmpty()) {
                            scanNodes.push(scanNode);
                        }
                        scanNode = lookup.apply(scanNode.parentId);
                    }

                    builder.push(scheme, ensureDelimiter(baseDir, "/", scanNodes.poll().path));
                    scanNodes.forEach(node -> builder.push(guessScheme(builder.peekFilename()), node.nodePath()));
                } else {
                    // Recreate using the archive URI
                    if (id == 0L) {
                        builder.push(scheme, baseDir);
                    } else if (archiveUri.isEmpty()) {
                        builder.push(scheme, ensureDelimiter(baseDir, "/", path));
                    } else {
                        for (String archive : Splitter.on("!/").omitEmptyStrings().split(ensureDelimiter(baseDir, "/", archiveUri))) {
                            builder.push(scheme, archive);
                            scheme = LegacyUtilities.guessScheme(archive);
                        }
                        if (!Objects.equals(type, TYPE_ARCHIVE)) {
                            builder.push(scheme, nodePath());
                        }
                    }
                }
                return builder.build().toUriString();
            }
        }

        private String nodePath() {
            return ensurePrefix("/", Objects.equals(name, "/") ? name : path);
        }

        @Nullable
        public String fileSystemType() {
            if (type == null) {
                return null;
            }

            switch (type) {
            case TYPE_FILE:
                if (signatures.containsKey(SIGNATURES_CLEAN_SHA1)) {
                    // We only collected clean SHA-1 on text files
                    return Bdio.FileSystemType.REGULAR_TEXT.toString();
                } else if (signatures.containsKey(SIGNATURES_SHA1)) {
                    // We only collected SHA-1 on binary files
                    return Bdio.FileSystemType.REGULAR_BINARY.toString();
                } else {
                    return Bdio.FileSystemType.REGULAR.toString();
                }
            case TYPE_ARCHIVE:
                return Bdio.FileSystemType.DIRECTORY_ARCHIVE.toString();
            case TYPE_DIRECTORY:
                return Bdio.FileSystemType.DIRECTORY.toString();
            case TYPE_PLACEHOLDER:
            case TYPE_DECLARED_COMPONENT:
                // This is a real edge case
                return null;
            default:
                throw new IllegalArgumentException("invalid file type: " + type);
            }
        }

        @Nullable
        public Long byteCount() {
            // Only report file sizes (if available) for files and archives
            return type == null || type.equals(TYPE_FILE) || type.equals(TYPE_ARCHIVE) ? size : null;
        }

        @Nullable
        public List<Digest> fingerprint() {
            // Be sure to return null instead of empty here because we don't want to serialize the empty list
            if (signatures.isEmpty()) {
                return null;
            }

            return signatures.entrySet().stream()
                    .map(e -> new Digest.Builder().algorithm(algorithmName(e.getKey())).value(e.getValue()).build())
                    .collect(collectingAndThen(toList(), l -> l.isEmpty() ? null : l));
        }

        /**
         * Returns the BDIO fingerprint algorithm name given the legacy signature type.
         */
        private static String algorithmName(String signatureType) {
            switch (signatureType) {
            case SIGNATURES_SHA1:
                return "sha1";
            case SIGNATURES_MD5:
                return "md5";
            case SIGNATURES_CLEAN_SHA1:
                return "sha1-ascii";
            default:
                return "unknown";
            }
        }

        /**
         * Returns a predicate for testing if a scan node is the base node given the base directory. This used to be as
         * simple as testing for {@code id == 0}, however this is not always the case in newer versions of the scanner
         * (in fact it is just the opposite, the base is the last node emitted).
         *
         * @implNote This method returns a predicate so it can capture the pre-computed expected values, hopefully
         *           this reduces the overhead of heavy use.
         */
        public static Predicate<LegacyScanNode> isBase(String baseDir) {
            String baseUri = HID.valueOf("file:" + baseDir).toUriString();
            String baseArchiveUri = "file:" + beforeLast(baseDir, '/') + "/";
            String baseName = afterLast(baseDir, '/');
            return scanNode -> Objects.equals(scanNode.uri, baseUri)
                    || (Objects.equals("/", scanNode.path)
                            && Objects.equals(scanNode.archiveUri, baseArchiveUri)
                            && Objects.equals(scanNode.name, baseName));
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
        private final Long timeToScan;

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

        @Nullable
        private final String ownerEntityKeyToken;

        @JsonCreator
        public LegacyScanContainer(
                @Nullable @JsonProperty("baseDir") String baseDir,
                @Nullable @JsonProperty("createdOn") Date createdOn,
                @Nullable @JsonProperty("timeToScan") Long timeToScan,
                @Nullable @JsonProperty("hostName") String hostName,
                @Nullable @JsonProperty("name") String name,
                @Nullable @JsonProperty("project") String project,
                @Nullable @JsonProperty("release") String release,
                @Nullable @JsonProperty("scanNodeList") List<LegacyScanNode> scanNodeList,
                @Nullable @JsonProperty("scannerVersion") String scannerVersion,
                @Nullable @JsonProperty("signatureVersion") String signatureVersion,
                @Nullable @JsonProperty("ownerEntityKeyToken") String ownerEntityKeyToken) {
            this.baseDir = removeSuffix(baseDir, "/");
            this.createdOn = createdOn != null ? createdOn.toInstant().atZone(ZoneOffset.UTC) : null;
            this.timeToScan = timeToScan;
            this.hostName = hostName;
            this.name = name;
            this.project = project;
            this.release = release;
            this.scanNodeList = scanNodeList != null ? Maps.uniqueIndex(scanNodeList, scanNode -> scanNode.id) : ImmutableMap.of();
            this.scannerVersion = scannerVersion;
            this.signatureVersion = signatureVersion;
            this.ownerEntityKeyToken = ownerEntityKeyToken;
        }

        public BdioMetadata metadata() {
            Optional<String> name = ExtraStrings.ofEmpty(this.name);
            return new BdioMetadata()
                    .id(name.map(LegacyUtilities::toNameUri).orElseGet(() -> toFileUri(hostName, baseDir, null)))
                    .name(name.orElseGet(() -> hostName + "#" + baseDir))
                    .creator(null, hostName)
                    .creationDateTime(createdOn)
                    .captureInterval(createdOn, createdOn != null && timeToScan != null ? createdOn.plus(timeToScan, ChronoUnit.MILLIS) : null)
                    .publisher(new ProductList.Builder()
                            .addProduct(scanClient())
                            .addProduct(new Product.Builder()
                                    .simpleName(LegacyScanContainerEmitter.class)
                                    .implementationVersion(LegacyScanContainerEmitter.class)
                                    .build())
                            .build());
        }

        public Map<String, Object> rootObject() {
            String id = toFileUri(hostName, baseDir, "root");

            Predicate<LegacyScanNode> isBase = LegacyScanNode.isBase(baseDir);
            Optional<LegacyScanNode> baseScanNode = Optional.ofNullable(scanNodeList.get(0L)).filter(isBase);
            if (!baseScanNode.isPresent() && !scanNodeList.isEmpty()) {
                baseScanNode = Optional.of(getLast(scanNodeList.values())).filter(isBase); // O(1)
                if (!baseScanNode.isPresent()) {
                    baseScanNode = scanNodeList.values().stream().filter(isBase).findFirst(); // O(n)
                }
            }

            File base = baseScanNode.map(scanNode -> new File(toFileUri(hostName, baseDir, scanNode.toString()))).orElse(null);
            if (project != null) {
                return new Project(id)
                        .name(project)
                        .version(release)
                        .base(base);
            } else {
                return new FileCollection(id)
                        .base(base);
            }
        }

        public Stream<Map<String, Object>> files() {
            return scanNodeList.values().stream().map(scanNode -> new File(toFileUri(hostName, baseDir, scanNode.toString()))
                    .fileSystemType(scanNode.fileSystemType())
                    .path(scanNode.path(baseDir, scanNodeList::get))
                    .byteCount(scanNode.byteCount())
                    .fingerprint(scanNode.fingerprint()));
        }

        private Product scanClient() {
            Product.Builder scanClient = new Product.Builder()
                    .name("ScanClient")
                    .version(scannerVersion)
                    .addCommentText("signature %s", signatureVersion);
            if (ownerEntityKeyToken != null && ownerEntityKeyToken.startsWith("SP#")) {
                scanClient.addCommentText("snippets");
            }
            return scanClient.build();
        }
    }

    /**
     * The sequence of BDIO entries to emit.
     */
    private final Spliterator<Object> entries;

    public LegacyScanContainerEmitter(InputStream inputStream) {
        this.entries = Stream.of(inputStream)
                .map(LegacyScanContainerEmitter::parseScanContainer)
                .flatMap(LegacyScanContainerEmitter::toBdioEntries)
                .spliterator();
    }

    @Override
    public void emit(Consumer<Object> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        Objects.requireNonNull(onNext);
        Objects.requireNonNull(onError);
        Objects.requireNonNull(onComplete);
        try {
            if (!entries.tryAdvance(onNext)) {
                onComplete.run();
            }
        } catch (UncheckedIOException e) {
            onError.accept(e.getCause());
        } catch (RuntimeException e) {
            onError.accept(e);
        }
    }

    @Override
    public Stream<Object> stream() {
        return StreamSupport.stream(entries, false);
    }

    /**
     * Parses a legacy scan container object from the supplied input stream.
     */
    private static LegacyScanContainer parseScanContainer(InputStream in) {
        try {
            return scanContainerObjectMapper().readValue(in, LegacyScanContainer.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Converts a scan container in BDIO entries. The first entry in the stream will be a "header" entry, the remaining
     * entries will produced as necessary to stay below the serialization size limit.
     */
    private static Stream<Object> toBdioEntries(LegacyScanContainer scanContainer) {
        BdioMetadata metadata = scanContainer.metadata();
        Stream<Map<String, Object>> nodes = Stream.concat(Stream.of(scanContainer.rootObject()), scanContainer.files());
        return Stream.concat(
                Stream.of(metadata.asNamedGraph()),
                partitionNodes(metadata, nodes).map(graph -> metadata.asNamedGraph(graph, JsonLdConsts.ID)));
    }

}
