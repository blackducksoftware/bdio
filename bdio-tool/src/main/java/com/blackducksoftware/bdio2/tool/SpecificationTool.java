/*
 * Copyright 2018 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2.tool;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.Resources;

/**
 * Outputs the BDIO specification.
 *
 * @author jgustie
 */
public class SpecificationTool extends Tool {

    public static void main(String[] args) {
        new SpecificationTool(null).parseArgs(args).run();
    }

    // TODO Multiple version support (e.g. old wiki content?)
    // TODO How can we generate the specification using Javadoc comments?
    // TODO Split specification into multiple documents by chapter so we can stitch them together with a TOC?
    // TODO Can we have a separate "sample" tool that generates the sample output?
    // TODO Offer an HTML output version that formats the Markdown

    public SpecificationTool(String name) {
        super(name);
    }

    @Override
    protected void execute() throws Exception {
        // For now, the specification is just packaged in the JAR file, just echo it out
        printOutput("%s", Resources.toString(Resources.getResource("specification/spec.txt"), UTF_8));
    }

}
