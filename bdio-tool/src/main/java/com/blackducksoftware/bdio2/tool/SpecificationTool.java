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

import java.io.IOException;
import java.io.UncheckedIOException;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

/**
 * Outputs the BDIO specification.
 *
 * @author jgustie
 */
public class SpecificationTool extends Tool {

    // TODO How do we generate this list dynamically?
    private static ImmutableList<String> SECTIONS = ImmutableList.<String> builder()
            .add("00.Abstract.txt")
            .add("01.Introduction.txt")
            .add("02.Model.txt")
            .add("03.SemanticRules.txt")
            .add("04.DocumentFormat.txt")
            .add("AA.NamespaceRecommendations.txt")
            .add("AB.IdentifierGuidelines.txt")
            .add("AC.FileData.txt")
            .add("AD.ContentTypes.txt")
            .build();

    public static void main(String[] args) {
        new SpecificationTool(null).parseArgs(args).run();
    }

    // TODO Multiple version support (e.g. old wiki content?)
    // TODO How can we generate the specification using Javadoc comments?
    // TODO Generate a TOC between sections 00 and 01?
    // TODO Can we have a separate "sample" tool that generates the sample output?
    // TODO Offer an HTML output version that formats the Markdown

    public SpecificationTool(String name) {
        super(name);
    }

    @Override
    protected void execute() throws Exception {
        SECTIONS.stream().sorted().map(resourceName -> {
            try {
                return Resources.toString(Resources.getResource(SpecificationTool.class, "spec/" + resourceName), UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).forEachOrdered(section -> printOutput("%s%n", section));
    }

}
