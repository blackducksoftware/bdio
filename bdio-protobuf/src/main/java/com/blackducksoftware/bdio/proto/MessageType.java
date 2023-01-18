/*
 * Copyright (C) 2022 Synopsys Inc.
 * http://www.synopsys.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Synopsys ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Synopsys.
 */
package com.blackducksoftware.bdio.proto;

/**
 * Enum defining the various message types supported by the Protobuf BDIO format.
 * <p>
 * NOTE: Order matters here, only append new types to the end.
 *
 * @author johara
 */
public enum MessageType {
    SCAN_HEADER,
    FILE_NODE,
    DEPENDENCY_NODE,
    COMPONENT_NODE,
    ANNOTATION_NODE,
    CONTAINER_IMAGE_NODE,
    CONTAINER_LAYER_NODE,
}
