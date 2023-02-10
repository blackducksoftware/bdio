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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

/**
 *
 * @author sharapov
 *
 */
public class BdioDependencyNode implements IBdioNode {

    private String componentId;

    private String evidenceId;

    private String containerLayer;

    private String whiteoutLayer;

    private String descriptionId;

    private List<String> matchTypes;

    public BdioDependencyNode(String componentId, String evidenceId, String containerLayer, String whiteoutLayer,
            String descriptionId, List<String> matchTypes) {
        this.componentId = componentId;
        this.evidenceId = evidenceId;
        this.containerLayer = containerLayer;
        this.whiteoutLayer = whiteoutLayer;
        this.descriptionId = descriptionId;
        this.matchTypes = ImmutableList.copyOf(matchTypes);
    }

    public String getComponentId() {
        return componentId;
    }

    public Optional<String> getEvidenceId() {
        return Optional.ofNullable(evidenceId);
    }

    public Optional<String> getContainerLayer() {
        return Optional.ofNullable(containerLayer);
    }

    public Optional<String> getWhiteoutLayer() {
        return Optional.ofNullable(whiteoutLayer);
    }

    public Optional<String> getDescriptionId() {
        return Optional.ofNullable(descriptionId);
    }

    public List<String> getMatchTypes() {
        return matchTypes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getComponentId(), getEvidenceId(), getContainerLayer(), getWhiteoutLayer(),
                getDescriptionId(), getMatchTypes());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        } else if (other instanceof BdioDependencyNode) {
            BdioDependencyNode dependencyNode = (BdioDependencyNode) other;
            return Objects.equals(getComponentId(), dependencyNode.getComponentId())
                    && Objects.equals(getEvidenceId(), dependencyNode.getEvidenceId())
                    && Objects.equals(getContainerLayer(), dependencyNode.getContainerLayer())
                    && Objects.equals(getWhiteoutLayer(), dependencyNode.getWhiteoutLayer())
                    && Objects.equals(getDescriptionId(), dependencyNode.getDescriptionId())
                    && Objects.equals(getMatchTypes(), dependencyNode.getMatchTypes());
        }

        return false;
    }
}
