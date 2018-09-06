/*
 * Copyright 2018 Synopsys, Inc.
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
package com.blackducksoftware.bdio2;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.blackducksoftware.bdio2.datatype.ValueObjectMapper;
import com.blackducksoftware.common.value.ProductList;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;

/**
 * Tests for {@code LegacyScanContainerEmitter}.
 *
 * @author jgustie
 */
@RunWith(Parameterized.class)
public class LegacyScanContainerEmitterTest {

    /**
     * Wrapper around the emitter types to help with test result readability.
     */
    private static final class TestEmitterFactory implements Function<InputStream, Emitter> {
        private final Class<? extends Emitter> type;

        public TestEmitterFactory(Class<? extends Emitter> type) {
            this.type = Objects.requireNonNull(type);
        }

        @Override
        public Emitter apply(InputStream in) {
            try {
                return type.getConstructor(InputStream.class).newInstance(in);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public String toString() {
            return type.getSimpleName();
        }
    }

    private static final ValueObjectMapper mapper = ValueObjectMapper.getContextValueObjectMapper();

    private static final String nameKey = Bdio.DataProperty.name.toString();

    private static final String publisherKey = Bdio.DataProperty.publisher.toString();

    private static final String creatorKey = Bdio.DataProperty.creator.toString();

    private static final String creationDateTimeKey = Bdio.DataProperty.creationDateTime.toString();

    private static final String captureInterval = Bdio.DataProperty.captureInterval.toString();

    @Parameters(name = "{0}")
    public static Iterable<Function<InputStream, Emitter>> emitterFactories() {
        return ImmutableList.<Function<InputStream, Emitter>> builder()
                .add(new TestEmitterFactory(LegacyStreamingScanContainerEmitter.class))
                .add(new TestEmitterFactory(LegacyScanContainerEmitter.class))
                .build();
    }

    private final Function<InputStream, Emitter> emitterFactory;

    public LegacyScanContainerEmitterTest(Function<InputStream, Emitter> emitterFactory) {
        this.emitterFactory = Objects.requireNonNull(emitterFactory);
    }

    @Test
    public void metadata() throws Exception {
        InputStream inputStream = CharSource.wrap("{"
                + "\"scannerVersion\": \"0.0.0.0\","
                + "\"signatureVersion\": \"7.0.0\","
                + "\"ownerEntityToken\": \"SP#example.com-test\","
                + "\"createdOn\": \"2016-11-22T16:33:20.000Z\","
                + "\"timeToScan\": 111,"
                + "\"name\": \"Test Metadata 1\","
                + "\"hostName\": \"example.com\","
                + "\"baseDir\": \"/test\","
                + "\"scanNodeList\": []"
                + "}").asByteSource(UTF_8).openStream();
        Map<?, ?> metadata = (Map<?, ?>) emitterFactory.apply(inputStream).stream().limit(1).collect(onlyElement());
        assertThat(mapper.fromFieldValue(nameKey, metadata.get(nameKey)))
                .isEqualTo("Test Metadata 1");
        assertThat(mapper.fromFieldValue(publisherKey, metadata.get(publisherKey)))
                .isEqualTo(ProductList.from("ScanClient/0.0.0.0 (signature 7.0.0) (snippets) LegacyScanContainerEmitter"));
        assertThat(mapper.fromFieldValue(creationDateTimeKey, metadata.get(creationDateTimeKey)))
                .isEqualTo(ZonedDateTime.parse("2016-11-22T16:33:20.000Z"));
        assertThat(mapper.fromFieldValue(creatorKey, metadata.get(creatorKey)))
                .isEqualTo("@example.com");
        assertThat(mapper.fromFieldValue(captureInterval, metadata.get(captureInterval)))
                .isEqualTo("2016-11-22T16:33:20Z/2016-11-22T16:33:20.111Z");
    }

}
