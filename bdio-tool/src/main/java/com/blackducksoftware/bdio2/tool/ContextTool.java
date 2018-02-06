/*
 * Copyright 2016 Black Duck Software, Inc.
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

import static com.google.common.collect.MoreCollectors.toOptional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioOptions;
import com.github.jsonldjava.core.Context;
import com.github.jsonldjava.core.DocumentLoader;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.google.common.base.Enums;

/**
 * Tool for producing a BDIO JSON-LD context.
 *
 * @author jgustie
 */
public class ContextTool extends Tool {

    public static void main(String[] args) {
        new ContextTool(null).parseArgs(args).run();
    }

    private String context = Bdio.Context.DEFAULT.toString();

    public ContextTool(@Nullable String name) {
        super(name);

        // Do not allow the context to be any arbitrary remote URI, only allow the "injected" documents to be loaded
        System.setProperty(DocumentLoader.DISALLOW_REMOTE_CONTEXT_LOADING, "true");
    }

    /**
     * Sets the explicit context to output. The value can be any BDIO context URI or just the exact version number.
     */
    public void setContext(String context) {
        Objects.requireNonNull(context);
        try {
            this.context = Bdio.Context.forSpecVersion(context).toString();
        } catch (IllegalArgumentException e) {
            this.context = context;
        }
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        arguments(args).stream().collect(toOptional()).ifPresent(this::setContext);
        return super.parseArguments(args);
    }

    @Override
    protected String formatException(Throwable failure) {
        if (failure instanceof JsonLdError) {
            return ((JsonLdError) failure).getType().toString();
        } else {
            return super.formatException(failure);
        }
    }

    @Override
    public void execute() throws JsonLdError {
        if (context.equals(Bdio.Context.DEFAULT.toString())) {
            // Always generate the default context from source as this tool actually gets used to produce the
            // authoritative version of the context
            printJson(generateContext());
        } else {
            // If we are not generating the default context, we can attempt to load it via the BDIO options class which
            // will have access to the explicit context versions
            printJson(new BdioOptions.Builder().build()
                    .jsonLdOptions()
                    .getDocumentLoader()
                    .loadDocument(context)
                    .getDocument());
        }
        printOutput("%n");
    }

    /**
     * Generates a representation of the default BDIO JSON-LD context.
     */
    public Map<String, Object> generateContext() throws JsonLdError {
        Map<String, Object> context = new LinkedHashMap<>();

        // Prefixes
        context.put("xsd", "http://www.w3.org/2001/XMLSchema#");

        // Classes
        for (Bdio.Class clazz : Bdio.Class.values()) {
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put(JsonLdConsts.ID, clazz.toString());
            context.put(clazz.name(), definition);
        }

        // Object properties
        for (Bdio.ObjectProperty objectProperty : Bdio.ObjectProperty.values()) {
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put(JsonLdConsts.ID, objectProperty.toString());
            definition.put(JsonLdConsts.TYPE, JsonLdConsts.ID);
            context.put(objectProperty.name(), definition);
        }

        // Data properties
        for (Bdio.DataProperty dataProperty : Bdio.DataProperty.values()) {
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put(JsonLdConsts.ID, dataProperty.toString());
            Bdio.Datatype datatype = range(dataProperty);
            if (datatype != Bdio.Datatype.Default) {
                definition.put(JsonLdConsts.TYPE, datatype.toString());
            }
            if (dataProperty.container() == Bdio.Container.ordered) {
                definition.put(JsonLdConsts.CONTAINER, JsonLdConsts.LIST);
            } else if (dataProperty.container() == Bdio.Container.unordered) {
                definition.put(JsonLdConsts.CONTAINER, JsonLdConsts.SET);
            }
            context.put(dataProperty.name(), definition);
        }

        // Use the JSON-LD API to normalize the output
        return new Context().parse(context).serialize();
    }

    public static Bdio.Datatype range(Bdio.DataProperty dataProperty) {
        Bdio.DataPropertyRange range = Enums.getField(dataProperty).getAnnotation(Bdio.DataPropertyRange.class);
        return range != null ? range.value() : Bdio.Datatype.Default;
    }

}
