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
package com.blackducksoftware.bdio2.tinkerpop;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.structure.T;
import org.junit.Test;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioEmitter;
import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.common.io.HeapOutputStream;
import com.github.jsonldjava.core.JsonLdConsts;

public class BlackDuckIoWriterTest extends BaseTest {

    private static final ValueObjectMapper valueObjectMapper = new ValueObjectMapper();

    public BlackDuckIoWriterTest(Configuration configuration) {
        super(configuration);
    }

    @Test
    public void writeMetadata() throws Exception {
        String id = "urn:uuid:" + UUID.randomUUID();
        Instant creation = Instant.now();
        graph.addVertex(
                T.label, TT.Metadata,
                TT.id, id,
                Bdio.DataProperty.creation.name(), creation.toString());

        HeapOutputStream buffer = new HeapOutputStream();
        graph.io(BlackDuckIo.build().onConfig(storeMetadataAndIds()))
                .writeGraph(buffer);

        Object obj = new BdioEmitter(buffer.getInputStream()).stream().findFirst().get();
        assertThat(obj).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) obj).containsEntry(JsonLdConsts.ID, id);

        Object creationValue = ((Map<?, ?>) obj).get(Bdio.DataProperty.creation.toString());
        assertThat(valueObjectMapper.fromFieldValue(creationValue)).isEqualTo(creation);
    }

}
