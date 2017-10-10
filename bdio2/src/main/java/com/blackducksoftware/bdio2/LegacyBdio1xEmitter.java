/*
 * Copyright 2017 Black Duck Software, Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.model.Component;
import com.blackducksoftware.bdio2.model.Dependency;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.model.License;
import com.blackducksoftware.bdio2.model.Project;
import com.blackducksoftware.common.base.ExtraOptionals;
import com.blackducksoftware.common.base.ExtraStrings;
import com.blackducksoftware.common.value.Digest;
import com.blackducksoftware.common.value.Product;
import com.blackducksoftware.common.value.ProductList;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.net.UrlEscapers;

/**
 * An adapter to convert BDIO 1.x data into BDIO.
 *
 * @author jgustie
 */
class LegacyBdio1xEmitter implements Emitter {

    /**
     * Regular expression for parsing SPDX creators.
     *
     * @see <a href="https://spdx.org/spdx-specification-21-web-version#h.i0jy297kwqcm">SPDX Creator</a>
     */
    private static Pattern SPDX_CREATOR = Pattern.compile("(?:Person: (?<personName>.*))"
            + "|(?:Tool: (?<toolName>.*?)(?:-(?<toolVersion>.+))?)"
            + "|(?:Organization: (?<organizationName>.*))");

    /**
     * A class representing the connection between the project and it's base file.
     */
    private static class BaseFile {

        private final AtomicBoolean hasFiles = new AtomicBoolean(false);

        private final AtomicReference<String> projectId = new AtomicReference<>(null);

        private final AtomicReference<String> baseFileId = new AtomicReference<>(null);

        /**
         * Records the presence of a project from a BDIO stream. Note that there are no strict requirements on how many
         * times this can be called (with the same or different identifiers).
         */
        public void accept(Project project) {
            // Only take the first project identifier
            projectId.compareAndSet(null, project.id());
        }

        /**
         * Records each file from a BDIO stream. Note that we are really only interested in the "base file", in BDIO 1.x
         * this means the file with the path "./" (which may not exist).
         */
        public void accept(File file) {
            // Take the first file with a path of "./"
            hasFiles.set(true);
            if (Objects.equals(file.get(Bdio.DataProperty.path.toString()), "./")) {
                baseFileId.compareAndSet(null, file.id());
            }
        }

        /**
         * Returns the graph nodes needed to associate a project to a base file.
         */
        public List<Object> graph() {
            String projectId = this.projectId.getAndSet(null);
            if (projectId != null && hasFiles.get()) {
                // Construct a missing base file if necessary
                Optional<String> baseFileId = Optional.ofNullable(this.baseFileId.getAndSet(null));
                File baseFile = baseFileId.map(File::new).orElseGet(() -> new File("file:///").path("file:///"));

                // Connect the project to the base file
                List<Object> result = new ArrayList<>(2);
                result.add(new Project(projectId).base(baseFile));
                if (!baseFileId.isPresent()) {
                    result.add(baseFile);
                }
                return result;
            } else {
                return Collections.emptyList();
            }
        }
    }

    /**
     * A class representing the archive context of a path. This code is used in an attempt to reconstruct the nesting
     * structure of a file name. In general this only works for Protex BOM Tool generated BDIO when the files are listed
     * in a specific order. It assumes that file names are relatively normalized (e.g. instead of using full HID
     * normalization, it assumes Protex normalized the paths to something mildly compatible with what we are doing).
     */
    private static class Archive {

        /**
         * The container of this archive, for multiple nesting levels.
         */
        private final Archive container;

        /**
         * The flattened file name of this archive. For example, "./foo/bar.jar"
         */
        private final String fileName;

        /**
         * The path used for files nested in this archive. For example, "jar:file%2F%2F%2Ffoo%2Fbar.jar#"
         */
        private final String nestedPath;

