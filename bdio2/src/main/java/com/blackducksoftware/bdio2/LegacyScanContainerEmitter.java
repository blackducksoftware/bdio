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

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.datatype.Fingerprint;
import com.blackducksoftware.bdio2.datatype.Products;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.model.Project;
import com.blackducksoftware.common.base.ExtraStrings;
import com.blackducksoftware.common.base.HID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * An adapter to convert a scan container (a.k.a. "dry run file") into BDIO.
 *
 * @author jgustie
 */
class LegacyScanContainerEmitter extends SpliteratorEmitter {

    /*
     * Notes on identifiers....
     * <ol>
     * <li>The {@code ProtexBomEventHandler} uses the project identifier as the {@code ScanContainer.hostName}</li>
     * <li>The {@code ScanGroupApi} uses the {@code ScanContainer.hostName} as the unique key IF the type is
     * {@code BOM_IMPORT}</li>
     * <li>The {@code ScanGroupApi} uses the host name and base directory as the unique key IF the type is
     * {@code FS}</li>
     * </ol>
     * So by setting the project identifier to the same value used for {@code FS} imports we keep them the same
     * unique scan group key.
     * <p>
     * ...BUT: if we did that then the base directory "File" node and the "Project" node would have the same
     * identifier. To make them unique we append a fragment to the project identifier (assuming fragments are
     * stripped off in the {@code ScanGroupApi}.
     */

    /**
     * File name extension to URI scheme mapping. This handles most of the legacy cases that we support, there are other
     * file extensions that would normally be valid (we can't get them all), so this only covers the 80-90% case.
     */
    private static final Map<String, String> EXTENSION_TO_SCHEME = ImmutableMap.<String, String> builder()
            .put("zip", "zip")
            .put("bz", "zip")
            .put("z", "zip")
            .put("nupg", "zip")
            .put("xpi", "zip")
            .put("egg", "zip")
            .put("jar", "zip")
            .put("war", "zip")
            .put("rar", "zip")
            .put("apk", "zip")
            .put("ear", "zip")
            .put("car", "zip")
            .put("nbm", "zip")
            .put("rpm", "rpm")
            .put("tar", "tar")
            .put("tgz", "tar")
            .put("txz", "tar")
            .put("tbz", "tar")
            .put("tbz2", "tar")
            .put("ar", "ar")
            .put("lib", "ar")
            .put("arj", "arj")
            .put("7z", "sevenZ")
            .build();

    /**
     * Internal representation of a legacy scan node used for conversion to BDIO.
     */
    public static final class LegacyScanContainer {

        @Nullable
        private final String baseDir;

        @Nullable
        private final Date createdOn;

        @Nullable
        private final String hostName;

        @Nullable
        private final String name;

        @Nullable
        private final String project;

        @Nullable
        private final String release;

        private final Map<Long, LegacyScanNode> scanNodeList;

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
            this.createdOn = createdOn;
            this.hostName = hostName;
            this.name = name;
            this.project = project;
            this.release = release;
            this.scanNodeList = scanNodeList != null ? Maps.uniqueIndex(scanNodeList, scanNode -> scanNode.id) : ImmutableMap.of();
            this.scannerVersion = scannerVersion;
            this.signatureVersion = signatureVersion;
        }

        private BdioMetadata metadata() {
            return new BdioMetadata().id(toFileUri(hostName, baseDir, "bdio"))
                    .name(name)
                    .creation(createdOn != null ? createdOn.toInstant() : null)
                    .producer(new Products.Builder()
                            // TODO Add a product for us?
                            .addProduct("HubScanClient", scannerVersion, "Signature " + signatureVersion)
                            .build());

        }

