/*
 * Copyright 2017 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2;

import java.util.Objects;

/**
 * Exception thrown when an attempt is made to reference a node that does not exist.
 *
 * @author jgustie
 */
public class NodeDoesNotExistException extends Exception {

    private final Object nodeIdentifier;

    private final String objectPropertyName;

    private final Object missingNodeIdentifier;

    private static String message(Object nodeIdentifier, String objectPropertyName, Object missingNodeIdentifier) {
        return String.format("'%s' references non-existent '%s': %s", nodeIdentifier, objectPropertyName, missingNodeIdentifier);
    }

    public NodeDoesNotExistException(Object nodeIdentifier, String objectPropertyName, Object missingNodeIdentifier) {
        super(message(nodeIdentifier, objectPropertyName, missingNodeIdentifier));
        this.nodeIdentifier = Objects.requireNonNull(nodeIdentifier);
        this.objectPropertyName = Objects.requireNonNull(objectPropertyName);
        this.missingNodeIdentifier = Objects.requireNonNull(missingNodeIdentifier);
    }

    /**
     * Returns the identifier of the node referencing a non-existent node.
     */
    public Object getNodeIdentifier() {
        return nodeIdentifier;
    }

    /**
     * Returns the name of the object property on the node which references a non-existent node.
     */
    public String getObjectPropertyName() {
        return objectPropertyName;
    }

    /**
     * Returns the bad identifier.
     */
    public Object getMissingNodeIdentifier() {
        return missingNodeIdentifier;
    }

}
