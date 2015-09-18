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

import static com.google.common.base.Objects.toStringHelper;

import java.util.Objects;

import com.blackducksoftware.bom.ImmutableNode;
import com.blackducksoftware.bom.Node;

/**
 * Base class for adaptors of a specific type to a node. In general subclasses should be mutable nodes that adapt a
 * given type to a node.
 *
 * @author jgustie
 */
public abstract class AdaptorNode<T> implements Node {

    /**
     * Returns an immutable snapshot of the current state of this adaptor.
     * <p>
     * Note that a large part of the adaptor is ensure that code like this never gets executed. However, there are cases
     * where thread safety of a downstream consumer cannot be guaranteed and this is necessary (at the cost of lots of
     * little memory allocations and extra burden on the garbage collector).
     * <p>
     * In general, try not to use this too much.
     */
    public Node snapshot() {
        return ImmutableNode.copyOf(this);
    }

    // Generalize object methods to be compatible with other node types.

    @Override
    public int hashCode() {
        return Objects.hash(id(), types(), data());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Node) {
            Node other = (Node) obj;
            return id().equals(other.id()) && types().equals(other.types()) && data().equals(other.data());
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("id", id())
                .add("types", types())
                .add("data", data())
                .toString();
    }

}