        private Stream<Map<String, Object>> nodes() {
            return Stream.concat(
                    Stream.of(new Project(toFileUri(hostName, baseDir, "PROJECT"))
                            .name(project)
                            .version(release)
                            .base(toFileUri(hostName, baseDir, null))),
                    scanNodeList.values().stream().map(scanNode -> scanNode.file(this)));
        }
    }

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

        private final Map<String, String> signatures;

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

        private File file(LegacyScanContainer scanContainer) {
            HID fileHid = fileHid(scanContainer, this);
            File bdioFile = new File(fileHid.toUri().toString());
            // TODO Best we can do for media type is extension match
            if (type == null
                    || type.equals(LegacyScanNode.TYPE_FILE)
                    || type.equals(LegacyScanNode.TYPE_ARCHIVE)) {
                bdioFile.byteCount(size);

                signatures.entrySet().stream()
                        .map(e -> Fingerprint.create(algorithmName(e.getKey()), e.getValue()))
                        .forEach(bdioFile::fingerprint);
            } else if (!type.equals(LegacyScanNode.TYPE_DIRECTORY)) {
                throw new IllegalArgumentException("invalid file type: " + type);
            }
            return bdioFile;
        }
    }

    /**
     * This module makes the configured object mapper behave like the one used to parse legacy scan container objects.
     */
    public static final class LegacyScanContainerModule extends Module {
        private static final LegacyScanContainerModule INSTANCE = new LegacyScanContainerModule();

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

    public LegacyScanContainerEmitter(InputStream scanContainerData) {
        super(streamLazyFromJson(scanContainerData, LegacyScanContainer.class, LegacyScanContainerModule.INSTANCE)
                .flatMap(scanContainer -> {
                    BdioMetadata metadata = scanContainer.metadata();
                    return Stream.concat(
                            Stream.of(metadata.asNamedGraph()),
                            partitionNodes(scanContainer.nodes())
                                    .map(graph -> (Object) metadata.asNamedGraph(graph, JsonLdConsts.ID)));
                })
                .spliterator());
    }

    private static Stream<List<Map<String, Object>>> partitionNodes(Stream<Map<String, Object>> nodeStream) {
        Spliterator<Map<String, Object>> nodes = nodeStream.spliterator();
        return StreamSupport.stream(new AbstractSpliterator<List<Map<String, Object>>>(nodes.estimateSize(), nodes.characteristics() | Spliterator.NONNULL) {
            @Override
            public boolean tryAdvance(Consumer<? super List<Map<String, Object>>> action) {
                // TODO Apply a load factor to Bdio.MAX_ENTRY_SIZE
                Partition<Map<String, Object>> partition = new Partition<>(LegacyScanContainerEmitter::estimateSize, Bdio.MAX_ENTRY_SIZE);
                while (nodes.tryAdvance(partition) && !partition.isFull()) {
                }
                return partition.consume(action);
            }
        }, false);
    }

    /**
     * Attempts to estimate the serialized JSON size of an object by only looking at what is already in available. This
     * means no character encoding: that would create overhead that isn't necessary for this computation.
     */
    private static int estimateSize(Object obj) {
        return estimateSize(obj, 0);
    }

    /**
     * Internal implementation that considers pretty print depth.
     */
    private static int estimateSize(Object obj, int depth) {
        if (obj instanceof String) {
            return 2 + ((String) obj).length(); // Estimate using single byte characters
        } else if (obj instanceof Number) {
            return 0; // TODO
        } else if (obj instanceof List<?>) {
            int size = 2 + ((List<?>) obj).size(); // Estimate overhead of list itself
            for (Object item : (List<?>) obj) {
                size += estimateSize(item);
            }
            return size;
        } else if (obj instanceof Map<?, ?>) {
            int size = 2 + (((Map<?, ?>) obj).size() * 3); // Estimate overhead of map itself
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                size += estimateSize(entry.getKey());
                size += estimateSize(entry.getValue());
            }
            return size;
        } else {
            return 0;
        }
    }

    private static class Partition<E> implements Consumer<E> {

        private final ToIntFunction<E> weigher;

        private final long maxWeight;

        private final List<E> partition = new ArrayList<>(); // TODO Give an initial size

        private long weight;

        private Partition(ToIntFunction<E> weigher, long maxWeight) {
            this.weigher = Objects.requireNonNull(weigher);
            this.maxWeight = maxWeight;
        }

        @Override
        public void accept(E element) {
            weight += weigher.applyAsInt(element);
            partition.add(element);
        }

        public boolean isFull() {
            return weight >= maxWeight;
        }

        public boolean consume(Consumer<? super List<E>> action) {
            if (partition.isEmpty()) {
                return false;
            } else {
                action.accept(partition);
                return true;
            }
        }
    }

    private static String toFileUri(@Nullable String hostName, @Nullable String baseDir, @Nullable String fragment) {
        try {
            return new URI("file", hostName, baseDir, fragment).toString();
        } catch (URISyntaxException e) {
            return baseDir;
        }
    }

    /**
     * Attempts to reconstruct a HID from the supplied scan node. The archive type is lost in the legacy
     * representation.
     */
    private static HID fileHid(LegacyScanContainer scanContainer, LegacyScanNode scanNode) {
        try {
            String scheme = "file";
            String ssp = null;
            String fragment = null;
            for (LegacyScanNode node : listArchives(scanContainer, scanNode)) {
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
            return HID.from(new URI(scheme, ssp, fragment));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns a sequence of nesting archive nodes leading up to (and including) the supplied scan node.
     */
    private static Iterable<LegacyScanNode> listArchives(LegacyScanContainer scanContainer, LegacyScanNode scanNode) {
        LinkedList<LegacyScanNode> result = new LinkedList<>();
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
     * Guesses a scheme based on a file name. We need to attempt this mapping because the original scheme is lost in the
     * legacy encoding, our only chance of reconstructing it is through extension matching.
     */
    private static String guessScheme(String filename) {
        // Get the cleaned up list of extensions
        String name = filename.substring(filename.lastIndexOf('/') + 1, filename.length());
        List<String> extensions = Splitter.on('.').splitToList(name.substring(name.indexOf('.') + 1, name.length()).toLowerCase());

        // Reverse iteration looking for single extension values (note that "foobar.tar.gz" is matched by "tar")
        for (String part : Lists.reverse(extensions)) {
            String scheme = EXTENSION_TO_SCHEME.get(part);
            if (scheme != null) {
                return scheme;
            }
        }
        return "unknown";
    }

}
