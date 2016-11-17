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
package com.blackducksoftware.bdio;

import java.util.Objects;

/**
 * A collection of constants and helpers pertaining to the BDIO specification.
 *
 * @author jgustie
 */
public class Bdio {

    public enum Class {

        /**
         * The project is the primary subject matter of any BDIO data, projects should be used to describe anything that
         * was created or managed in the context of the BDIO document. For example, a BDIO project should be used to
         * describe each Maven POM file or each Protex project. Projects convey different metadata from
         * "components" (the later of which is just a BOM entry for a project); for example, a project may
         * declare multiple license terms to choose from whereas a component must specify exactly which terms were
         * selected; a project may have many versions, but a component references exactly one. It is always
         * true that a project and component can coexist for the same entity: for example there can be only one "log4j"
         * project while there can be many components describing the usage of "log4j" for other projects.
         */
        Project("http://blackducksoftware.com/rdf/terms#Project"),

        /**
         * A version is specific to a project, it defines the metadata for a project at a specific instant in time. Any
         * aspect of a project can re-defined in terms of a specific version as any aspect of the project can change
         * over time (including websites, source control locations, even names). Versions should also be navigable as a
         * tree: at some point (project inception) there is a single version. Note that components do not have versions,
         * instead they reference a specific version. If no versions are present for a project in a BDIO
         * dataset, it is assumed the project defines the current version.
         */
        Version("http://blackducksoftware.com/rdf/terms#Version"),

        /**
         * A component may also known as a "dependency" or "artifact". Essentially it is a single BOM entry. A component
         * is the link between two projects (only one of which may be present in the current BDIO context). The link may
         * not be fully defined: only partial information about linkage may be known given the evidence at hand. In
         * addition to establishing a link between two projects, a component can contain additional metadata pertaining
         * to the details of the link: for example, the specific licensing terms used or how a project is using another
         * project (e.g. is linked project used for building, only at runtime or for testing).
         * <p>
         * A component is also a useful stand-in for a project when it is known the other project exists, but only
         * limited details are available in the current context. For example, it may be useful to create a component for
         * every GAV encountered during processing, those components may be used for linking vulnerabilities even if the
         * full project for that GAV does not exist in the current context.
         */
        Component("http://blackducksoftware.com/rdf/terms#Component"),

        /**
         * A license represents the specific terms under which the use of a particular project (or component) is
         * governed. A project may be linked to multiple licenses with complex relationships between them. A component
         * may similarly be linked to multiple licenses, however none of the relationships may be disjunctive: this
         * ensures that the component unambiguously references the selected license terms. Components which do not
         * reference licenses are assumed to accept the default (and unambiguous) licensing terms of the version of the
         * project they reference.
         */
        License("http://blackducksoftware.com/rdf/terms#License"),

        /**
         * A file is used to represent the metadata pertaining to an entry in a (possibly virtual) file system. Files
         * can used to represent any type of file system entry, including regular files, symlinks and directories. The
         * inclusion of directories is optional, i.e. you do not need to include a full directory structure, if no
         * metadata is captured for a directory, then it does not need to be included. All sizes should be represented
         * in bytes (not blocks). For file systems with efficient calculation of directory sizes, those sizes can be
         * included for directory entries.
         */
        File("http://blackducksoftware.com/rdf/terms#File"),
        // TODO Should we have classes for Directory, RegularFile, etc?

        /**
         * While every object will have a unique identifier in the current BDIO context, those identifiers (the value of
         * the JSON-LD @id field) are opaque and cannot be considered for anything other then equality tests when
         * resolving link references. It then becomes necessary to include identifiers from other "external" contexts.
         * These identifiers are specific to the context in which they are used and may have their own formatting
         * constraints or additional references that are necessary for resolution.
         */
        Identifier("http://blackducksoftware.com/rdf/terms#Identifier"),

        /**
         * It is not generally desirable to include complete file contents in a BDIO document, instead file identity can
         * be established using a fingerprint of the file. Typically, the fingerprint will be a SHA-1 hash of the file
         * contents. The fingerprint object is meant to include both the value of the fingerprint and the algorithm used
         * to compute it.
         */
        // TODO Should we even have this? Probably not, just use a datatype
        Fingerprint("http://blackducksoftware.com/rdf/terms#Fingerprint"),

        /**
         * A vulnerability represents a specific weakness in a project. It is often convenient to reference
         * vulnerabilities from specific project versions or the components linked to those versions. Vulnerabilities
         * may be found through simple look ups based on well know project metadata (e.g.
         * "this version of this project is known to have this vulnerability"); however they may also be discovered
         * through means such as static analysis of source or object code.
         */
        Vulnerability("http://blackducksoftware.com/rdf/terms#Vulnerability"),

