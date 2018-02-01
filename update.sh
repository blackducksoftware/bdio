#/bin/bash

# Include the latest version of the specification
bdio spec > specification.md

# Include only the BDIO contexts relative to the published URI
bdio context --pretty 2.0.0 > 2.0.0
# TODO Can we define MIME types for the context files?
# TODO Can we do an "index" file with the default context?

# Include a BDIO file with all of the SPDX licenses
# TODO bdio spdxdoc --pretty > spdx.jsonld

# TODO Samples
# TODO Extract Javadoc to "releases/<LIB VERSION>/api/docs/"?
# TODO Lint everything
# TODO Add the Git push (only if there were no lint errors)?
