# Abstract
Black Duck I/O defines a vocabulary and mechanism by which information about software can be transferred between solutions inside and outside of the Black Duck ecosystem.

# Introduction
TODO

## Actors
The actors in a BDIO system are the "producers", "publishers", "consumers" and "processors". Producers and consumers are concerned with the syntactical structure (e.g. "is the document well formatted according to the specification?"); while publishers and processors are concerned with the semantics of the data itself (e.g. "are all the file nodes connected to a project?").

# Model

## Classes

`Component`
: `https://blackducksoftware.github.io/bdio#Component`
: A component may also be known as a "dependency" or "artifact". Essentially it is a single BOM entry. A component is the link between two projects (only one of which may be present in the current BDIO context). The link may not be fully defined: only partial information about linkage may be known given the evidence at hand. In addition to establishing a link between two projects, a component can contain additional metadata pertaining to the details of the link: for example, the specific licensing terms used or how a project is using another project (e.g. is linked project used for building, only at runtime or for testing).
:
:A component is also a useful stand-in for a project when it is known the other project exists, but only limited details are available in the current context. For example, it may be useful to create a component for every GAV encountered during processing, those components may be used for linking vulnerabilities even if the full project for that GAV does not exist in the current context.

`Container`
: `https://blackducksoftware.github.io/bdio#Container
: A container represents a stand-alone software package, including any system software needed for execution.

`Dependency`
: `https://blackducksoftware.github.io/bdio#Dependency`
: A dependency can be added to a project or a component to indicate that it depends on another component.

`File`
: `https://blackducksoftware.github.io/bdio#File`
: A file is used to represent the metadata pertaining to an entry in a (possibly virtual) file system. Files can used to represent any type of file system entry, including regular files, symlinks and directories. The inclusion of directories is optional, i.e. you do not need to include a full directory structure, if no metadata is captured for a directory, then it does not need to be included. All sizes should be represented in bytes (not blocks).

`FileCollection`
: `https://blackducksoftware.github.io/bdio#FileCollection`
:  A file collection is used to describe an arbitrary group of files that cannot be better described using another more appropriate construct (like a project).

`License`
: `https://blackducksoftware.github.io/bdio#License`
: A license represents the specific terms under which the use of a particular project (or component) is governed. A project may be linked to multiple licenses with complex relationships between them. A component may similarly be linked to multiple licenses, however none of the relationships may be disjunctive: this ensures that the component unambiguously references the selected license terms. Components which do not reference licenses are assumed to accept the default (and unambiguous) licensing terms of the version of the project they reference.

`Note`
: `https://blackducksoftware.github.io/bdio#Note`
: A note represents the outcome of a specific calculation on part of a file. Notes can be simple (such as inclusion of a content range), or more complex (such as the output of a processing algorithm).

`Project`
: `https://blackducksoftware.github.io/bdio#Project`
:  A project represents a software package, typically in source form. For example, a BDIO project should be used to describe each Maven POM file or each Protex project. Projects convey different metadata from "components" (the later of which is just a BOM entry for a project); for example, a project may declare multiple license terms to choose from whereas a component must specify exactly which terms were selected; a project may have many versions, but a component references exactly one. It is always true that a project and component can coexist for the same entity: for example there can be only one "log4j" project while there can be many components describing the usage of "log4j" for other projects.

`Repository`
: `https://blackducksoftware.github.io/bdio#Repository`
: A repository is a collection of software metadata and possibly binary artifacts. Generally speaking a repository is a collection of projects, however it may be useful to enumerate contents using component objects.

`Vulnerability`
: `https://blackducksoftware.github.io/bdio#Vulnerability`
: A vulnerability represents a specific weakness in a project. It is often convenient to reference vulnerabilities from specific project versions or the components linked to those versions. Vulnerabilities may be found through simple look ups based on well know project metadata (e.g. "this version of this project is known to have this vulnerability"); however they may also be discovered through means such as static analysis of source or object code.

## Object Properties

`base`
: `https://blackducksoftware.github.io/bdio#hasBase`
: Points to a project's base directory.

`declaredBy`
: `https://blackducksoftware.github.io/bdio#declaredBy`
: Indicates a component was declared by a specific file.

`dependency`
: `https://blackducksoftware.github.io/bdio#hasDependency`
: The list of dependencies.

`dependsOn`
: `https://blackducksoftware.github.io/bdio#dependsOn`
: Indicates the dependent component.

