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
import java.util.Optional;

/**
 *
 * @author sharapov
 *
 */
public class BdioComponentNode implements IBdioNode {

    private String id;

    private String namespace;

    private String identifier;

    private String descriptionId;

    public BdioComponentNode(String id, String namespace, String identifier, String descriptionId) {
        this.id = id;
        this.namespace = namespace;
        this.identifier = identifier;
        this.descriptionId = descriptionId;
    }

    public String getId() {
        return id;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Optional<String> getDescriptionId() {
        return Optional.ofNullable(descriptionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getNamespace(), getIdentifier(), getDescriptionId());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other instanceof BdioComponentNode) {
            BdioComponentNode componentNode = (BdioComponentNode) other;
            return Objects.equals(getId(), componentNode.getId())
                    && Objects.equals(getNamespace(), componentNode.getNamespace())
                    && Objects.equals(getIdentifier(), componentNode.getIdentifier())
                    && Objects.equals(getDescriptionId(), componentNode.getDescriptionId());
        }

        return false;
    }
}
