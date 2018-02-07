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

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdConsts;
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
                    .creator(null, hostName)
                    .creationDateTime(createdOn)
                    .publisher(new ProductList.Builder()
                            .addProduct(new Product.Builder()
                                    .name("ScanClient")
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
            return Stream.concat(Stream.of(root()), scanNodeList.values().stream().map(this::file));
        }

        private Map<String, Object> root() {
            String id = toFileUri(hostName, baseDir, "project");
            File base = scanNodeList.isEmpty() ? null : new File(toFileUri(hostName, baseDir, "scanNode-0"));
            if (project != null) {
                return new Project(id).base(base).name(project).version(release);
            } else {
                return new FileCollection(id).base(base);
            }
        }

        private File file(LegacyScanNode scanNode) {
            File bdioFile = new File(toFileUri(hostName, baseDir, "scanNode-" + scanNode.id))
                    .path(path(this, scanNode))
                    .fileSystemType(fileSystemType(scanNode.type, scanNode.signatures.containsKey(LegacyScanNode.SIGNATURES_CLEAN_SHA1)));
            if (scanNode.type == null
                    || scanNode.type.equals(LegacyScanNode.TYPE_FILE)
                    || scanNode.type.equals(LegacyScanNode.TYPE_ARCHIVE)) {
                bdioFile.byteCount(scanNode.size);
                bdioFile.fingerprint(emptyToNull(scanNode.signatures.entrySet().stream()
                        .map(e -> new Digest.Builder()
                                .algorithm(algorithmName(e.getKey()))
                                .value(e.getValue())
                                .build())
                        .collect(toList())));
            }
            return bdioFile;
        }

        private static <T> List<T> emptyToNull(List<T> input) {
            return input.isEmpty() ? null : input;
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

    /**
     * The sequence of BDIO entries to emit.
     */
    private final Spliterator<Object> entries;

    public LegacyScanContainerEmitter(InputStream inputStream) {
        this.entries = Stream.of(inputStream)
                .map(in -> {
                    try {
                        return new ObjectMapper()
                                .registerModule(LegacyScanContainerModule.INSTANCE)
                                .readValue(in, LegacyScanContainer.class);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
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
     * Converts a scan container in BDIO entries. The first entry in the stream will be a "header" entry, the remaining
     * entries will produced as necessary to stay below the serialization size limit.
     */
    private static Stream<Object> toBdioEntries(LegacyScanContainer scanContainer) {
        // Extract the metadata once, we will reuse it for every entry
        BdioMetadata metadata = scanContainer.metadata();

        Stream<List<Map<String, Object>>> graphNodes = LegacyUtilities.partitionNodes(metadata, scanContainer.nodes());
        return Stream.concat(
                Stream.of(metadata.asNamedGraph()),
                graphNodes.map(graph -> metadata.asNamedGraph(graph, JsonLdConsts.ID)));
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
    private static String path(LegacyScanContainer scanContainer, LegacyScanNode scanNode) {
        if (scanNode.uri != null) {
            // The URI is already computed, don't do any work
            return scanNode.uri;
        }

        try {
            String scheme = "file";
            String ssp = null;
            String fragment = null;
            for (LegacyScanNode node : listArchives(scanContainer, scanNode)) {
                // TODO Eliminating intermediate URI construction would be a significant optimization
                if (ssp == null) {
                    // Include the base directory so the hierarchy is preserved up through the base file node
                    String path = ExtraStrings.ensureDelimiter(scanContainer.baseDir, "/", node.path);
                    ssp = new URI(scheme, null, path, null).getRawSchemeSpecificPart();
                } else {
                    // Nest the scheme specific part
                    ssp = HID.from(new URI(scheme, ssp, fragment)).toUriString();
                    fragment = ExtraStrings.ensurePrefix("/", Objects.equals(node.name, "/") ? "" : node.path);
                    scheme = LegacyUtilities.guessScheme(ssp);
                }
            }
            return HID.from(new URI(scheme, ssp, fragment)).toUriString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns a sequence of nesting archive nodes leading up to (and including) the supplied scan node.
     */
    private static Iterable<LegacyScanNode> listArchives(LegacyScanContainer scanContainer, LegacyScanNode scanNode) {
        Deque<LegacyScanNode> result = new ArrayDeque<>();

        // Follow the scan nodes up to root
        LegacyScanNode parent = scanNode;
        while (parent != null) {
            // Add the parent (to the front) if the type is archive
            if (Objects.equals(parent.type, LegacyScanNode.TYPE_ARCHIVE) || result.isEmpty()) {
                result.addFirst(parent);
            }

            // This is why we store the scan nodes in a map
            parent = scanContainer.scanNodeList.get(parent.parentId);
        }
        return result;
    }

    /**
     * Returns the BDIO file system type given the legacy file type.
     */
    @Nullable
    private static String fileSystemType(@Nullable String type, boolean hasCleanSha1) {
        if (type == null) {
            return null;
        }

        switch (type) {
        case LegacyScanNode.TYPE_FILE:
            if (hasCleanSha1) {
                // We only collected clean SHA-1 on text files
                return Bdio.FileSystemType.REGULAR_TEXT.toString();
            } else {
                return Bdio.FileSystemType.REGULAR.toString();
            }
        case LegacyScanNode.TYPE_ARCHIVE:
            return Bdio.FileSystemType.DIRECTORY_ARCHIVE.toString();
        case LegacyScanNode.TYPE_DIRECTORY:
            return Bdio.FileSystemType.DIRECTORY.toString();
        case "PLACEHOLDER":
        case "DECLARED_COMPONENT":
            // This is a real edge case
            return null;
        default:
            throw new IllegalArgumentException("invalid file type: " + type);
        }
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

}