`note`
: `https://blackducksoftware.github.io/bdio#hasNote`
:  Lists the notes applicable to a file.

`parent`
: `https://blackducksoftware.github.io/bdio#hasParent`
: Points to a file's parent. Typically this relationship is implicit; producers do not need to supply it.

`previousVersion`
: `https://blackducksoftware.github.io/bdio#hasPreviousVersion`
: Links a project version to it's previous version.

`subproject`
: `https://blackducksoftware.github.io/bdio#hasSubproject`
: Establishes that a project has a subproject or module relationship to another project.

## Data Properties

`buildDetails`
: `https://blackducksoftware.github.io/bdio#hasBuildDetails`
: The URL used to obtain additional details about the build environment.

`buildNumber`
: `https://blackducksoftware.github.io/bdio#hasBuildNumber`
: The build number captured from the build environment.

`byteCount`
: `https://blackducksoftware.github.io/bdio#hasByteCount`
: The size (in bytes) of a file.

`contentType`
: `https://blackducksoftware.github.io/bdio#hasContentType`
: The content type of a file.

`context`
: `https://blackducksoftware.github.io/bdio#hasContext`
: The namespace specific base context used to resolve a locator. Typically this is just a URL, however any specification understood by the namespace specific resolver is acceptable.

`copyrightYear`
: `https://blackducksoftware.github.io/bdio#hasCopyrightYear`
: The year or range of years of the applicable copyright for a specific file.

`creationDateTime`
: `https://blackducksoftware.github.io/bdio#hasCreationDateTime`
: The time at which the BDIO document was created. This property should be specified for the named graph.

`creator`
: `https://blackducksoftware.github.io/bdio#hasCreator`
: The user who created the BDIO document. This property should be specified for the named graph.

`encoding`
: `https://blackducksoftware.github.io/bdio#hasEncoding`
: The character encoding of a file. It is required that producers store the encoding independent of the content type's parameters.

`fileSystemType`
: `https://blackducksoftware.github.io/bdio#hasFileSystemType`
: The file system type of file. Represented as a content-type-like string indicating the type file.

`fingerprint`
: `https://blackducksoftware.github.io/bdio#hasFingerprint`
: The fingerprints of a file.

`homepage`
: `https://blackducksoftware.github.io/bdio#hasHomepage`
: The homepage associated with the entity.

`identifier`
: `https://blackducksoftware.github.io/bdio#hasIdentifier`
: The namespace specific locator for a component. Also known as an "external identifier".

`license`
: `https://blackducksoftware.github.io/bdio#hasLicense`
: The license expression describing either the allowed (in the case of a project) or effective license(s) (in the case of a component).
:
: Note that there is not a specific object property creating a relationship between projects/components and licenses: this expression may reference an otherwise disconnected license within the BDIO document if necessary.

`linkPath`
: `https://blackducksoftware.github.io/bdio#hasLinkPath`
: The symbolic link target of a file.

`name`
: `https://blackducksoftware.github.io/bdio#hasName`
: The display name of the entity.

`namespace`
: `https://blackducksoftware.github.io/bdio#hasNamespace`
: The namespace a component exists in. Also known as a "forge" or "system type", this defines how many different fields should be interpreted (e.g. identifiers, versions and scopes are defined within a particular namespace).
:
: Note that namespace values are not part of the BDIO specification. There are BDIO recommendations, however it is ultimately up to the producer and consumer of the BDIO data to handshake on the appropriate rules.

`path`
: `https://blackducksoftware.github.io/bdio#hasPath`
: The hierarchical path of a file relative to the base directory.

`producer`
: `https://blackducksoftware.github.io/bdio#hasProducer`
: The tool which produced the BDIO document. This property should be specified for the named graph.

`range`
: `https://blackducksoftware.github.io/bdio#hasRange`
: The ranges of file content a note applies to. Multiple ranges can be specified, however the units must be distinct (e.g. "bytes" and "chars").

`requestedVersion`
: `https://blackducksoftware.github.io/bdio#hasRequestedVersion`
: The namespace specific version range that resulted in a component being included.

`resolver`
: `https://blackducksoftware.github.io/bdio#hasResolver`
: The tool which resolved the namespace specific locator.

`rightsHolder`
: `https://blackducksoftware.github.io/bdio#hasRightsHolder`
: The entity or entities claiming rights over a specific file.

`scope`
: `https://blackducksoftware.github.io/bdio#hasScope`
: The namespace specific scope of a dependency as determined by the resolution tool used to define the dependency. For example, if a dependency came from an npm package's "devDependencies" field, then the scope should be "devDependencies".

