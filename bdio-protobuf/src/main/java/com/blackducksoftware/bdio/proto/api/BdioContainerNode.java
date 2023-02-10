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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

/**
 *
 * @author sharapov
 *
 */
public class BdioContainerNode implements IBdioNode {

    private String id;

    private String image;

    private String architecture;

    private List<String> repoTags;

    private String os;

    private Instant createdAt;

    private String config;

    private List<String> layers;

    public BdioContainerNode(String id, String image, String architecture, List<String> repoTags, String os,
            Instant createdAt, String config, List<String> layers) {
        this.id = id;
        this.image = image;
        this.architecture = architecture;
        this.repoTags = ImmutableList.copyOf(repoTags);
        this.os = os;
        this.createdAt = createdAt;
        this.config = config;
        this.layers = ImmutableList.copyOf(layers);
    }

    public String getId() {
        return id;
    }

    public String getImage() {
        return image;
    }

    public String getArchitecture() {
        return architecture;
    }

    public List<String> getRepoTags() {
        return repoTags;
    }

    public String getOs() {
        return os;
    }

    public Optional<Instant> getCreatedAt() {
        return Optional.ofNullable(createdAt);
    }

    public String getConfig() {
        return config;
    }

    public List<String> getLayers() {
        return layers;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getImage(), getArchitecture(), getRepoTags(), getOs(), getCreatedAt(),
                getConfig(), getLayers());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other instanceof BdioContainerNode) {
            BdioContainerNode containerImageNode = (BdioContainerNode) other;
            return Objects.equals(getId(), containerImageNode.getId())
                    && Objects.equals(getImage(), containerImageNode.getImage())
                    && Objects.equals(getArchitecture(), containerImageNode.getArchitecture())
                    && Objects.equals(getRepoTags(), containerImageNode.getRepoTags())
                    && Objects.equals(getOs(), containerImageNode.getOs())
                    && Objects.equals(getCreatedAt(), containerImageNode.getCreatedAt())
                    && Objects.equals(getConfig(), containerImageNode.getConfig())
                    && Objects.equals(getLayers(), containerImageNode.getLayers());
        }

        return false;
    }
}
