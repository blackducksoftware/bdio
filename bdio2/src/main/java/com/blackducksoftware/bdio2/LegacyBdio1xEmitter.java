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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.model.Component;
import com.blackducksoftware.bdio2.model.Dependency;
import com.blackducksoftware.bdio2.model.File;
import com.blackducksoftware.bdio2.model.FileCollection;
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
     * Computes additional nodes need for BDIO 2.x.
     */
    private static class NodeComputer {

        /**
         * The number of dependencies to collect before producing a node.
         */
        private static final int DEPENDENCY_BATCH_SIZE = 100;

        /**
         * State indicating that this computer is finished.
         */
        private final AtomicBoolean finished = new AtomicBoolean();

        /**
         * Reference to a supplier that generates a new logically empty node for the root object each time it is
         * invoked. Such a node can be included anywhere in the graph and all nodes will eventually be merged.
         */
        private final AtomicReference<Supplier<BdioObject>> rootObjectRef = new AtomicReference<>();

        /**
         * Reference to a supplier that generates a new node for the base file. This should be a non-null value as long
         * as the BDIO 1.x data contains at least one file node.
         */
        private final AtomicReference<Supplier<File>> baseFileRef = new AtomicReference<>();

        /**
         * A buffer of dependency nodes so we don't create too many root objects.
         */
        private final List<Dependency> dependencyBuffer = new ArrayList<>();

        /**
         * A buffer of converted BDIO 2.x nodes.
         */
        private final Deque<Map<String, Object>> convertedBuffer = new ArrayDeque<>();

        public void setRootProjectId(String id) {
            rootObjectRef.compareAndSet(null, () -> new Project(id));
        }

        public void setRootFileCollectionId(String id) {
            rootObjectRef.compareAndSet(null, () -> new FileCollection(id));
        }

        public void setBaseFile(String id, @Nullable String fileName) {
            if (Objects.equals(fileName, "./")) {
                baseFileRef.set(() -> new File(id));
            } else if (baseFileRef.get() == null) {
                // The double check is just avoid tons of unnecessary lambdas...not sure if that is a thing
                baseFileRef.compareAndSet(null, () -> new File("file:///").path("file:///"));
            }
        }

        public void addRootDependency(Dependency dependency) {
            dependencyBuffer.add(LegacyUtilities.identifyDeclaredByToDependsOn(dependency));
            if (dependencyBuffer.size() > DEPENDENCY_BATCH_SIZE) {
                drainDependencies();
            }
        }

        public void addFirst(Map<String, Object> node) {
            convertedBuffer.addFirst(node);
        }

        public Map<String, Object> pollFirst() {
            return convertedBuffer.pollFirst();
        }

        public boolean finish() {
            if (finished.compareAndSet(false, true)) {
                drainBaseFile();
                drainDependencies();
                return true;
            }
            return false;
        }

        private void drainBaseFile() {
            Supplier<BdioObject> rootObjectSupplier = rootObjectRef.get();
            Supplier<File> baseFileSupplier = baseFileRef.get();
            if (rootObjectSupplier != null && baseFileSupplier != null) {
                BdioObject rootObject = rootObjectSupplier.get();
                File baseFile = baseFileSupplier.get();

                if (rootObject instanceof Project) {
                    ((Project) rootObject).base(baseFile);
                } else if (rootObject instanceof FileCollection) {
                    ((FileCollection) rootObject).base(baseFile);
                }
                addFirst(rootObject);

                if (baseFile.containsKey(Bdio.DataProperty.path.toString())) {
                    addFirst(baseFile);
                }
            }
        }

        private void drainDependencies() {
            Supplier<BdioObject> rootObjectSupplier = rootObjectRef.get();
            if (rootObjectSupplier != null && !dependencyBuffer.isEmpty()) {
                BdioObject rootObject = rootObjectSupplier.get();
                for (Dependency dependency : dependencyBuffer) {
                    if (rootObject instanceof Project) {
                        ((Project) rootObject).dependency(dependency);
                    } else if (rootObject instanceof FileCollection) {
                        ((FileCollection) rootObject).dependency(dependency);
                    }
                }
                dependencyBuffer.clear();
                addFirst(rootObject);
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

        private static final String VOCAB = "http://blackducksoftware.com/rdf/terms#";

        /**
         * The prefixes supported in BDIO 1.x.
         */
        private static final ImmutableMap<String, String> PREFIXES = ImmutableMap.<String, String> builder()
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
         * Normalizes on shorter prefix form instead of the fully qualified form.
         */
        @Nullable
        private static String applyPrefix(@Nullable String value) {
            if (value == null || value.length() <= 26 || value.charAt(0) == '@') {
                return value;
            } else if (value.startsWith(VOCAB)) {
                return value.substring(VOCAB.length());
            } else {
                // TODO Does this do the right thing for terms in the context (which won't need prefixes)?
                for (Map.Entry<String, String> prefix : PREFIXES.entrySet()) {
                    if (value.startsWith(prefix.getValue())) {
                        return prefix.getKey() + value.substring(prefix.getValue().length());
                    }
                }
                return value;
            }
        }
    }

    /**
     * Single use reference to the input stream. Used so we can defer touching the stream until we have an error handler
     * that we can report the error to.
     */
    private final AtomicReference<InputStream> inputStream;

    /**
     * The parser used to stream in the JSON.
     */
    private Bdio1JsonParser jp;

    /**
     * The logic for computing additional BDIO 2.x nodes.
     */
    private final NodeComputer computedNodes = new NodeComputer();

    /**
     * Buffer used to hold BDIO 1.x nodes which are not ready for being emitted.
     */
    private final Deque<Map<String, Object>> unconvertedNodes = new ArrayDeque<>();

    /**
     * Reusable single node buffer for BDIO 1.x data.
     */
    private final Map<String, Object> currentNode = new LinkedHashMap<>();

    /**
     * The archive nesting context use to process file paths.
     */
    private Archive archive;

    /**
     * The computed BDIO metadata. Will be {@code null} until enough information has been parsed.
     */
    private BdioMetadata metadata;

    public LegacyBdio1xEmitter(InputStream inputStream) {
        this.inputStream = new AtomicReference<>(inputStream);
    }

    @Override
    public void emit(Consumer<Object> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        Objects.requireNonNull(onNext);
        Objects.requireNonNull(onError);
        Objects.requireNonNull(onComplete);
        try {
            if (jp == null) {
                InputStream in = inputStream.getAndSet(null);
                if (in != null) {
                    jp = Bdio1JsonParser.create(new JsonFactory().createParser(in));
                }
            }
            if (metadata == null) {
                if (jp.nextToken() != null) {
                    if (!jp.isExpectedStartArrayToken()) {
                        throw new IOException("expected start array:  " + jp.getCurrentToken());
                    }

                    parseMetadata();
                    onNext.accept(metadata.asNamedGraph());
                    return;
                }
            } else {
                List<Map<String, Object>> graph = parseGraph();
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
    private void parseMetadata() throws IOException {
        while (readNode()) {
            if (currentType().equals("BillOfMaterials")) {
                // Convert the BillOfMaterials node into BDIO metadata
                metadata = new BdioMetadata().id(currentId());
                currentValue("spdx:name").ifPresent(metadata::name);
                convertCreationInfo(metadata::creationDateTime, metadata::creator, metadata::producer);
                return;
            } else {
                // Weren't ready for this node yet, buffer a copy for later
                unconvertedNodes.add(new LinkedHashMap<>(currentNode));
            }
        }

        // We got to the end of the file and never found a BillOfMaterials node
        metadata = BdioMetadata.createRandomUUID();
    }

    /**
     * Parses a graph by reading enough nodes to fill a BDIO entry.
     */
    private List<Map<String, Object>> parseGraph() throws IOException {
        List<Map<String, Object>> graph = new ArrayList<>(LegacyUtilities.averageEntryNodeCount());
        int size = fillGraphNodes(graph, LegacyUtilities.estimateEntryOverhead(metadata));

        // If the graph is empty, see if there are any final computed nodes we need to return
        if (graph.isEmpty() && computedNodes.finish()) {
            fillGraphNodes(graph, size);
        }

        return graph;
    }

    /**
     * Attempts to fill the supplied list with nodes.
     */
    private int fillGraphNodes(List<Map<String, Object>> graph, int size) throws IOException {
        AtomicInteger estimatedSize = new AtomicInteger(size);
        Consumer<Map<String, Object>> addToGraph = node -> {
            if (estimatedSize.addAndGet(LegacyUtilities.estimateSize(node)) < Bdio.MAX_ENTRY_SIZE) {
                graph.add(node);
            } else {
                computedNodes.addFirst(node);
            }
        };

        while (estimatedSize.get() < Bdio.MAX_ENTRY_SIZE) {
            // Look for something we already computed
            Map<String, Object> computedNode = computedNodes.pollFirst();
            if (computedNode != null) {
                addToGraph.accept(computedNode);
            } else {
                // Populate the current node from the unconverted node buffer or by reading it
                Map<String, Object> bufferedNode = unconvertedNodes.pollFirst();
                if (bufferedNode != null) {
                    currentNode.clear();
                    currentNode.putAll(bufferedNode);
                } else if (!readNode()) {
                    break;
                }

                // Convert the current node from BDIO 1.x to BDIO 2.x
                convert(addToGraph);
            }
        }

        return estimatedSize.get();
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
                    // TODO Technically '@type' can be a list but right now we override nextTextValue to get it
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
            if (currentValue("name").isPresent()) {
                convertProject(graph);
                computedNodes.setRootProjectId(currentId());
            } else {
                convertFileCollection(graph);
                computedNodes.setRootFileCollectionId(currentId());
            }
        } else if (type.equals("Component")) {
            convertComponent(graph);
        } else if (type.equals("License")) {
            convertLicense(graph);
        } else if (type.equals("File")) {
            convertFile(graph, computedNodes::addRootDependency);
            computedNodes.setBaseFile(currentId(), currentValue("fileName").orElse(null));
        }
    }

    private void convertProject(Consumer<? super Project> project) {
        Project result = new Project(currentId());
        currentValue("name").ifPresent(result::name);
        currentValue("revision").ifPresent(result::version);
        convertExternalIdentifier(result::namespace, result::identifier, result::context);
        convertRelationships(result::dependency);
        project.accept(result);
    }

    private void convertFileCollection(Consumer<? super FileCollection> fileCollection) {
        FileCollection result = new FileCollection(currentId());
        fileCollection.accept(result);
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

    private void convertFile(Consumer<? super File> file, Consumer<? super Dependency> dependency) {
        File result = new File(currentId());
        currentValue(Number.class, "size").map(Number::longValue).ifPresent(result::byteCount);
        convertPath(result::path);
        convertFileTypes(result::fileSystemType);
        convertChecksums(result::fingerprint);
        currentValue("artifactOf")
                .map(component -> new Dependency().declaredBy(result).dependsOn(component))
                .ifPresent(dependency);
        // TODO "matchDetail", ( "matchType" | "content" | "artifactOf" | "licenseConcluded" )
        // TODO "license"
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

        ProductList.Builder producerBuilder = new ProductList.Builder();
        // TODO "organizationName" in the comment?
        creationInfo.filter(m -> m.group("toolName") != null)
                .map(m -> new Product.Builder()
                        .name(m.group("toolName"))
                        .version(m.group("toolVersion"))
                        .build())
                .ifPresent(producerBuilder::addProduct);
        producerBuilder.addProduct(new Product.Builder()
                .simpleName(LegacyBdio1xEmitter.class)
                .implementationVersion(LegacyBdio1xEmitter.class)
                .build());
        producer.accept(producerBuilder.build());
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
    private void convertPath(Consumer<String> path) {
        currentValue("fileName").ifPresent(fileName -> {
            Archive currentArchive;
            // TODO Leverage file systemType conversion? What about multiple values?
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
     * Converts the file system type from the current node.
     */
    private void convertFileTypes(Consumer<String> fileSystemType) {
        // TODO This can have multiple values?
        currentValue("fileType").flatMap(fileType -> Optional.ofNullable(toFileSystemType(fileType))).ifPresent(fileSystemType);
    }

    /**
     * Converts the checksums from the current node. The supplied consumer may be invoked multiple times.
     */
    // TODO This should take a `Consumer<List<Digest>>`
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
     * Returns the file system type from the BDIO 1.x file type.
     */
    private static String toFileSystemType(String fileType) {
        // TODO There were other SPDX types that were supported and were not in the context...
        if (fileType.equals("BINARY") || fileType.equals("APPLICATION")) {
            return Bdio.FileSystemType.REGULAR.toString();
        } else if (fileType.equals("TEXT") || fileType.equals("SOURCE")) {
            return Bdio.FileSystemType.REGULAR_TEXT.toString();
        } else if (fileType.equals("DIRECTORY")) {
            return Bdio.FileSystemType.DIRECTORY.toString();
        } else if (fileType.equals("ARCHIVE")) {
            return Bdio.FileSystemType.DIRECTORY_ARCHIVE.toString();
        } else {
            return null;
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