`sourceBranch`
: `https://blackducksoftware.github.io/bdio#hasSourceBranch`
: The SCM branch name from the build environment.

`sourceRepository`
: `https://blackducksoftware.github.io/bdio#hasSourceRepository`
: The URI representing the SCM location from the build environment.

`sourceRevision`
: `https://blackducksoftware.github.io/bdio#hasSourceRevision`
: The SCM revision identifier from the build environment.

`sourceTag`
: `https://blackducksoftware.github.io/bdio#hasSourceTag`
: The SCM tag name from the build environment.

`vendor`
: `https://blackducksoftware.github.io/bdio#hasVendor`
: The name of the vendor who provides a project or component.

`version`
: `https://blackducksoftware.github.io/bdio#hasVersion`
: The display version of the entity. Must reference a single version.

## Datatypes

`ContentRange`
: `https://blackducksoftware.github.io/bdio#ContentRange`
: An HTTP Content Range string.

`ContentType`
: `https://blackducksoftware.github.io/bdio#ContentType`
: An Http Content Type string.

`DateTime`
: `http://www.w3.org/2001/XMLSchema#dateTime`
: ISO date/time string.

`Default`
:
: Unrestricted string value.

`Digest`
: `https://blackducksoftware.github.io/bdio#Digest`
: A string that encapsulates an algorithm name and an unrestricted digest value.

`Long`
: `http://www.w3.org/2001/XMLSchema#long`
: Natural number.

`Products`
: `https://blackducksoftware.github.io/bdio#Products`
: An HTTP User Agent string.

# Semantic Rules
When processing the BDIO model, there are several defined behaviors that should uniformly apply to ensure interoperability. Often it is impractical for publishers to produce fully normal BDIO model, therefore several relationships are expected to be "implicit", processors MUST handle BDIO data the same regardless of their presence. The published BDIO data in conjunction with any implicit relationships constitutes a connected graph.

## The Root
The "root" of the BDIO data is a top-level object (one of: "Project", "Container", "Repository" or "FileCollection"). For a project, the root project cannot be claimed as a sub-project or previous version by any other project. Publishers MUST NOT produce BDIO data with multiple roots; processors MAY elect an arbitrary object as the root or fail outright when the root could be ambiguous.

## Implicit Relationships

### Missing File Parents
The relationship between a file node and it's parent need not be explicitly defined provided that the resolved absolute hierarchical path can be used to unambiguously identify the parent file.

### Missing Project Dependencies
TODO Should we allow project dependencies to be implied for unreachable components? What would we use as scope, etc.?

## Preservation of Unknown Data
Publishers may choose to include data in a BDIO data set that is not part of the BDIO model, this data will come in the form of JSON-LD types or terms not defined by this specification. Processors MUST preserve this unknown data when handling BDIO, however any data which is not reachable from the root project SHOULD be ignored and does not need to preserved.

## File Data Properties
When describing file or resources in BDIO, publishers SHALL adhere to the guidelines in this section: this ensures data size is minimized and interpretation by processors can be consistent. BDIO files can be used to describe files on a file system (real or virtual), entries within an archive, resources from the web or any other entity which can be described as hierarchal structure of named pieces of data.

### File Paths
BDIO files must be represented as a tree-like hierarchy: each file is permitted to have a single parent. The file's path is used to identify the location within the hierarchy. The path MUST be encoded as a URI. Most entities will have a "natural" URI (e.g. with a "file:" or "http(s):" scheme); however special considerations are necessary for the path to an archive entry. Archive entry paths must be encoding using the pattern:

````
<scheme> ":" <archive-URI> "#" <entry-name>
````

Using this pattern, it is possible to encode archive entry paths at arbitrary "nesting levels" (i.e. archives within archives). It is important when constructing archive entry path URIs that the URI of the archive itself be encoded (including any "/" characters), as archive nesting levels increase, so will the encodings (e.g. a "/" will initially be encoded as "%2F", at the next nesting level it will appear as "%252F"). All URI encodings MUST be performed on NFC normalized UTF-8 encoded byte sequences. Trailing slashes and files named "." or ".." MUST be omitted. The scheme should be used to identify the archive format. Archive entry names SHOULD start with a "/", however there are cases where the entry name can be used without the leading slash (refer to [Appendix C: File Data][appendix-c] for additional details).

