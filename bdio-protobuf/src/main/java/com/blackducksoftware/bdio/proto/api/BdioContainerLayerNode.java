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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 *
 * @author sharapov
 *
 */
public class BdioContainerLayerNode implements IBdioNode {

    private String id;

    private String layer;

    private long size;

    private String command;

    private Instant createdAt;

    private String descriptionId;

    public BdioContainerLayerNode(String id, String layer, long size, String command, Instant createdAt,
            String descriptionId) {
        this.id = id;
        this.layer = layer;
        this.size = size;
        this.command = command;
        this.createdAt = createdAt;
        this.descriptionId = descriptionId;
    }

    public String getId() {
        return id;
    }

    public String getLayer() {
        return layer;
    }

    public long getSize() {
        return size;
    }

    public Optional<String> getCommand() {
        return Optional.ofNullable(command);
    }

    public Optional<Instant> getCreatedAt() {
        return Optional.ofNullable(createdAt);
    }

    public Optional<String> getDescriptionId() {
        return Optional.ofNullable(descriptionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getLayer(), getSize(), getCommand(), getCreatedAt(), getDescriptionId());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other instanceof BdioContainerLayerNode) {
            BdioContainerLayerNode containerLayerNode = (BdioContainerLayerNode) other;
            return getSize() == containerLayerNode.getSize() && Objects.equals(getId(), containerLayerNode.getId())
                    && Objects.equals(getLayer(), containerLayerNode.getLayer())
                    && Objects.equals(getCommand(), containerLayerNode.getCommand())
                    && Objects.equals(getCreatedAt(), containerLayerNode.getCreatedAt())
                    && Objects.equals(getDescriptionId(), containerLayerNode.getDescriptionId());
        }

        return false;
    }
}
