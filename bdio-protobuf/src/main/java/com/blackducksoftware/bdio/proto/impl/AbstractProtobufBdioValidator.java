/*
 * Copyright (C) 2023 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio.proto.impl;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.blackducksoftware.bdio.proto.api.BdioValidationException;
import com.blackducksoftware.bdio.proto.api.IProtobufBdioValidator;
import com.blackducksoftware.bdio.proto.domain.ProtoAnnotationNode;
import com.blackducksoftware.bdio.proto.domain.ProtoBdbaFileNode;
import com.blackducksoftware.bdio.proto.domain.ProtoComponentNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerLayerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoContainerNode;
import com.blackducksoftware.bdio.proto.domain.ProtoDependencyNode;
import com.blackducksoftware.bdio.proto.domain.ProtoFileNode;

/**
 * Provides common methods for validation of different node types
 *
 * @author sharapov
 *
 */
public abstract class AbstractProtobufBdioValidator implements IProtobufBdioValidator {

    private static final String DEPENDENCY_NODE_CLASS = ProtoDependencyNode.class.getSimpleName();

    private static final String FILE_NODE_CLASS = ProtoFileNode.class.getSimpleName();

    private static final String COMPONENT_NODE_CLASS = ProtoComponentNode.class.getSimpleName();

    private static final String ANNOTATION_NODE_CLASS = ProtoComponentNode.class.getSimpleName();

    private static final String CONTAINER_NODE_CLASS = ProtoContainerNode.class.getSimpleName();

    private static final String CONTAINER_LAYER_NODE_CLASS = ProtoContainerLayerNode.class.getSimpleName();

    private static final String BDBA_FILE_NODE_CLASS = ProtoBdbaFileNode.class.getSimpleName();

    protected void validate(ProtoDependencyNode node) {
        requireNonBlank(DEPENDENCY_NODE_CLASS, "componentId", node.getComponentId());
        requireNonEmptyList(DEPENDENCY_NODE_CLASS, "matchTypes", node.getMatchTypesList());
    }

    protected void validate(ProtoFileNode node) {
        requireNonEmpty(FILE_NODE_CLASS, "name", node.getName());
        requireNonBlank(FILE_NODE_CLASS, "path", node.getPath());
        requireNonBlank(FILE_NODE_CLASS, "uri", node.getUri());
        requireNonBlank(FILE_NODE_CLASS, "fileSystemType", node.getFileSystemType());
    }

    protected void validate(ProtoComponentNode node) {
        requireNonBlank(COMPONENT_NODE_CLASS, "id", node.getId());
        requireNonBlank(COMPONENT_NODE_CLASS, "namespace", node.getNamespace());
        requireNonBlank(COMPONENT_NODE_CLASS, "identifier", node.getIdentifier());
    }

    protected void validate(ProtoAnnotationNode node) {
        requireNonBlank(ANNOTATION_NODE_CLASS, "id", node.getId());
        requireNonBlank(ANNOTATION_NODE_CLASS, "comment", node.getComment());
    }

    protected void validate(ProtoContainerNode node) {
        requireNonBlank(CONTAINER_NODE_CLASS, "id", node.getId());
        requireNonBlank(CONTAINER_NODE_CLASS, "image", node.getImage());
        requireNonBlank(CONTAINER_NODE_CLASS, "architecture", node.getArchitecture());
        requireNonBlank(CONTAINER_NODE_CLASS, "os", node.getOs());
        requireNonBlank(CONTAINER_NODE_CLASS, "config", node.getConfig());
        requireNonEmptyList(CONTAINER_NODE_CLASS, "layers", node.getLayersList());
    }

    protected void validate(ProtoContainerLayerNode node) {
        requireNonBlank(CONTAINER_LAYER_NODE_CLASS, "id", node.getId());
        requireNonBlank(CONTAINER_LAYER_NODE_CLASS, "layer", node.getLayer());
    }

    protected void validate(ProtoBdbaFileNode node) {
        requireNonBlank(BDBA_FILE_NODE_CLASS, "id", node.getId());
        requireNonBlank(BDBA_FILE_NODE_CLASS, "uri", node.getUri());
        requireNonNull(BDBA_FILE_NODE_CLASS, "lastModifiedDateTime", node.getLastModifiedDateTime());
    }

    private void requireNonNull(String className, String fieldName, Object value) {
        if (value == null) {
            throw new BdioValidationException("The field " + className + "." + fieldName + " must be non null: " + value);
        }
    }
    
    private void requireNonEmpty(String className, String fieldName, String value) {
        if (StringUtils.isEmpty(value)) {
            throw new BdioValidationException("The field " + className + "." + fieldName + " must not be empty: " + value);
        }
    }

    private void requireNonBlank(String className, String fieldName, String value) {
        if (StringUtils.isBlank(value)) {
            throw new BdioValidationException("The field " + className + "." + fieldName + " must not be blank: " + value);
        }
    }

    private <T> void requireNonEmptyList(String className, String fieldName, List<T> list) {
        if (list.isEmpty()) {
            throw new BdioValidationException("The list " + className + "." + fieldName + " must not be empty");
        }
    }
}