### File Types
Metadata regarding how the content of a file should be (or was) interpreted is split over several data properties in BDIO. The file system type is defined the role a file plays in the structure of the file hierarchy, it should be obtained either directly from the file system or from archive metadata used to preserve the original file system structure. The content type describes how the contents of the file should be interpreted, often times this information is inferred from the file name (e.g. extension matching); however producers may know the type as a consequence of processing the file. Publishers MUST NOT include a content type obtained through simple file name matching as this work can be done by a processor later; if content type is represented in metadata using a different format (e.g. using extended file attributes), or if the content type was determined based on some specific processing performed on the actual contents of the file, it is appropriate to include the content type, even if the result would be the same as computed through standard name mapping. Text based content SHOULD include the encoding (this information MUST NOT be included in the content type); it is possible that the encoding can be determined without a content type. Symbolic links can be recorded using the link path: the value is the same format described for file paths.

Processors MAY imply the file system type according the following rules, publishers MUST NOT generate conflicting data and consumers SHOULD reject data containing conflicts:

1. If another file references the file as a parent...
    * A byte count or content type implies a file system type of `directory/archive`
    * Otherwise the implied file system type is `directory`
1. A link path implies a file system type of `symlink`
1. An encoding implies a file system type of `regular/text`
1. Processors MAY apply implementation specific file path hueristics to determine the file system type, otherwise the implied file system type is `regular`

# Document Format
BDIO data can be transferred using one of four different formats depending on the capabilities of the parties involved and volume of data. Any JSON data being transferred MAY be pretty printed, it makes human consumption easier and has minimal impact on data size when compression is being used. BDIO data MUST be expressed as a named graph, the graph's label is used to uniquely identify the source of the data.

For all supported formats, characters MUST be encoded using UTF-8. Consumers MUST NOT accept alternate character encodings.

## JSON-LD
The default format for BDIO data is [JSON-LD 1.0][jsonld].

Producers MAY use remote JSON-LD contexts. Compliant consumers MAY operate in "offline mode", restricting access to remote JSON-LD context documents. When operating in this mode, an offline cached copy of the following JSON-LD context documents MUST be made available during processing: `https://blackducksoftware.com/bdio`

Both compliant consumers and producers SHOULD use GZIP content encoding when transferring JSON-LD data. Note that content encoding support for HTTP requests is often not offered by default. Producers MUST create JSON-LD data whose content is strictly less then 16MB; consumers SHOULD fail when presented with a JSON-LD file in excess of this limit. The size limit is applied on presentation and MUST NOT account for effects of JSON-LD normalization though the application of compaction or expansion algorithms.

JSON-LD data should be given the file extension `.jsonld` and should be transferred using the content type application/ld+json (only in [accordance][jsonld-json] with the [JSON-LD Specification][jsonld-iana]).

## JSON
While all JSON-LD is technically JSON, it is often convenient to simplify the syntax using JSON-LD compaction. The default context for BDIO documents may be referenced as a remote context using the IRI `https://blackducksoftware.com/bdio`. Compliant consumers SHOULD include an offline copy of this context to avoid network traffic when processing JSON-LD data.

The `Link` header should be honored on both requests and responses per the [JSON-LD specification][jsonld-iana].

As with JSON-LD, GZIP content encoding SHOULD be used when transferring JSON data. Furthermore, the same 16MB size limit applies to the JSON data as presented to the consumer.

JSON data should be given the file extension `.json` and should be transferred using either the content type `application/json` (preferred when using explicit links to context data) or `application/vnd.blackducksoftware.bdio+json` (when the use of the default BDIO context is implied).

## BDIO Document
In addition to transferring raw JSON-LD or a JSON representation of linked data, there are some limitations for which a separate format makes sense. For example, server support for `Content-Encoding` and `Link` request headers cannot always be guaranteed. The BDIO Document format is intended to overcome some of these limitations in a non-invasive manor.

A "BDIO Document" is a Zip file consisting of any number of JSON-LD files, or "entries". Entries are individually compressed per the Zip specification, BDIO Document producers SHOULD use the DEFLATE compression method. The uncompressed size of each entry MUST be strictly less then 16MB. Compliant consumers SHOULD process entries in "appearance order", that is, the order in which they were added to the Zip archive. Entry names MUST have a ".jsonld" suffix and SHOULD NOT contain the "/" character. Additional non-JSON-LD files MAY be included in the archive.

