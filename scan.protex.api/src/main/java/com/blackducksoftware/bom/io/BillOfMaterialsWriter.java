/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 * 
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bom.io;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;

import com.blackducksoftware.bom.Node;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A simple writer for producing a JSON formatted Bill of Materials from a source of node instances.
 *
 * @author jgustie
 */
public class BillOfMaterialsWriter implements Closeable, Flushable {

    /**
     * The output stream backed generator used to produce actual JSON.
     */
    private final JsonGenerator json;

    /**
     * Create a new writer that produces UTF-8 encoded JSON.
     */
    public BillOfMaterialsWriter(LinkedDataContext context, OutputStream out) throws IOException {
        // Setup the JSON generator
        // TODO Expose a way add more modules to the object mapper?
        json = new ObjectMapper()
                .registerModule(new BillOfMaterialsModule(context))
                .getFactory()
                .createGenerator(out);

        // TODO Any other config we want to do?
        json.useDefaultPrettyPrinter();
        json.disable(Feature.AUTO_CLOSE_TARGET);

        // Start the list of nodes
        json.writeStartArray();
    }

    /**
     * Just keep calling this.
     */
    public BillOfMaterialsWriter write(Node node) throws IOException {
        json.writeObject(checkNotNull(node));
        return this;
    }

    @Override
    public void flush() throws IOException {
        json.flush();
    }

    @Override
    public void close() throws IOException {
        // Close the list (and object enclosing the context if necessary)
        json.writeEndArray();
        json.close();
    }
}
