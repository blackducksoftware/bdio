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

import java.io.IOException;
import java.io.Reader;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.Node;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A simple reader for consuming a JSON formatted Bill of Materials from a source of characters.
 *
 * @author jgustie
 */
public class BillOfMaterialsReader implements AutoCloseable {

    /**
     * The reader backed parser used to read actual JSON.
     */
    private final JsonParser json;

    public BillOfMaterialsReader(LinkedDataContext context, Reader in) throws IOException {
        // Setup the JSON parser
        json = new ObjectMapper()
                .registerModule(new BillOfMaterialsModule(context))
                .getFactory()
                .createParser(in);

        // TODO Any other config we want to do?

        // Start by finding the list of nodes
        if (json.nextToken() != JsonToken.START_ARRAY) {
            throw new IOException("expected input to start with an array");
        }
    }

    /**
     * Just keep calling this until it returns {@code null}.
     */
    @Nullable
    public Node read() throws IOException {
        if (json.nextToken() != JsonToken.END_ARRAY) {
            return json.readValueAs(Node.class);
        } else {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        json.close();
    }

}