Each JSON-LD entry MUST be self-contained; producers SHOULD use the "expanded" form but may use other forms so long as full context information is included. Additionally, the same named graph label MUST be used for every entry. Unlike the other formats, compliant consumers are not required to provide any online connectivity or cached context content when processing BDIO Documents. Linked data nodes which span multiple JSON-LD entries MUST have an `@id` and `@type` specification in every JSON-LD node.

To simplify BDIO Document processing, several restrictions are placed on the Zip format used as the primary container. BDIO Documents MUST NOT contain header or footer data, that is the file should begin with a local file header and end with the Zip end of central directory record. Additionally BDIO Documents MUST NOT contain entries which are not listed in the central directory, this restriction allows BDIO Documents to be processed using stream based tools.

BDIO Documents should be given the file extension .bdio and should be transferred as binary data using the content type `application/vnd.blackducksoftware.bdio+zip`.

# Appendix A: Namespace Recommendations [appendix-a]
It is impractical for this specification to absolutely define all of the available namespaces and their rules, they constantly change as new tools are introduced and as people remember how to use old tools. The following non-normative recommendations for publishers and processors serve only as a guideline to what could be implemented. These recommendations are subject to change and ultimately it is the responsibility of the publishers and processors to agree on the namespace values and the interpretation of the field values.
This section is under development in the Proposed Namespace Values area.

# Appendix B: Identifier Guidelines [appendix-b]
Selection of proper identifiers is imperative to the proper construction of a BDIO data set.
TODO Suggest identifiers to use in specific situations, include use of "mvn:" and "urn:uuid:" URIs

# Appendix C: File Data [appendix-c]

## Common File Path Archive Schemes

| Scheme    | Slash Required | Description |
|-----------|----------------|-------------|
| `zip`     | No             | ZIP |
| `jar`     | No             | Java Archive |
| `tar`     | No             | Tape Archive |
| `rpm`     | No             | RPM Package Manager |
| `ar`      | No             | Unix archiver |
| `arj`     | No             | ARJ archives |
| `cpio`    | No             | Copy in and out |
| `dump`    | No             | Unix dump |
| `sevenz`  | No             | 7-Zip |
| `rar`     | Yes            | Roshal Archive |
| `xar`     | Yes            | Extensible Archive |
| `phar`    | Yes            | PHP Archive |
| `cab`     | Yes            | Cabinet |
| `unknown` | Yes            | Used when the actual scheme is not known |

NOTE: File extensions and/or compression formats are not accounted for using the scheme, e.g. a file with the extension ".tgz" or ".tar.gz" still has a scheme of "tar".

## Allowed File System Types

| File System Type         | Description |
|--------------------------|-------------|
| `regular`                | A regular file, typically of unknown or binary content. |
| `regular/text`           | A regular file with known text content. Should be accompanied by an encoding. |
| `directory`              | A directory entry which may or may not contain children. |
| `directory/archive`      | An archive which may or may not contain children. Can also be treated as a regular file. |
| `symlink`                | A symbolic link. Should be accompanied by a link target. |
| `other/device/block`     | A block device, like a drive. |
| `other/device/character` | A character device, like a terminal. |
| `other/pipe`             | A named pipe. |
| `other/socket`           | A socket. |
| `other/whiteout`         | A whiteout, or file removal in a layered file system. |

NOTE: File system types are compared case-insensitively.

# Appendix D: BDIO Content Types [appendix-d]

| Content Type                                  | Extension | Description |
|-----------------------------------------------|-----------|-------------|
| `application/ld+json`                         | `jsonld`  | The content type used when BDIO data is represented using JSON-LD. The context will either be referenced as a remote document or will be explicitly included in the content body. Note that only UTF-8 character encoding is allowed. |
| `application/json`                            | `json`    | The content type used when BDIO data is represented using plain JSON and the context is specified externally (e.g. using the `Link` header). Note that only UTF-8 character encoding is allowed. |
| `application/vnd.blackducksoftware.bdio+json` | `json`    | The content type used when BDIO data is represented using plain JSON that should be interpreted using the default BDIO context. |
| `application/vnd.blackducksoftware.bdio+zip`  | `bdio`    | The content type used when BDIO data is represented as self-contained JSON-LD stored in a ZIP archive. |


[jsonld]: https://www.w3.org/TR/2014/REC-json-ld-20140116/
[jsonld-json]: http://www.w3.org/TR/json-ld/#interpreting-json-as-json-ld "Interpreting JSON as JSON-LD"
[jsonld-iana]: http://www.w3.org/TR/json-ld/#iana-considerations "IANA Considerations"
