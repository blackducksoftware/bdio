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

import static com.blackducksoftware.common.base.ExtraStreams.ofType;
import static com.blackducksoftware.common.base.ExtraStrings.afterLast;
import static com.blackducksoftware.common.base.ExtraStrings.beforeFirst;
import static com.blackducksoftware.common.base.ExtraStrings.beforeLast;
import static com.blackducksoftware.common.base.ExtraStrings.ensurePrefix;
import static com.blackducksoftware.common.base.ExtraStrings.removePrefix;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.model.Annotation;
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
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import com.github.jsonldjava.core.JsonLdConsts;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import com.google.common.net.UrlEscapers;

/**
 * An adapter to convert BDIO 1.x data into BDIO.
 *
 * @author jgustie
 */
class LegacyBdio1xEmitter extends LegacyJsonParserEmitter {

    /**
     * The BDIO 1.x {@code @vocab}.
     */
    private static final String VOCAB = "http://blackducksoftware.com/rdf/terms#";

    /**
     * Regular expression for parsing SPDX creators.
     *
     * @see <a href="https://spdx.org/spdx-specification-21-web-version#h.i0jy297kwqcm">SPDX Creator</a>
     */
    private static Pattern SPDX_CREATOR = Pattern.compile("(?:Person: (?<personName>.*))"
            + "|(?:Tool: (?<toolName>.*?)(?:-(?<toolVersion>.+))?)"
            + "|(?:Organization: (?<organizationName>.*))");