        private Archive(Archive container, String fileName) {
            while (container != null && !fileName.startsWith(container.fileName)) {
                container = container.container;
            }

            this.container = container;
            this.fileName = fileName;
            this.nestedPath = new StringBuilder()
                    .append(LegacyUtilities.guessScheme(fileName)).append(':')
                    .append(UrlEscapers.urlPathSegmentEscaper().escape(computePath(container, fileName))).append('#')
                    .toString();
        }

        public static String computePath(Archive container, String fileName) {
            if (container != null) {
                String path = fileName.substring(container.fileName.length());
                return container.nestedPath + UrlEscapers.urlFragmentEscaper().escape(path);
            } else {
                checkArgument(fileName.startsWith("./"), "invalid BDIO 1.x fileName (must start with './'): %s", fileName);
                return "file:///" + Joiner.on('/')
                        .join(Iterables.transform(Splitter.on('/').omitEmptyStrings().split(fileName.substring(2)),
                                UrlEscapers.urlPathSegmentEscaper().asFunction()));
            }
        }
    }

    /**
     * Extends the standard JSON parser with some useful behavior for parsing BDIO 1.x JSON.
     */
    private static class Bdio1JsonParser extends JsonParserDelegate {

        /**
         * The prefixes supported in BDIO 1.x.
         */
        private static final ImmutableMap<String, String> PREFIXES = ImmutableMap.<String, String> builder()
                .put("", "http://blackducksoftware.com/rdf/terms#")
                .put("spdx:", "http://spdx.org/rdf/terms#")
                .put("doap:", "http://usefulinc.com/ns/doap#")
                .put("rdfs:", "http://www.w3.org/2000/01/rdf-schema#")
                .put("xsd:", "http://www.w3.org/2001/XMLSchema#")
                .build();

        public static Bdio1JsonParser create(JsonParser jp) {
            return jp instanceof Bdio1JsonParser ? (Bdio1JsonParser) jp : new Bdio1JsonParser(jp);
        }

        private Bdio1JsonParser(JsonParser d) {
            super(Objects.requireNonNull(d));
        }

        @Override
        public String nextFieldName() throws IOException {
            return applyPrefix(super.nextFieldName());
        }

        @Override
        public String nextTextValue() throws IOException {
            String textValue = super.nextTextValue();
            if (Objects.equals(getCurrentName(), JsonLdConsts.TYPE)) {
                return applyPrefix(textValue);
            }
            return textValue;
        }

        /**
         * Returns the next field value, potentially by recursing through structured types.
         */
        public Object nextFieldValue() throws IOException {
            JsonToken currToken = nextToken();
            if (currToken.isBoolean()) {
                return getBooleanValue();
            } else if (currToken.isNumeric()) {
                return getNumberValue();
            } else if (currToken == JsonToken.VALUE_STRING) {
                return getText();
            } else if (currToken == JsonToken.VALUE_NULL) {
                return null;
            } else if (currToken == JsonToken.START_ARRAY) {
                List<Object> result = new ArrayList<>();
                Object element = nextFieldValue();
                while (getCurrentToken() != JsonToken.END_ARRAY) {
                    result.add(element);
                    element = nextFieldValue();
                }
                return result;
            } else if (currToken == JsonToken.END_ARRAY) {
                return null;
            } else if (currToken == JsonToken.START_OBJECT) {
                Map<String, Object> result = new LinkedHashMap<>();
                while (nextToken() == JsonToken.FIELD_NAME) {
                    result.put(applyPrefix(getCurrentName()), nextFieldValue());
                }
                return result;
            } else {
                throw new JsonParseException(this, "unexpected field value token: " + currToken);
            }
        }

        /**
         * Normalizes on prefix form instead of fully qualified form since that is how the data was probably already
         * presented. Also, the fully qualified names are longer which optimizes the "starts with" computation.
         */
        @Nullable
        private static String applyPrefix(@Nullable String value) {
            if (value != null && value.length() > 0 && value.charAt(0) != '@') {
                for (Map.Entry<String, String> prefix : PREFIXES.entrySet()) {
                    if (value.startsWith(prefix.getValue())) {
                        return prefix.getKey() + value.substring(prefix.getValue().length());
                    }
                }
            }
            return value;
        }
    }

