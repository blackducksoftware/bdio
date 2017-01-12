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
package com.blackducksoftware.bdio2.tool;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.Bdio.Container;
import com.blackducksoftware.bdio2.Bdio.Datatype;
import com.github.jsonldjava.core.Context;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;

/**
 * Tool for producing the current BDIO JSON-LD context.
 *
 * @author jgustie
 */
public class ContextTool extends Tool {

    public static void main(String[] args) {
        new ContextTool(null).parseArgs(args).run();
    }

    public ContextTool(@Nullable String name) {
        super(name);
    }

    @Override
    public void execute() throws JsonLdError {
        printJson(generateContext());
    }

    /**
     * Generates a representation of the BDIO JSON-LD context.
     */
    public Map<String, Object> generateContext() throws JsonLdError {
        Map<String, Object> context = new LinkedHashMap<>();

        // Prefixes
        // context.put("bdio", Bdio.Context.DEFAULT + "#");
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
            if (dataProperty.type() != Datatype.Default) {
                definition.put(JsonLdConsts.TYPE, dataProperty.type().toString());
            }
            if (dataProperty.container() == Container.ordered) {
                definition.put(JsonLdConsts.CONTAINER, JsonLdConsts.LIST);
            } else if (dataProperty.container() == Container.unordered) {
                definition.put(JsonLdConsts.CONTAINER, JsonLdConsts.SET);
            }
            context.put(dataProperty.name(), definition);
        }

        // Use the JSON-LD API to normalize the output
        return new Context().parse(context).serialize();
    }

}
