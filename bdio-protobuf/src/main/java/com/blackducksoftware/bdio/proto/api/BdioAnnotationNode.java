/*
 * Copyright (C) 2023 Synopsys Inc.
 * http://www.synopsys.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Synopsys ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Synopsys.
 */
package com.blackducksoftware.bdio.proto.api;

import java.util.Objects;

/**
 *
 * @author sharapov
 *
 */
public class BdioAnnotationNode implements IBdioNode {

    private String id;

    private String comment;

    public BdioAnnotationNode(String id, String comment) {
        this.id = id;
        this.comment = comment;
    }

    public String getId() {
        return id;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getComment());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other instanceof BdioAnnotationNode) {
            BdioAnnotationNode annotationNode = (BdioAnnotationNode) other;
            return Objects.equals(getId(), annotationNode.getId())
                    && Objects.equals(getComment(), annotationNode.getComment());
        }

        return false;
    }
}