    /**
     * The parser used to stream in the JSON.
     */
    private final Bdio1JsonParser jp;

    /**
     * Reusable single node buffer.
     */
    private final Map<String, Object> currentNode = new LinkedHashMap<>();

    /**
     * Buffer used to hold nodes which are not ready for being emitted.
     */
    private final Deque<Map<String, Object>> buffer = new LinkedList<>();

    /**
     * The base file context used to associate the project with the file hierarchy.
     */
    private final BaseFile baseFile = new BaseFile();

    /**
     * The archive nesting context use to process file paths.
     */
    private Archive archive;

    /**
     * The computed BDIO metadata. Will be {@code null} until enough information has been parsed.
     */
    private BdioMetadata metadata;

    public LegacyBdio1xEmitter(InputStream inputStream) {
        try {
            this.jp = Bdio1JsonParser.create(new JsonFactory().createParser(inputStream));
        } catch (IOException e) {
            // TODO Should this be deferred and thrown from first call to tryAdvance?
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void emit(Consumer<Object> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        Objects.requireNonNull(onNext);
        Objects.requireNonNull(onError);
        Objects.requireNonNull(onComplete);
        try {
            if (metadata == null) {
                if (jp.nextToken() == JsonToken.START_ARRAY && parseMetadata()) {
                    onNext.accept(metadata.asNamedGraph());
                    return;
                }
            } else {
                List<Object> graph = parseGraph();
                if (!graph.isEmpty()) {
                    onNext.accept(metadata.asNamedGraph(graph, JsonLdConsts.ID));
                    return;
                }
            }

            onComplete.run();
        } catch (IOException e) {
            onError.accept(e);
        }
    }

    @Override
    public void dispose() {
        try {
            jp.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Parses the metadata from the stream by reading nodes until the {@code BillOfMaterials} node is encountered.
     */
    private boolean parseMetadata() throws IOException {
        while (readNode()) {
            if (currentType().equals("BillOfMaterials")) {
                // Convert the BillOfMaterials node into BDIO metadata
                metadata = new BdioMetadata().id(currentId());
                currentValue("spdx:name").ifPresent(metadata::name);
                convertCreationInfo(metadata::creationDateTime, metadata::creator, metadata::producer);
                return true;
            } else {
                // Weren't ready for this node yet, buffer a copy for later
                buffer.add(new LinkedHashMap<>(currentNode));
            }
        }

        // We got to the end of the file and never found a BillOfMaterials node
        return false;
    }

    /**
     * Parses a graph by reading enough nodes to fill a BDIO entry.
     */
    private List<Object> parseGraph() throws IOException {
        List<Object> graph = new ArrayList<>(LegacyUtilities.averageEntryNodeCount());
        AtomicInteger estimatedSize = new AtomicInteger(LegacyUtilities.estimateEntryOverhead(metadata));
        while (estimatedSize.get() < Bdio.MAX_ENTRY_SIZE) {
            // Look for something in the buffer, otherwise read the next node
            Map<String, Object> bufferedNode = buffer.pollFirst();
            if (bufferedNode != null) {
                currentNode.clear();
                currentNode.putAll(bufferedNode);
            } else if (!readNode()) {
                break;
            }

            // Convert the current node from BDIO 1.x to BDIO 2.x
            convert(node -> {
                if (estimatedSize.addAndGet(LegacyUtilities.estimateSize(node)) < Bdio.MAX_ENTRY_SIZE) {
                    // Add it to the result set
                    graph.add(node);
                } else {
                    // Just put it back, we will have to re-convert it next time around
                    buffer.offerFirst(new LinkedHashMap<>(currentNode));
                }
            });
        }
        return graph.isEmpty() ? baseFile.graph() : graph;
    }

    /**
     * Populates the {@code currentNode} from the JSON stream.
     */
    private boolean readNode() throws IOException {
        currentNode.clear();
        if (jp.nextToken() == JsonToken.START_OBJECT) {
            while (jp.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = jp.getCurrentName();
                if (fieldName == null) {
                    break;
                } else if (fieldName.startsWith("@")) {
                    currentNode.put(fieldName, jp.nextTextValue());
                } else {
                    currentNode.put(fieldName, jp.nextFieldValue());
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Helper to extract the JSON-LD identifier from the current node.
     */
    private String currentId() {
        Object id = currentNode.get(JsonLdConsts.ID);
        checkState(id instanceof String, "current identifier must be a string: %s", id);
        return (String) id;
    }

    /**
     * Helper to extract the JSON-LD type from the current node.
     */
    private String currentType() {
        Object type = currentNode.get(JsonLdConsts.TYPE);
        checkState(type instanceof String, "current type must be a string: %s", type);
        return (String) type;
    }

    /**
     * Helper to traverse the current node for a {@code String} value.
     *
     * @see #currentValue(Class, Object...)
     */
    private Optional<String> currentValue(Object... paths) {
        return currentValue(String.class, paths);
    }

    /**
     * Helper to traverse the current node. The paths can be string map keys or integer list indexes.
     */
    private <T> Optional<T> currentValue(Class<T> type, Object... paths) {
        Object result = currentNode;
        for (Object path : paths) {
            if (result instanceof Map<?, ?>) {
                result = ((Map<?, ?>) result).get(path);
            } else if (result instanceof List<?>) {
                result = ((List<?>) result).get((Integer) path);
            } else if (result == null) {
                return Optional.empty();
            }
        }
        return type.isInstance(result) ? Optional.of(type.cast(result)) : Optional.empty();
    }

    /**
     * Helper to traverse containers in the current node by returning list or map sizes.
     */
    private int currentSize(Object... paths) {
        return currentValue(Object.class, paths).map(obj -> {
            if (obj instanceof Map<?, ?>) {
                return ((Map<?, ?>) obj).size();
            } else if (obj instanceof List<?>) {
                return ((List<?>) obj).size();
            } else {
                return 0;
            }
        }).orElse(0);
    }

    /**
     * Converts the current node from BDIO 1.x to BDIO 2.x. If the current node should not be included in the final
     * result, this method returns {@code null}.
     */
    private void convert(Consumer<? super Map<String, Object>> graph) {
        String type = currentType();
        if (type.equals("Project")) {
            convertProject(graph);
        } else if (type.equals("Component")) {
            convertComponent(graph);
        } else if (type.equals("License")) {
            convertLicense(graph);
        } else if (type.equals("File")) {
            convertFile(graph);
        }
    }

    private void convertProject(Consumer<? super Project> project) {
        Project result = new Project(currentId());
        currentValue("name").ifPresent(result::name);
        currentValue("revision").ifPresent(result::version);
        convertExternalIdentifier(result::namespace, result::identifier, result::context);
        convertRelationships(result::dependency);
        baseFile.accept(result);
        project.accept(result);
    }

    private void convertComponent(Consumer<? super Component> component) {
        Component result = new Component(currentId());
        currentValue("name").ifPresent(result::name);
        currentValue("homepage").ifPresent(result::homepage);
        convertRevision(result::version, result::requestedVersion);
        // TODO currentValue("license").ifPresent(result::license);
        convertExternalIdentifier(result::namespace, result::identifier, result::context);
        convertRelationships(result::dependency);
        component.accept(result);
    }

    private void convertLicense(Consumer<? super License> license) {
        License result = new License(currentId());
        currentValue("spdx:name").ifPresent(result::name);
        convertExternalIdentifier(result::namespace, result::identifier, result::context);
        license.accept(result);
    }

    private void convertFile(Consumer<? super File> file) {
        File result = new File(currentId());
        currentValue(Number.class, "size").map(Number::longValue).ifPresent(result::byteCount);
        convertPath(result::path);
        convertChecksums(result::fingerprint);
        // TODO "matchDetail", ( "matchType" | "content" | "artifactOf" | "licenseConcluded" )
        baseFile.accept(result);
        file.accept(result);
    }

    /**
     * Converts the creation info from the current node.
     */
    private void convertCreationInfo(Consumer<ZonedDateTime> creationDateTime, Consumer<String> creator, Consumer<ProductList> producer) {
        currentValue("creationInfo", "spdx:created").flatMap(dateTime -> {
            try {
                return Optional.of(OffsetDateTime.parse(dateTime).toZonedDateTime());
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }).ifPresent(creationDateTime);

        Optional<Matcher> creationInfo = currentValue("creationInfo", "spdx:creator")
                .map(SPDX_CREATOR::matcher)
                .filter(Matcher::matches);

        creationInfo.filter(m -> m.group("personName") != null).map(m -> m.group("personName")).ifPresent(creator);

        creationInfo.filter(m -> m.group("toolName") != null).map(m -> {
            Product.Builder result = new Product.Builder().name(m.group("toolName")).version(m.group("toolVersion"));
            // TODO "organizationName" in the comment?
            return ProductList.of(result.build());
        }).ifPresent(producer);
    }

    /**
     * Converts the external identifier from the current node.
     */
    // TODO This only converts a single external identifier!
    // TODO If there are multiple external identifiers, multiple components/projects what-ever need to be created!
    private void convertExternalIdentifier(Consumer<String> namespace, Consumer<String> identifier, Consumer<String> repository) {
        // TODO Attempt to support Black Duck Hub by converting this from an external identifier to Hub reference
        currentValue("externalIdentifier", "externalSystemTypeId").map(LegacyBdio1xEmitter::toNamespace).ifPresent(namespace);
        currentValue("externalIdentifier", "externalId").ifPresent(identifier);
        currentValue("externalIdentifier", "externalRepositoryLocation").ifPresent(repository);
    }

    /**
     * Converts the revision from the current node.
     */
    // TODO Same multiple external identifier bug exists here
    private void convertRevision(Consumer<String> version, Consumer<String> requestedVersion) {
        currentValue("revision").ifPresent(revision -> {
            String externalSystemTypeId = currentValue("externalIdentifier", "externalSystemTypeId").map(LegacyBdio1xEmitter::toNamespace).orElse("");
            String externalId = currentValue("externalIdentifier", "externalId").orElse(null);
            if (externalSystemTypeId.equals("npmjs") && externalId != null) {
                version.accept(ExtraStrings.afterLast(externalId, '@'));
                requestedVersion.accept(revision);
            } else {
                version.accept(revision);
            }
        });
    }

    /**
     * Converts the file name from the current node.
     */
    public void convertPath(Consumer<String> path) {
        currentValue("fileName").ifPresent(fileName -> {
            Archive currentArchive;
            if (currentValue("fileType").filter(Predicate.isEqual("ARCHIVE")).isPresent()) {
                currentArchive = (archive = new Archive(archive, fileName)).container;
            } else {
                while (archive != null && !fileName.startsWith(archive.fileName)) {
                    archive = archive.container;
                }
                currentArchive = archive;
            }
            path.accept(Archive.computePath(currentArchive, fileName));
        });
    }

    /**
     * Converts the checksums from the current node. The supplied consumer may be invoked multiple times.
     */
    private void convertChecksums(Consumer<Digest> fingerprint) {
        for (int index = 0, size = currentSize("checksum"); index < size; ++index) {
            Optional<String> algorithm = currentValue("checksum", index, "algorithm").map(LegacyBdio1xEmitter::toDigestAlgorithm);
            Optional<String> checksumValue = currentValue("checksum", index, "checksumValue");
            ExtraOptionals.and(algorithm, checksumValue, Digest::of).ifPresent(fingerprint);
        }
    }

    /**
     * Converts the relationships from the current node. The supplied consumer may be invoked multiple times.
     */
    private void convertRelationships(Consumer<Dependency> dependency) {
        for (int index = 0, size = currentSize("relationship"); index < size; ++index) {
            String relationshipType = currentValue("relationship", index, "relationshipType").orElse("");
            if (relationshipType.equals("DYNAMIC_LINK") || relationshipType.equals("http://blackducksoftware.com/rdf/terms#relationshipType_dynamicLink")) {
                currentValue("relationship", index, "related")
                        .map(ref -> new Dependency().dependsOn(ref))
                        .ifPresent(dependency);
            }
        }
    }

    /**
     * Returns the digest algorithm name from the BDIO 1.x algorithm identifier.
     */
    private static String toDigestAlgorithm(String algorithm) {
        // Look for both the short form and fully qualified form since these were identifiers in BDIO 1.x
        if (algorithm.equals("sha1") || algorithm.equals("http://spdx.org/rdf/terms#checksumAlgorithm_sha1")) {
            return "sha1";
        } else if (algorithm.equals("md5") || algorithm.equals("http://spdx.org/rdf/terms#checksumAlgorithm_md5")) {
            return "md5";
        } else if (algorithm.indexOf(':') <= 0) {
            return algorithm;
        } else {
            throw new IllegalArgumentException("unable to convert digest algorithm: " + algorithm);
        }
    }

    /**
     * Returns the namespace name from the BDIO 1.x external system type identifier.
     */
    private static String toNamespace(String externalSystemTypeId) {
        // Only strip off the vocab fully qualified portion of the identifier, if present
        externalSystemTypeId = ExtraStrings.removePrefix(externalSystemTypeId, "http://blackducksoftware.com/rdf/terms#");

        // Look for both the short form and fully qualified form since these were identifiers in BDIO 1.x
        if (externalSystemTypeId.equals("anaconda") || externalSystemTypeId.equals("externalIdentifier_anaconda")) {
            return "anaconda";
        } else if (externalSystemTypeId.equals("bower") || externalSystemTypeId.equals("externalIdentifier_bower")) {
            return "bower";
        } else if (externalSystemTypeId.equals("cocoapods") || externalSystemTypeId.equals("externalIdentifier_cocoapods")) {
            return "cocoapods";
        } else if (externalSystemTypeId.equals("cpan") || externalSystemTypeId.equals("externalIdentifier_cpan")) {
            return "cpan";
        } else if (externalSystemTypeId.equals("goget") || externalSystemTypeId.equals("externalIdentifier_goget")) {
            return "golang";
        } else if (externalSystemTypeId.equals("maven") || externalSystemTypeId.equals("externalIdentifier_maven")) {
            return "maven";
        } else if (externalSystemTypeId.equals("npm") || externalSystemTypeId.equals("externalIdentifier_npm")) {
            return "npmjs";
        } else if (externalSystemTypeId.equals("nuget") || externalSystemTypeId.equals("externalIdentifier_nuget")) {
            return "nuget";
        } else if (externalSystemTypeId.equals("rubygems") || externalSystemTypeId.equals("externalIdentifier_rubygems")) {
            return "rubygems";
        } else if (externalSystemTypeId.equals("bdsuite") || externalSystemTypeId.equals("externalIdentifier_bdsuite")) {
            return "bdsuite";
        } else if (externalSystemTypeId.equals("bdhub") || externalSystemTypeId.equals("externalIdentifier_bdhub")) {
            return "bdhub";
        } else if (externalSystemTypeId.equals("openhub") || externalSystemTypeId.equals("externalIdentifier_openhub")) {
            return "openhub";
        } else {
            // Technically this wasn't supported, but it was still allowed
            return externalSystemTypeId;
        }
    }

}
