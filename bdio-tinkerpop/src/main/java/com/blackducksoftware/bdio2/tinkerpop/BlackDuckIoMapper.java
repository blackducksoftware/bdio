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

import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.Mapper;

/**
 * A "do nothing" I/O mapper because there is no customizable mapping logic in BDIO.
 *
 * @author jgustie
 */
public class BlackDuckIoMapper implements Mapper<Void> {

    private BlackDuckIoMapper() {
    }

    @Override
    public Void createMapper() {
        throw new UnsupportedOperationException("BDIO does not have an object mapper");
    }

    public static Builder build() {
        return new Builder();
    }

    public final static class Builder implements Mapper.Builder<Builder> {

        private Builder() {
        }

        @Override
        public Builder addRegistry(IoRegistry registry) {
            throw new UnsupportedOperationException("BDIO does not accept custom serializers");
        }

        public BlackDuckIoMapper create() {
            return new BlackDuckIoMapper();
        }

    }

}
