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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URL;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.blackducksoftware.common.base.ExtraStrings;
import com.google.common.io.Resources;
import com.google.common.net.MediaType;

/**
 * A collection of constants and helpers pertaining to the BDIO specification.
 *
 * @author jgustie
 */
public class Bdio {

    /**
     * Annotation used to describe which class a property may be used on. This is different from a traditional linked
     * data "domain" is that it <em>is</em> restrictive.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Domain {
        /**
         * Flag indicating the property may be used for graph metadata.
         */
        boolean metadata() default false;

        /**
         * The list of allowed types.
         */
        Bdio.Class[] value() default {};
    }

    /**
     * Annotation used to describe the conjunctive range of an object property. That is, an object property can
     * reference <em>any</em> of the specified types.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ObjectPropertyRange {
        /**
         * The list of allowed types.
         */
        Bdio.Class[] value() default {};

        // TODO Have a boolean indicating the range is the same as the domain
        // OR Have a boolean indicating you can only reference the type the property is found on?
    }

    /**
     * Annotation used to describe the range of a literal data property.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface DataPropertyRange {
        /**
         * The literal data type for the property.
         */
        Bdio.Datatype value();
    }

    @SuppressWarnings("JavaLangClash")
    public enum Class {

        /**
         * A descriptor for a BDIO entity.
         */
        Annotation("https://blackducksoftware.github.io/bdio#Annotation"),

        /**
         * A component may also be known as a "dependency" or "artifact". Essentially it is a single BOM entry. A
         * component is the link between two projects (only one of which may be present in the current BDIO context).
         * The link may not be fully defined: only partial information about linkage may be known given the evidence at
         * hand. In addition to establishing a link between two projects, a component can contain additional metadata
         * pertaining to the details of the link: for example, the specific licensing terms used or how a project is
         * using another project (e.g. is linked project used for building, only at runtime or for testing).
         * <p>
         * A component is also a useful stand-in for a project when it is known the other project exists, but only
         * limited details are available in the current context. For example, it may be useful to create a component for
         * every GAV encountered during processing, those components may be used for linking vulnerabilities even if the
         * full project for that GAV does not exist in the current context.
         */
        Component("https://blackducksoftware.github.io/bdio#Component"),

        /**
         * A container represents a stand-alone software package, including any system software needed for execution.
         */
        Container("https://blackducksoftware.github.io/bdio#Container"),

        /**
         * A dependency can be added to a project or a component to indicate that it depends on another component.
         */
        Dependency("https://blackducksoftware.github.io/bdio#Dependency"),

        /**
         * A file is used to represent the metadata pertaining to an entry in a (possibly virtual) file system. Files
         * can used to represent any type of file system entry, including regular files, symlinks and directories. The
         * inclusion of directories is optional, i.e. you do not need to include a full directory structure, if no
         * metadata is captured for a directory, then it does not need to be included. All sizes should be represented
         * in bytes (not blocks).
         */
        File("https://blackducksoftware.github.io/bdio#File"),

        /**
         * A file collection is used to describe an arbitrary group of files that cannot be better described using
         * another more appropriate construct (like a project).
         */
        FileCollection("https://blackducksoftware.github.io/bdio#FileCollection"),

        /**
         * A license represents the specific terms under which the use of a particular project (or component) is
         * governed. A project may be linked to multiple licenses with complex relationships between them. A component
         * may similarly be linked to multiple licenses, however none of the relationships may be disjunctive: this
         * ensures that the component unambiguously references the selected license terms. Components which do not
         * reference licenses are assumed to accept the default (and unambiguous) licensing terms of the version of the
         * project they reference.
         */
        License("https://blackducksoftware.github.io/bdio#License"),

        /**
         * A grouping of licenses used when constructing complex license expressions.
         */
        LicenseGroup("https://blackducksoftware.github.io/bdio#LicenseGroup"),

        /**
         * A note represents the outcome of a specific calculation on part of a file. Notes can be simple (such as
         * inclusion of a content range), or more complex (such as the output of a processing algorithm).
         */
        Note("https://blackducksoftware.github.io/bdio#Note"),

        /**
         * A project represents a software package, typically in source form. For example, a BDIO project should be used
         * to describe each Maven POM file or each Protex project. Projects convey different metadata from "components"
         * (the later of which is just a BOM entry for a project); for example, a project may declare multiple license
         * terms to choose from whereas a component must specify exactly which terms were selected; a project may have
         * many versions, but a component references exactly one. It is always true that a project and component can
         * coexist for the same entity: for example there can be only one "log4j" project while there can be many
         * components describing the usage of "log4j" for other projects.
         */
        Project("https://blackducksoftware.github.io/bdio#Project"),

        /**
         * A repository is a collection of software metadata and possibly binary artifacts. Generally speaking a
         * repository is a collection of projects, however it may be useful to enumerate contents using component
         * objects.
         */
        Repository("https://blackducksoftware.github.io/bdio#Repository"),

        /**
         * A vulnerability represents a specific weakness in a project. It is often convenient to reference
         * vulnerabilities from specific project versions or the components linked to those versions. Vulnerabilities
         * may be found through simple look ups based on well know project metadata (e.g.
         * "this version of this project is known to have this vulnerability"); however they may also be discovered
         * through means such as static analysis of source or object code.
         */
        Vulnerability("https://blackducksoftware.github.io/bdio#Vulnerability"),

        ;

        private final String iri;

        private Class(String iri) {
            this.iri = Objects.requireNonNull(iri);
        }

        public boolean embedded() {
            return this == Class.Annotation
                    || this == Class.Dependency
                    || this == Class.LicenseGroup
                    || this == Class.Note;
        }

        @Override
        public String toString() {
            return iri;
        }
    }

    public enum ObjectProperty {

        /**
         * Indicates a component or project is affected by a particular vulnerability.
         */
        @Domain({ Class.Vulnerability })
        @ObjectPropertyRange({ Class.Component, Class.Project })
        affected("https://blackducksoftware.github.io/bdio#hasAffected", Container.unordered),

        /**
         * Points to a project's base directory.
         */
        @Domain({ Class.Container, Class.FileCollection, Class.Project, Class.Repository })
        @ObjectPropertyRange({ Class.File })
        base("https://blackducksoftware.github.io/bdio#hasBase", Container.unordered),

        /**
         * Used to indicate two objects represent the same thing and directs you to the preferred representation.
         */
        @Domain({ Class.Component, Class.Container, Class.License, Class.Project, Class.Repository, Class.Vulnerability })
        @ObjectPropertyRange({})
        canonical("https://blackducksoftware.github.io/bdio#hasCanonical", Container.single),

        /**
         * Indicates a component was declared by a specific file.
         */
        @Domain({ Class.Dependency })
        @ObjectPropertyRange({ Class.File })
        declaredBy("https://blackducksoftware.github.io/bdio#declaredBy", Container.unordered),

        /**
         * The list of dependencies.
         */
        @Domain({ Class.Component, Class.Container, Class.FileCollection, Class.Project, Class.Repository })
        @ObjectPropertyRange({ Class.Dependency })
        dependency("https://blackducksoftware.github.io/bdio#hasDependency", Container.unordered),

        /**
         * Indicates the dependent component.
         */
        @Domain({ Class.Dependency })
        @ObjectPropertyRange({ Class.Component }) // TODO Include Project in the range?
        dependsOn("https://blackducksoftware.github.io/bdio#dependsOn", Container.unordered),

        /**
         * Allows association of arbitrary comments and descriptions.
         */
        @Domain({ Class.Component, Class.Container, Class.Dependency, Class.File, Class.FileCollection, Class.License, Class.LicenseGroup, Class.Project, Class.Repository, Class.Vulnerability })
        @ObjectPropertyRange({ Class.Annotation })
        description("https://blackducksoftware.github.io/bdio#hasDescription", Container.unordered),

        /**
         * Indicates a component was discovered using evidence from a specific file.
         */
        @Domain({ Class.Dependency })
        @ObjectPropertyRange({ Class.File })
        evidence("https://blackducksoftware.github.io/bdio#hasEvidence", Container.unordered),

        /**
         * The license being used. This can be used in with other license relationships to create complex license
         * expressions.
         * <p>
         * For root objects, the license defines the terms under which the project may be licensed, for a component, the
         * license defines the term under which usage of the component is licensed.
         */
        @Domain({ Class.Component, Class.Container, Class.Dependency, Class.LicenseGroup, Class.Project })
        @ObjectPropertyRange({ Class.License, Class.LicenseGroup })
        license("https://blackducksoftware.github.io/bdio#hasLicense", Container.single),

        /**
         * A simultaneously required license being used. This can be used in with other license relationships to create
         * complex license expressions.
         */
        @Domain({ Class.Component, Class.Container, Class.Dependency, Class.LicenseGroup, Class.Project })
        @ObjectPropertyRange({ Class.License, Class.LicenseGroup })
        licenseConjunctive("https://blackducksoftware.github.io/bdio#haslicenseConjunctive", Container.ordered),

        /**
         * A choice of licenses being used. This can be used in with other license relationships to create complex
         * license expressions.
         */
        @Domain({ Class.Component, Class.Container, Class.Dependency, Class.LicenseGroup, Class.Project })
        @ObjectPropertyRange({ Class.License, Class.LicenseGroup })
        licenseDisjunctive("https://blackducksoftware.github.io/bdio#hasLicenseDisjunctive", Container.ordered),

        /**
         * Identifies an exception to the terms of the license.
         */
        @Domain({ Class.License })
        @ObjectPropertyRange({ Class.License })
        licenseException("https://blackducksoftware.github.io/bdio#hasLicenseException", Container.single),

        /**
         * The minimal license being used. This can be used in with other license relationships to create complex
         * license expressions.
         */
        @Domain({ Class.Component, Class.Container, Class.Dependency, Class.LicenseGroup, Class.Project })
        @ObjectPropertyRange({ Class.License })
        licenseOrLater("https://blackducksoftware.github.io/bdio#hasLicenseOrLater", Container.single),

        /**
         * Lists the notes applicable to a file.
         */
        @Domain({ Class.File })
        @ObjectPropertyRange({ Class.Note })
        note("https://blackducksoftware.github.io/bdio#hasNote", Container.ordered),

        /**
         * Points to a file's parent. Typically this relationship is implicit; producers do not need to supply it.
         */
        @Domain({ Class.File })
        @ObjectPropertyRange({ Class.File })
        parent("https://blackducksoftware.github.io/bdio#hasParent", Container.single),

        /**
         * Links a project version to it's previous version.
         */
        @Domain({ Class.Project })
        @ObjectPropertyRange({ Class.Project })
        previousVersion("https://blackducksoftware.github.io/bdio#hasPreviousVersion", Container.single),

        /**
         * Establishes that a project has a subproject or module relationship to another project.
         */
        @Domain({ Class.Project })
        @ObjectPropertyRange({ Class.Project })
        subproject("https://blackducksoftware.github.io/bdio#hasSubproject", Container.unordered),

        ;

        private final String iri;

        private final Container container;

        private ObjectProperty(String iri, Container container) {
            this.iri = Objects.requireNonNull(iri);
            this.container = Objects.requireNonNull(container);
        }

        @Override
        public String toString() {
            return iri;
        }

        public Container container() {
            return container;
        }
    }

    public enum DataProperty {

        /**
         * The URL used to obtain additional details about the build environment.
         */
        @Domain(metadata = true)
        @DataPropertyRange(Datatype.Default) // TODO Change type to "AnyURI"?
        buildDetails("https://blackducksoftware.github.io/bdio#hasBuildDetails", Container.single),

        /**
         * The build number captured from the build environment.
         */
        @Domain(metadata = true)
        @DataPropertyRange(Datatype.Default)
        buildNumber("https://blackducksoftware.github.io/bdio#hasBuildNumber", Container.single),

        /**
         * The size (in bytes) of a file.
         */
        @Domain({ Class.File })
        @DataPropertyRange(Datatype.Long)
        // The name "byte count" does not conflict with "size" or "length" and is less ambiguous
        byteCount("https://blackducksoftware.github.io/bdio#hasByteCount", Container.single),

        /**
         * A comment used to annotate a BDIO entity.
         */
        @Domain({ Class.Annotation })
        @DataPropertyRange(Datatype.Default)
        comment("https://blackducksoftware.github.io/bdio#hasComment", Container.single),

        /**
         * The content type of a file.
         */
        @Domain({ Class.File })
        @DataPropertyRange(Datatype.ContentType)
        contentType("https://blackducksoftware.github.io/bdio#hasContentType", Container.single),

        /**
         * The namespace specific base context used to resolve a locator. Typically this is just a URL, however any
         * specification understood by the namespace specific resolver is acceptable.
         */
        @Domain({ Class.Component, Class.License, Class.Project, Class.Vulnerability })
        @DataPropertyRange(Datatype.Default)
        // TODO Is this a good name? Back to repository?
        context("https://blackducksoftware.github.io/bdio#hasContext", Container.single),

        /**
         * The date and time creation of an entity occurred.
         */
        @Domain(value = { Class.Annotation, Class.File, Class.Vulnerability }, metadata = true)
        @DataPropertyRange(Datatype.DateTime)
        creationDateTime("https://blackducksoftware.github.io/bdio#hasCreationDateTime", Container.single),

        /**
         * The user and/or host who created the BDIO document. The host portion must be prefixed with an "@" sign.
         */
        @Domain(value = { Class.Annotation }, metadata = true)
        @DataPropertyRange(Datatype.Default)
        creator("https://blackducksoftware.github.io/bdio#hasCreator", Container.single),

        /**
         * The character encoding of a file. It is required that producers store the encoding independent of the content
         * type's parameters.
         */
        @Domain({ Class.File })
        @DataPropertyRange(Datatype.Default)
        encoding("https://blackducksoftware.github.io/bdio#hasEncoding", Container.single),

        /**
         * The file system type of file. Represented as a content-type-like string indicating the type file.
         *
         * @see Bdio.FileSystemType
         */
        @Domain({ Class.File })
        @DataPropertyRange(Datatype.Default)
        fileSystemType("https://blackducksoftware.github.io/bdio#hasFileSystemType", Container.single),

        /**
         * The fingerprints of a file.
         */
        @Domain({ Class.File })
        @DataPropertyRange(Datatype.Digest)
        fingerprint("https://blackducksoftware.github.io/bdio#hasFingerprint", Container.unordered),

        /**
         * The homepage associated with the entity.
         */
        @Domain({ Class.Component, Class.License, Class.Project, Class.Vulnerability })
        @DataPropertyRange(Datatype.Default) // TODO Change type to "AnyURI"?
        // TODO Other URLs: issues, source, wiki, etc.?
        homepage("https://blackducksoftware.github.io/bdio#hasHomepage", Container.unordered),

        /**
         * The namespace specific locator for a component. Also known as an "external identifier".
         */
        @Domain({ Class.Component, Class.License, Class.Project, Class.Vulnerability })
        @DataPropertyRange(Datatype.Default)
        identifier("https://blackducksoftware.github.io/bdio#hasIdentifier", Container.single),

        /**
         * The date and time a file was last modified.
         */
        @Domain({ Class.File, Class.Vulnerability })
        @DataPropertyRange(Datatype.DateTime)
        lastModifiedDateTime("https://blackducksoftware.github.io/bdio#hasLastModifiedDateTime", Container.single),

        /**
         * The symbolic link target of a file.
         */
        @Domain({ Class.File })
        @DataPropertyRange(Datatype.Default)
        linkPath("https://blackducksoftware.github.io/bdio#hasLinkPath", Container.single),

        /**
         * The display name of the entity.
         */
        // TODO Use JSON-LD Container.language to support multi-language names?
        @Domain(value = { Class.Component, Class.License, Class.Project, Class.Repository, Class.Vulnerability }, metadata = true)
        @DataPropertyRange(Datatype.Default)
        name("https://blackducksoftware.github.io/bdio#hasName", Container.single),

        /**
         * The namespace a component exists in. Also known as a "forge" or "system type", this defines how many
         * different fields should be interpreted (e.g. identifiers, versions and scopes are defined within a particular
         * namespace).
         * <p>
         * Note that namespace values are not part of the BDIO specification. There are BDIO recommendations,
         * however it is ultimately up to the producer and consumer of the BDIO data to handshake on the appropriate
         * rules.
         */
        @Domain({ Class.Component, Class.Container, Class.Dependency, Class.License, Class.Project, Class.Repository, Class.Vulnerability })
        @DataPropertyRange(Datatype.Default)
        namespace("https://blackducksoftware.github.io/bdio#hasNamespace", Container.single),

        /**
         * The hierarchical path of a file relative to the base directory.
         */
        @Domain({ Class.File })
        @DataPropertyRange(Datatype.Default)
        path("https://blackducksoftware.github.io/bdio#hasPath", Container.single),

        /**
         * The platform (e.g. operating system) the data was captured for. This is generally lower level information
         * then can be found in the resolver, e.g. while the resolve might contain tool specific specifiers, the
         * platform would be used to describe the operating system running the tool.
         */
        @Domain(value = { Class.Component, Class.Container, Class.File, Class.Project, Class.Vulnerability }, metadata = true)
        @DataPropertyRange(Datatype.Products)
        platform("https://blackducksoftware.github.io/bdio#hasPlatform", Container.single),

        /**
         * The tool which published the BDIO document.
         */
        @Domain(metadata = true)
        @DataPropertyRange(Datatype.Products)
        publisher("https://blackducksoftware.github.io/bdio#hasPublisher", Container.single),

        /**
         * The ranges of file content a note applies to. Multiple ranges can be specified, however the units must be
         * distinct (e.g. "bytes" and "chars").
         */
        @Domain({ Class.Dependency, Class.Note })
        @DataPropertyRange(Datatype.ContentRange)
        range("https://blackducksoftware.github.io/bdio#hasRange", Container.unordered),

        /**
         * The namespace specific version range that resulted in a component being included.
         */
        @Domain({ Class.Component })
        @DataPropertyRange(Datatype.Default)
        requestedVersion("https://blackducksoftware.github.io/bdio#hasRequestedVersion", Container.single),

        /**
         * The tool which resolved the namespace specific locator.
         */
        @Domain({ Class.Component, Class.License, Class.Project, Class.Vulnerability })
        @DataPropertyRange(Datatype.Products)
        resolver("https://blackducksoftware.github.io/bdio#hasResolver", Container.single),

        /**
         * The statement of rights for a specific file. Generally this will be a copyright statement like "Copyright (C)
         * 2016 Black Duck Software Inc.".
         */
        @Domain({ Class.Note })
        @DataPropertyRange(Datatype.Default)
        rights("https://blackducksoftware.github.io/bdio#hasRights", Container.single),

        /**
         * The namespace specific scope of a dependency as determined by the resolution tool used to define the
         * dependency. For example, if a dependency came from an npm package's "devDependencies" field, then the
         * scope should be "devDependencies".
         */
        @Domain({ Class.Dependency })
        @DataPropertyRange(Datatype.Default)
        scope("https://blackducksoftware.github.io/bdio#hasScope", Container.single),

        /**
         * The SCM branch name from the build environment.
         */
        @Domain(metadata = true)
        @DataPropertyRange(Datatype.Default)
        sourceBranch("https://blackducksoftware.github.io/bdio#hasSourceBranch", Container.single),

        /**
         * The URI representing the SCM location from the build environment.
         */
        @Domain(metadata = true)
        @DataPropertyRange(Datatype.Default)
        sourceRepository("https://blackducksoftware.github.io/bdio#hasSourceRepository", Container.single),

        /**
         * The SCM revision identifier from the build environment.
         */
        @Domain(metadata = true)
        @DataPropertyRange(Datatype.Default)
        sourceRevision("https://blackducksoftware.github.io/bdio#hasSourceRevision", Container.single),

        /**
         * The SCM tag name from the build environment.
         */
        @Domain(metadata = true)
        @DataPropertyRange(Datatype.Default)
        sourceTag("https://blackducksoftware.github.io/bdio#hasSourceTag", Container.single),

        /**
         * The name of the vendor who provides a project or component.
         */
        @Domain({ Class.Component, Class.Project })
        @DataPropertyRange(Datatype.Default)
        vendor("https://blackducksoftware.github.io/bdio#hasVendor", Container.single),

        /**
         * The display version of the entity. Must reference a single version.
         */
        @Domain({ Class.Component, Class.Project })
        @DataPropertyRange(Datatype.Default)
        version("https://blackducksoftware.github.io/bdio#hasVersion", Container.single),

        // TODO Container information: repo, tag, id, etc.

        ;

        private final String iri;

        private final Container container;

        private DataProperty(String iri, Container container) {
            this.iri = Objects.requireNonNull(iri);
            this.container = Objects.requireNonNull(container);
        }

        @Override
        public String toString() {
            return iri;
        }

        public Container container() {
            return container;
        }
    }

    public enum Datatype {

        /**
         * An HTTP Content Range string.
         */
        ContentRange("https://blackducksoftware.github.io/bdio#ContentRange"),

        /**
         * An Http Content Type string.
         */
        ContentType("https://blackducksoftware.github.io/bdio#ContentType"),

        /**
         * ISO date/time string.
         */
        DateTime("http://www.w3.org/2001/XMLSchema#dateTime"),

        /**
         * Unrestricted string value.
         */
        Default(""),

        /**
         * A string that encapsulates an algorithm name and an unrestricted digest value.
         */
        Digest("https://blackducksoftware.github.io/bdio#Digest"),

        /**
         * Natural number.
         */
        Long("http://www.w3.org/2001/XMLSchema#long"),

        /**
         * An HTTP User Agent string.
         */
        Products("https://blackducksoftware.github.io/bdio#Products"),

        // TODO AnyURI("http://www.w3.org/2001/XMLSchema#anyURI")
        // TODO Boolean("http://www.w3.org/2001/XMLSchema#boolean")
        // TODO Language("http://www.w3.org/2001/XMLSchema#language")
        // TODO UUID (or use anyURI with 'urn:uuid:')?
        // TODO Token (like HTTP, not XML)?
        // TODO SPDX license expression? Do they have one already?

        ;

        private final String iri;

        private Datatype(String iri) {
            this.iri = Objects.requireNonNull(iri);
        }

        @Override
        public String toString() {
            return iri;
        }
    }

    public enum Container {
        single, ordered, unordered
    }

    public enum FileSystemType {
        /**
         * A regular file, typically of unknown or binary content.
         */
        REGULAR("regular"),

        /**
         * A regular file with known text content. Should be accompanied by an encoding.
         */
        REGULAR_TEXT("regular/text"),

        /**
         * A directory entry which may or may not contain children.
         */
        DIRECTORY("directory"),

        /**
         * An archive which may or may not contain children. Can also be treated as a regular file.
         */
        DIRECTORY_ARCHIVE("directory/archive"),

        /**
         * A symbolic link. Should be accompanied by a link target.
         */
        SYMLINK("symlink"),

        /**
         * A block device, like a drive.
         */
        OTHER_DEVICE_BLOCK("other/device/block"),

        /**
         * A character device, like a terminal.
         */
        OTHER_DEVICE_CHARACTER("other/device/character"),

        /**
         * A door used for interprocess communication.
         */
        OTHER_DOOR("other/door"),

        /**
         * A named pipe.
         */
        OTHER_PIPE("other/pipe"),

        /**
         * A socket.
         */
        OTHER_SOCKET("other/socket"),

        /**
         * A whiteout, or file removal in a layered file system.
         */
        OTHER_WHITEOUT("other/whiteout"),
        ;

        private final String value;

        private final boolean regularFile;

        private final boolean directory;

        private final boolean symbolicLink;

        private final boolean other;

        private FileSystemType(String value) {
            this.value = Objects.requireNonNull(value);

            String type = ExtraStrings.beforeFirst(value, '/');
            regularFile = type.equals("regular");
            directory = type.equals("directory");
            symbolicLink = type.equals("symlink");
            other = type.equals("other");
        }

        public boolean isRegularFile() {
            return regularFile;
        }

        public boolean isDirectory() {
            return directory;
        }

        public boolean isSymbolicLink() {
            return symbolicLink;
        }

        public boolean isOther() {
            return other;
        }

        /**
         * Returns the file system type represented by the specified object.
         */
        public static FileSystemType from(@Nullable Object obj) {
            if (obj instanceof FileSystemType) {
                return (FileSystemType) obj;
            } else {
                for (FileSystemType fileSystemType : values()) {
                    if (fileSystemType.value.equals(obj)) {
                        return fileSystemType;
                    }
                }
                throw new IllegalArgumentException("unknown file system type: " + obj);
            }
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum ContentType {

        /**
         * The content type used when BDIO data is represented using JSON-LD. The context will either be referenced as a
         * remote document or will be explicitly included in the content body. Note that only UTF-8 character encoding
         * is allowed.
         */
        JSONLD("application/ld+json", "jsonld"),

        /**
         * The content type used when BDIO data is represented using plain JSON and the context is specified externally
         * (e.g. using the {@code Link} header). Note that only UTF-8 character encoding is allowed.
         */
        JSON("application/json", "json"),

        /**
         * The content type used when BDIO data is represented using plain JSON that should be interpreted using the
         * default BDIO context.
         */
        BDIO_JSON("application/vnd.blackducksoftware.bdio+json", "json"),

        /**
         * The content type used when BDIO data is represented as self-contained JSON-LD stored in a ZIP archive.
         */
        BDIO_ZIP("application/vnd.blackducksoftware.bdio+zip", "bdio"),

        ;

        private final String mediaType;

        private final String extension;

        private ContentType(String mediaType, String extension) {
            this.mediaType = Objects.requireNonNull(mediaType);
            this.extension = Objects.requireNonNull(extension);
        }

        @Override
        public String toString() {
            return mediaType;
        }

        /**
         * Returns the (non-unique) file name extension associated with this content type.
         */
        public String extension() {
            return extension;
        }

        /**
         * Helper method so that media types can be easily transformed to library specific representations. For example,
         * if a {@code MediaType} class has a constructor that accepts a {@code String}, then you can use
         * {@code BDIO_V2_ZIP.as(MediaType::new)}.
         */
        public <R> R as(Function<String, R> f) {
            return f.apply(mediaType);
        }

        /**
         * Returns the BDIO content type corresponding to the supplied media type.
         */
        public static ContentType forMediaType(String input) {
            // TODO Switch from Guava to Magpie when possible
            MediaType mediaType = MediaType.parse(input);
            for (Bdio.ContentType contentType : Bdio.ContentType.values()) {
                if (contentType.as(MediaType::parse).is(mediaType)) {
                    return contentType;
                }
            }
            throw new IllegalArgumentException("unsupported media type: " + input);
        }

        /**
         * Returns the BDIO content type corresponding to the supplied file name.
         */
        public static ContentType forFileName(String input) {
            for (Bdio.ContentType contentType : Bdio.ContentType.values()) {
                if (input.endsWith("." + contentType.extension)) {
                    return contentType;
                }
            }
            throw new IllegalArgumentException("unsupported file extension: " + ExtraStrings.afterLast(input, '/'));
        }

    }

    public enum Context {

        VERSION_2_0("https://blackducksoftware.github.io/bdio/2.0.0", "bdio-context-2.0.jsonld"),
        VERSION_1_1_1("http://blackducksoftware.com/rdf/terms/1.1.1", "bdio-context-1.1.1.jsonld"),
        VERSION_1_1("http://blackducksoftware.com/rdf/terms/1.1.0", "bdio-context-1.1.jsonld"),
        VERSION_1_0("http://blackducksoftware.com/rdf/terms/1.0.0", "bdio-context-1.0.jsonld"),
        DEFAULT("https://blackducksoftware.github.io/bdio", VERSION_2_0.resourceName);

        private final String iri;

        private final String resourceName;

        private Context(String iri, String resourceName) {
            this.iri = Objects.requireNonNull(iri);
            this.resourceName = Objects.requireNonNull(resourceName);
        }

        @Override
        public String toString() {
            return iri;
        }

        /**
         * Returns the class loader resource used to access the context definition.
         */
        URL resourceUrl() {
            return Resources.getResource(Bdio.class, resourceName);
        }

        /**
         * Returns the context for a given specification version.
         */
        public static Context forSpecVersion(String specVersion) {
            switch (specVersion) {
            case "": // v0 == v1.0.0
            case "1.0.0":
                return Context.VERSION_1_0;
            case "1.1.0":
                return Context.VERSION_1_1;
            case "1.1.1":
                return Context.VERSION_1_1_1;
            case "2.0.0":
                return Context.VERSION_2_0;
            default:
                throw new IllegalArgumentException("unknown BDIO specification version: " + specVersion);
            }
        }
    }

    /**
     * The maximum size in bytes for an expanded BDIO JSON-LD entry.
     */
    public static final int MAX_ENTRY_SIZE = 16 * 1024 * 1024;

    /**
     * Returns an entry name to use for a file inside the BDIO Zip file. Each data entry consists of an equivalently
     * named JSON-LD graph. The only requirement from the BDIO specification is that the returned value end with
     * a ".jsonld" suffix.
     */
    public static String dataEntryName(int entryNumber) {
        if (entryNumber < -1) {
            throw new IllegalArgumentException("entryNumber must greater then or equal to -1: " + entryNumber);
        } else if (entryNumber < 0) {
            // The header basically contains an empty named graph with metadata for that graph. Although not required by
            // the specification, this optimization may help reduce the time it takes consumers to obtain the data
            // necessary to display meaningful status messages.
            return "bdio-header.jsonld";
        } else {
            // 2 digits still gives us ~1.5 GB, right?
            return String.format("bdio-entry-%02d.jsonld", entryNumber);
        }
    }

    /**
     * Checks to see if the supplied entry name represents BDIO data.
     */
    public static boolean isDataEntryName(String name) {
        return name.endsWith(".jsonld");
    }

    private Bdio() {
        assert false;
    }
}
