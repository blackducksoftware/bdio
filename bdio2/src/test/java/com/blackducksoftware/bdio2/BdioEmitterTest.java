/*
 * Copyright 2016 Black Duck Software, Inc.
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

import static com.blackducksoftware.bdio2.test.BdioTest.zipBytes;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactivestreams.Subscriber;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.common.collect.Lists;

/**
 * Tests for {@link BdioEmitter}.
 *
 * @author jgustie
 */
public class BdioEmitterTest {

    @Mock
    private Subscriber<Object> subscriber;

    @Before
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void readOne() {
        BdioEmitter emitter = new BdioEmitter(zipBytes("[ \"test\" ]"));
        try {
            emitter.emit(subscriber::onNext, subscriber::onError, subscriber::onComplete);
            verify(subscriber).onNext(Lists.newArrayList("test"));
            verifyNoMoreInteractions(subscriber);

            emitter.emit(subscriber::onNext, subscriber::onError, subscriber::onComplete);
            verify(subscriber).onComplete();
            verifyNoMoreInteractions(subscriber);
        } finally {
            emitter.dispose();
        }
    }

    @Test
    public void readSyntaxError() {
        BdioEmitter emitter = new BdioEmitter(zipBytes("I'm not JSON"));
        try {
            emitter.emit(subscriber::onNext, subscriber::onError, subscriber::onComplete);
            verify(subscriber).onError(any(JsonParseException.class));
            verifyNoMoreInteractions(subscriber);
        } finally {
            emitter.dispose();
        }
    }

}
