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
package com.blackducksoftware.bdio2.tinkerpop;

public class Tokens {

    // TODO Mimic org.apache.tinkerpop.gremlin.structure.T

    // TODO Use hidden namespace?

    /**
     * Partition representing the identifier of the JSON-LD named graph. Used to isolate the entire graph to the subset
     * of data from a single JSON-LD graph.
     */
    public static final String partition = "_partition";

    /**
     * Identifier of the JSON-LD node. Used to correlate data coming from the JSON-LD graph.
     */
    // TODO Name this "userId" to correspond to the Tinkerpop concept and avoid confusion with T.id?
    public static final String id = "_id";

    /**
     * When there are extra unknown properties, they are serialized together under this key.
     */
    public static final String unknown = "_unknown";

    /**
     * Vertex label of the named graph.
     */
    public static final String NamedGraph = "_NamedGraph";

    public static class NamedGraphTokens {
        public static final String createdAt = "createdAt";

        public static final String updatedAt = "updatedAt";
    }

}