    /**
     * The characters that we can retain when matching tool names to create a product.
     */
    private static final CharMatcher PRODUCT_TOKEN_CHAR = CharMatcher.anyOf("!#$%&'*+-.^_`|~")
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.inRange('A', 'Z'));

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
         * State indicating that we have seen at least one file.
         */
        private final AtomicBoolean hasFile = new AtomicBoolean();

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
        private final Multimap<String, Dependency> dependencyBuffer = LinkedHashMultimap.create();

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

        public void addFile(String id, @Nullable String fileName) {
            hasFile.set(true);
            if (Objects.equals(fileName, "./")) {
                baseFileRef.set(() -> new File(id));
            }
        }

        public void addRootDependency(Dependency dependency) {
            LegacyUtilities.mergeDependency(dependencyBuffer, dependency);
            // TODO We might need to drain more frequently if the number of files on each dependency is too large
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
            if (rootObjectSupplier != null && (baseFileSupplier != null || hasFile.get())) {
                File baseFile;
                if (baseFileSupplier != null) {
                    baseFile = baseFileSupplier.get();
                } else {
                    baseFile = new File("file:///").path("file:///");
                }

                BdioObject rootObject = rootObjectSupplier.get();
                if (rootObject instanceof Project) {
                    ((Project) rootObject).base(baseFile);
                } else if (rootObject instanceof FileCollection) {
                    ((FileCollection) rootObject).base(baseFile);
                }

                // Add the computed nodes to the graph
                addFirst(rootObject);
                if (baseFileSupplier == null) {
                    addFirst(baseFile);
                }
            }
        }

        private void drainDependencies() {
            Supplier<BdioObject> rootObjectSupplier = rootObjectRef.get();
            if (rootObjectSupplier != null && !dependencyBuffer.isEmpty()) {
                BdioObject rootObject = rootObjectSupplier.get();
                for (Dependency dependency : dependencyBuffer.values()) {
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
                int endIndex = fileName.endsWith("/") ? fileName.length() - 1 : fileName.length();
                String path = fileName.substring(container.fileName.length(), endIndex);
                return container.nestedPath + UrlEscapers.urlFragmentEscaper().escape(path);
            } else {
                checkArgument(fileName.startsWith("./"), "invalid BDIO 1.x fileName (must start with './'): %s", fileName);
                return "file:///" + Joiner.on('/').join(Iterables.transform(
                        Splitter.on('/').omitEmptyStrings().split(fileName.substring(2)),
                        UrlEscapers.urlPathSegmentEscaper().asFunction()));
            }
        }
    }

    /**
     * Factory instance to create the specialized JSON parser.
     */
    private static class Bdio1JsonFactory extends JsonFactory {
        private static final long serialVersionUID = 1L;

        @Override
        protected JsonParser _createParser(InputStream in, IOContext ctxt) throws IOException {
            return new Bdio1JsonParser(super._createParser(in, ctxt));
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
        super(new Bdio1JsonFactory(), inputStream);
    }

    @Override
    protected Object next(JsonParser jsonParser) throws IOException {
        Bdio1JsonParser jp = Bdio1JsonParser.create(jsonParser);
        if (metadata == null) {
            if (jp.nextToken() != null) {
                if (!jp.isExpectedStartArrayToken()) {
                    throw new IOException("expected start array:  " + jp.getCurrentToken());
                }

                parseMetadata(jp);
                return metadata.asNamedGraph();
            }
        } else {
            List<Map<String, Object>> graph = parseGraph(jp);
            if (!graph.isEmpty()) {
                return metadata.asNamedGraph(graph, JsonLdConsts.ID);
            }
        }
        return null;
    }

    /**
     * Parses the metadata from the stream by reading nodes until the {@code BillOfMaterials} node is encountered.
     */
    private void parseMetadata(Bdio1JsonParser jp) throws IOException {
        while (readNode(jp)) {
            if (currentType().equals("BillOfMaterials")) {
                // Convert the BillOfMaterials node into BDIO metadata
                metadata = new BdioMetadata().id(beforeLast(currentId(), '#'));

                // Preserve legacy semantics for name handling
                Optional<String> name = currentValue("spdx:name").filter(s -> !s.isEmpty());
                name.map(n -> String.format("%s <%s>", n, metadata.id())).ifPresent(metadata::name);
                name.map(LegacyUtilities::toNameUri).ifPresent(metadata::id);

                convertCreationInfo(metadata::creationDateTime, metadata::creator, metadata::publisher);
                return;
            } else {
                // Weren't ready for this node yet, buffer a copy for later
                unconvertedNodes.add(new LinkedHashMap<>(currentNode));
            }
        }

        // We got to the end of the file and never found a BillOfMaterials node
        metadata = BdioMetadata.createRandomUUID();
        metadata.publisher(ProductList.of(product().build()));
    }

    /**
     * Parses a graph by reading enough nodes to fill a BDIO entry.
     */
    private List<Map<String, Object>> parseGraph(Bdio1JsonParser jp) throws IOException {
        List<Map<String, Object>> graph = new ArrayList<>(LegacyUtilities.averageEntryNodeCount());
        int size = fillGraphNodes(jp, graph, LegacyUtilities.estimateEntryOverhead(metadata));

        // If the graph is empty, see if there are any final computed nodes we need to return
        if (graph.isEmpty() && computedNodes.finish()) {
            fillGraphNodes(jp, graph, size);
        }

        return graph;
    }

    /**
     * Attempts to fill the supplied list with nodes.
     */
    private int fillGraphNodes(Bdio1JsonParser jp, List<Map<String, Object>> graph, int size) throws IOException {
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
                } else if (!readNode(jp)) {
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
    private boolean readNode(Bdio1JsonParser jp) throws IOException {
        currentNode.clear();
        if (jp.nextToken() == JsonToken.START_OBJECT) {
            while (jp.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = jp.getCurrentName();
                if (fieldName != null) {
                    currentNode.put(fieldName, jp.nextFieldValue());
                } else {
                    // TODO Is this reachable?
                    break;
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
        checkState(id == null || id instanceof String, "current identifier must be a string: %s", id);
        if (isNullOrEmpty((String) id)) {
            // BDIO 1.x used blank node identifiers (e.g. "_:b0", which is an invalid URI) when the ID was absent
            id = BdioObject.randomId();
            currentNode.put(JsonLdConsts.ID, id);
        }
        return (String) id;
    }

    /**
     * Helper to extract the JSON-LD type from the current node.
     */
    private String currentType() {
        Object type = currentNode.get(JsonLdConsts.TYPE);
        if (type instanceof List<?>) {
            // TODO Is this what we want to do?
            type = ((List<?>) type).get(0);
        }
        checkState(type instanceof String, "current type must be a string: %s", type);
        return removePrefix((String) type, VOCAB);
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
     * Helper to traverse the current node for a sequence of {@code String} values.
     *
     * @see #currentValues(Class, Object...)
     */
    private Stream<String> currentValues(Object... paths) {
        return currentValues(String.class, paths);
    }

    /**
     * Helper to traverse the current node for a sequence of values.
     *
     * @see #currentValue(Class, Object...)
     */
    private <T> Stream<T> currentValues(Class<T> type, Object... paths) {
        Object value = currentValue(Object.class, paths).orElse(null);
        if (value instanceof Iterable<?>) {
            return Streams.stream((Iterable<?>) value).flatMap(ofType(type));
        } else {
            return type.isInstance(value) ? Stream.of(type.cast(value)) : Stream.empty();
        }
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
     * Converts the current node from BDIO 1.x to BDIO 2.x.
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
            computedNodes.addFile(currentId(), currentValue("fileName").orElse(null));
        }
    }

    private void convertProject(Consumer<? super Project> project) {
        Project result = new Project(currentId());
        currentValue("name").ifPresent(result::name);
        currentValue("revision").ifPresent(result::version);
        convertExternalIdentifier(result::namespace, result::identifier, result::context);
        convertRelationships(result::dependency);
        convertComment(result::description);
        project.accept(result);
    }

    private void convertFileCollection(Consumer<? super FileCollection> fileCollection) {
        FileCollection result = new FileCollection(currentId());
        convertComment(result::description);
        fileCollection.accept(result);
    }

    private void convertComponent(Consumer<? super Component> component) {
        Component result = new Component(currentId());
        currentValue("name").ifPresent(result::name);
        currentValue("homepage").ifPresent(result::homepage);
        convertRevision(result::version, result::requestedVersion);
        currentValue("license").ifPresent(result::license);
        convertExternalIdentifier(result::namespace, result::identifier, result::context);
        convertRelationships(result::dependency);
        convertComment(result::description);
        component.accept(result);
    }

    private void convertLicense(Consumer<? super License> license) {
        License result = new License(currentId());
        currentValue("spdx:name").ifPresent(result::name);
        convertExternalIdentifier(result::namespace, result::identifier, result::context);
        convertComment(result::description);
        license.accept(result);
    }

    private void convertFile(Consumer<? super File> file, Consumer<? super Dependency> dependency) {
        File result = new File(currentId());
        currentValue(Number.class, "size").map(Number::longValue).ifPresent(result::byteCount);
        convertPath(result::path);
        convertFileTypes(result::fileSystemType);
        convertChecksums(result::fingerprint);
        convertMatchDetail(result, dependency);
        convertComment(result::description);

        currentValue("artifactOf").map(dependsOn -> {
            Dependency dep = new Dependency().dependsOn(dependsOn).evidence(result);
            currentValue("licenseConcluded").ifPresent(dep::license);
            return dep;
        }).ifPresent(dependency);

        // Handle an edge case where the root directory might have erroneously declared a size of zero
        if (Objects.equals(currentValue("fileName").orElse(null), "./")
                && currentValue(Number.class, "size").orElse(-1).longValue() == 0) {
            result.remove(Bdio.DataProperty.byteCount.toString());
        }

        // The current node did not contain enough meaningful file data to be included. This assumption is only safe for
        // files because somewhere there must be an association between the file identifier and the path which would
        // result in a size of at least 3.
        if (result.size() > 2) {
            file.accept(result);
        }
    }

    /**
     * Converts a generic comment.
     */
    private void convertComment(Consumer<Annotation> annotation) {
        currentValue("rdfs:comment").map(comment -> new Annotation().comment(comment)).ifPresent(annotation);
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

        // Attempt to capture the hostname from the BillOfMaterials "@id"
        String hostname = Optional.of(currentId())
                .flatMap(id -> {
                    try {
                        return Optional.ofNullable(ensurePrefix("@", URI.create(id).getHost()));
                    } catch (IllegalArgumentException e) {
                        return Optional.empty();
                    }
                }).orElse("");

        // TODO Is this acceptable for multiple "Person:" entries?
        StringJoiner creatorBuilder = new StringJoiner(",");
        ProductList.Builder producerBuilder = new ProductList.Builder();
        currentValues("creationInfo", "spdx:creator").map(SPDX_CREATOR::matcher).filter(Matcher::matches).forEach(m -> {
            // We ignore the organization
            if (Strings.emptyToNull(m.group("personName")) != null) {
                creatorBuilder.add(m.group("personName") + hostname);
            } else if (m.group("toolName") != null) {
                String name = Strings.emptyToNull(PRODUCT_TOKEN_CHAR.retainFrom(m.group("toolName")));
                String version = Optional.ofNullable(m.group("toolVersion")).map(PRODUCT_TOKEN_CHAR::retainFrom).flatMap(ExtraStrings::ofEmpty).orElse(null);
                if (name != null) {
                    producerBuilder.addProduct(new Product.Builder().name(name).version(version).build());
                }
            }
        });

        // Note that mapping through forSpecVersion forces the version to be valid
        String specVersion = currentValue("specVersion").filter(s -> !s.isEmpty()).orElse("1.0.0");
        Bdio.Context.forSpecVersion(specVersion);
        producerBuilder.addProduct(product().addCommentText("bdio %s", specVersion).build());

        creator.accept(ExtraStrings.ofEmpty(creatorBuilder.toString()).orElse(emptyToNull(hostname)));
        producer.accept(producerBuilder.build());
    }

    /**
     * Converts the external identifier from the current node.
     */
    // TODO This only converts a single external identifier!
    // TODO Multiple external identifiers = multiple components/projects with canonical links
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
            String namespace = currentValue("externalIdentifier", "externalSystemTypeId").map(LegacyBdio1xEmitter::toNamespace).orElse("");
            String externalId = currentValue("externalIdentifier", "externalId").orElse(null);
            // TODO Did we support requested versions in Ruby Bundler as well?
            if (namespace.equals("npmjs") && externalId != null) {
                version.accept(afterLast(externalId, '@'));
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
            if (currentValues("fileType").flatMap(LegacyBdio1xEmitter::toFileSystemType)
                    .anyMatch(Predicate.isEqual(Bdio.FileSystemType.DIRECTORY_ARCHIVE.toString()))) {
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
        currentValues("fileType")
                .flatMap(LegacyBdio1xEmitter::toFileSystemType)
                // TODO Try to sort this? Or just take the first one?
                .limit(1)
                .forEach(fileSystemType);
    }

    /**
     * Converts the checksums from the current node. The supplied consumer may be invoked multiple times.
     */
    private void convertChecksums(Consumer<Collection<Digest>> fingerprint) {
        List<Digest> fingerprints = new ArrayList<>(currentSize("checksum"));
        for (int index = 0, size = currentSize("checksum"); index < size; ++index) {
            Optional<String> algorithm = currentValue("checksum", index, "algorithm").flatMap(LegacyBdio1xEmitter::toDigestAlgorithm);
            Optional<String> checksumValue = currentValue("checksum", index, "checksumValue");
            ExtraOptionals.and(algorithm, checksumValue, Digest::of).ifPresent(fingerprints::add);
        }
        if (!fingerprints.isEmpty()) {
            fingerprint.accept(fingerprints);
        }
    }

    /**
     * Converts the match detail for a file from the current node.
     */
    private void convertMatchDetail(File file, Consumer<? super Dependency> dependency) {
        currentValue("matchDetail", "artifactOf").flatMap(dependsOn -> {
            Dependency dep = new Dependency().dependsOn(dependsOn);
            currentValue("matchDetail", "licenseConcluded").ifPresent(dep::license);
            String matchType = currentValue("matchDetail", "matchType").orElse(null);
            if (matchType == null) {
                // Treat this like an exact match (i.e. as if the "artifactOf" was directly on the file)
                dep.evidence(file);
            } else if (checkIdentifier(matchType, "PARTIAL", "http://blackducksoftware.com/rdf/terms#matchType_partial")) {
                // We don't know the actual range but we need it to differentiate from an exact match
                dep.evidence(file);
                dep.range("bytes */0"); // TODO What range do we use?
            } else if (checkIdentifier(matchType, "DEPENDENCY", "http://blackducksoftware.com/rdf/terms#matchType_dependency")) {
                // Ignore content, we don't have enough context to do anything useful with it
                dep.declaredBy(file);
            } else {
                // Unsupported match type
                return Optional.empty();
            }
            return Optional.of(dep);
        }).ifPresent(dependency);
    }

    /**
     * Converts the relationships from the current node. The supplied consumer may be invoked multiple times.
     */
    private void convertRelationships(Consumer<Dependency> dependency) {
        for (int index = 0, size = currentSize("relationship"); index < size; ++index) {
            String relationshipType = currentValue("relationship", index, "relationshipType").orElse("");
            if (checkIdentifier(relationshipType, "DYNAMIC_LINK", "http://blackducksoftware.com/rdf/terms#relationshipType_dynamicLink")) {
                currentValue("relationship", index, "related")
                        .map(ref -> new Dependency().dependsOn(ref))
                        .ifPresent(dependency);
            }
        }
    }

    /**
     * Returns the file system type from the BDIO 1.x file type.
     */
    private static Stream<String> toFileSystemType(String fileType) {
        // TODO BDIO 1.x had "OTHER" that isn't accounted for
        // TODO SPDX has "_documentation", "_image", "_audio", "_video", "_spdx" that aren't accounted for
        // TODO Should binary map to regular/binary?
        if (checkIdentifier(fileType, "BINARY", "http://spdx.org/rdf/terms#fileType_binary")
                || checkIdentifier(fileType, "APPLICATION", "http://spdx.org/rdf/terms#fileType_application")) {
            return Stream.of(Bdio.FileSystemType.REGULAR.toString());
        } else if (checkIdentifier(fileType, "TEXT", "http://spdx.org/rdf/terms#fileType_text")
                || checkIdentifier(fileType, "SOURCE", "http://spdx.org/rdf/terms#fileType_source")) {
            return Stream.of(Bdio.FileSystemType.REGULAR_TEXT.toString());
        } else if (checkIdentifier(fileType, "DIRECTORY", "http://blackducksoftware.com/rdf/terms#fileType_directory")) {
            return Stream.of(Bdio.FileSystemType.DIRECTORY.toString());
        } else if (checkIdentifier(fileType, "ARCHIVE", "http://spdx.org/rdf/terms#fileType_archive")) {
            return Stream.of(Bdio.FileSystemType.DIRECTORY_ARCHIVE.toString());
        } else {
            return Stream.empty();
        }
    }

    /**
     * Returns the digest algorithm name from the BDIO 1.x algorithm identifier.
     */
    private static Optional<String> toDigestAlgorithm(String algorithm) {
        // Look for both the short form and fully qualified form since these were identifiers in BDIO 1.x
        if (checkIdentifier(algorithm, "sha1", "http://spdx.org/rdf/terms#checksumAlgorithm_sha1")) {
            return Optional.of("sha1");
        } else if (checkIdentifier(algorithm, "sha256", "http://spdx.org/rdf/terms#checksumAlgorithm_sha256")) {
            return Optional.of("sha256");
        } else if (checkIdentifier(algorithm, "md5", "http://spdx.org/rdf/terms#checksumAlgorithm_md5")) {
            return Optional.of("md5");
        } else if (algorithm.indexOf(':') <= 0) {
            return Optional.of(algorithm);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns the namespace name from the BDIO 1.x external system type identifier.
     */
    private static String toNamespace(String externalSystemTypeId) {
        // Look for both the short form and fully qualified form since these were identifiers in BDIO 1.x
        if (checkIdentifier(externalSystemTypeId, "anaconda", "http://blackducksoftware.com/rdf/terms#externalIdentifier_anaconda")) {
            return "anaconda";
        } else if (checkIdentifier(externalSystemTypeId, "bower", "http://blackducksoftware.com/rdf/terms#externalIdentifier_bower")) {
            return "bower";
        } else if (checkIdentifier(externalSystemTypeId, "cocoapods", "http://blackducksoftware.com/rdf/terms#externalIdentifier_cocoapods")) {
            return "cocoapods";
        } else if (checkIdentifier(externalSystemTypeId, "cpan", "http://blackducksoftware.com/rdf/terms#externalIdentifier_cpan")) {
            return "cpan";
        } else if (checkIdentifier(externalSystemTypeId, "goget", "http://blackducksoftware.com/rdf/terms#externalIdentifier_goget")) {
            return "golang";
        } else if (checkIdentifier(externalSystemTypeId, "maven", "http://blackducksoftware.com/rdf/terms#externalIdentifier_maven")) {
            return "maven";
        } else if (checkIdentifier(externalSystemTypeId, "npm", "http://blackducksoftware.com/rdf/terms#externalIdentifier_npm")) {
            return "npmjs";
        } else if (checkIdentifier(externalSystemTypeId, "nuget", "http://blackducksoftware.com/rdf/terms#externalIdentifier_nuget")) {
            return "nuget";
        } else if (checkIdentifier(externalSystemTypeId, "rubygems", "http://blackducksoftware.com/rdf/terms#externalIdentifier_rubygems")) {
            return "rubygems";
        } else if (checkIdentifier(externalSystemTypeId, "bdsuite", "http://blackducksoftware.com/rdf/terms#externalIdentifier_bdsuite")) {
            return "bdsuite";
        } else if (checkIdentifier(externalSystemTypeId, "bdhub", "http://blackducksoftware.com/rdf/terms#externalIdentifier_bdhub")) {
            return "bdhub";
        } else if (checkIdentifier(externalSystemTypeId, "openhub", "http://blackducksoftware.com/rdf/terms#externalIdentifier_openhub")) {
            return "openhub";
        } else {
            // Technically this wasn't supported, but it was still allowed
            return externalSystemTypeId;
        }
    }

    /**
     * Check a value that was specified as an identifier in BDIO 1.x. This means we need to consider the term name, the
     * fully qualified name and possibly some strangeness that can occur during JSON-LD compaction.
     * <p>
     * There are a few places where you have "iri#field" and "iri#field_value" which might compact as "field:_value" so
     * we need to detect that case. It is also possible that you have a vocabulary match on the IRI.
     */
    private static boolean checkIdentifier(String value, String term, String iri) {
        if (value.equals(term) || value.equals(iri) || value.equals(removePrefix(iri, VOCAB))) {
            return true;
        }

        List<String> parts = Splitter.on(":_").limit(2).splitToList(value);
        if (parts.size() == 2) {
            String valueIri = beforeFirst(iri, '#') + '#' + parts.get(0) + '_' + parts.get(1);
            if (valueIri.equals(iri)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the product identifying this code.
     */
    private static Product.Builder product() {
        return new Product.Builder()
                .simpleName(LegacyBdio1xEmitter.class)
                .implementationVersion(LegacyBdio1xEmitter.class);
    }

}