        // TODO ContinuousIntegrationEnvironment

        ;

        private final String iri;

        private Class(String iri) {
            this.iri = Objects.requireNonNull(iri);
        }

        @Override
        public String toString() {
            return iri;
        }

        /**
         * In a JSON-LD framing context, should this class be embedded.
         */
        public boolean embed() {
            return this == Class.Identifier || this == Class.Fingerprint;
        }
    }

    public enum ObjectProperty {

        /**
         * Establishes that a project has a subproject or module relationship to another project.
         */
        // Domain = Project; Range = Project
        subproject("http://blackducksoftware.com/rdf/terms#hasSubproject")

        // TODO hasCurrentVersion
        // TODO hasPreviousVersion
        // TODO hasBase hasRoot

        // TODO hasIdentifier
        // TODO hasFingerprint

        ;

        private final String iri;

        private ObjectProperty(String iri) {
            this.iri = Objects.requireNonNull(iri);
        }

        @Override
        public String toString() {
            return iri;
        }
    }

    public enum DataProperty {

        /**
         * The time at which the BDIO document was created. This property should be specified for the named graph.
         */
        // TODO Incorporate "time" or "datetime" or "timestamp" into the name?
        creation("http://blackducksoftware.com/rdf/terms#hasCreation", Datatype.DateTime, Container.single),

        /**
         * The user who created the BDIO document. This property should be specified for the named graph.
         */
        // TODO Is this too confusing with "creation"?
        creator("http://blackducksoftware.com/rdf/terms#hasCreator", Datatype.Default, Container.single),

        /**
         * The tool which produced the BDIO document. This should be specified using the same format as an HTTP user
         * agent. This property should be specified for the named graph.
         */
        // TODO Do we need a datatype?
        producer("http://blackducksoftware.com/rdf/terms#hasProducer", Datatype.Default, Container.single),

        // TODO Continuous Integration environment
        // repository, branch, buildNumber, build[Url], etc.
        // buildName (like Jenkins "BUILD_TAG")

        /**
         * The display name of the entity.
         */
        name("http://blackducksoftware.com/rdf/terms#hasName", Datatype.Default, Container.single),

        /**
         * The display version of the entity.
         */
        // Domain = Version || Component?
        version("http://blackducksoftware.com/rdf/terms#hasVersion", Datatype.Default, Container.single),

        /**
         * The size (in bytes) of a file.
         */
        // The name "byte count" does not conflict with "size" or "length" and is less ambiguous
        // Domain = File
        byteCount("http://blackducksoftware.com/rdf/terms#hasByteCount", Datatype.Long, Container.single),

        // TODO copyrightYear
        // TODO rightsHolder
        // TODO contentType
        // TODO algorithm XXX
        // TODO fingerprintValue XXX
        // TODO hid/path?

        ;

        private final String iri;

        private final Datatype type;

        private final Container container;

        private DataProperty(String iri, Datatype type, Container container) {
            this.iri = Objects.requireNonNull(iri);
            this.type = Objects.requireNonNull(type);
            this.container = Objects.requireNonNull(container);
        }

        @Override
        public String toString() {
            return iri;
        }

        public Datatype type() {
            return type;
        }

        public Container container() {
            return container;
        }
    }

    public enum Datatype {

        Default(""),
        DateTime("http://www.w3.org/2001/XMLSchema#dateTime"),
        Long("http://www.w3.org/2001/XMLSchema#long"),

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

    public enum Context {
        DEFAULT("http://blackducksoftware.com/rdf/terms", "bdio-context-2.0.jsonld"),
        VERSION_2_0("http://blackducksoftware.com/rdf/terms/2.0.0", "bdio-context-2.0.jsonld"),
        VERSION_1_1("http://blackducksoftware.com/rdf/terms/1.1.0", "bdio-context-1.1.jsonld"),
        VERSION_1_0("http://blackducksoftware.com/rdf/terms/1.0.0", "bdio-context-1.0.jsonld");

        private final String iri;

        private final String resourceName;

        private Context(String iri, String resourceName) {
            this.iri = Objects.requireNonNull(iri);
            this.resourceName = Objects.requireNonNull(resourceName);
        }

        public String resourceName() {
            return resourceName;
        }

        @Override
        public String toString() {
            return iri;
        }
    }

    /**
     * The maximum size in bytes for an expanded BDIO JSON-LD entry.
     */
    public static final int MAX_ENTRY_SIZE = 16 * 1024 * 1024;

    // TODO We should expose an "average node size" so we can decide how many nodes to buffer

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

    private Bdio() {
        assert false;
    }
}
