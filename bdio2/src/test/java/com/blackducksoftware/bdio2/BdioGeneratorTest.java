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
package com.blackducksoftware.bdio2;

import static com.blackducksoftware.bdio2.test.BdioTest.zipBytes;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.reset;
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
 * Tests for {@link BdioGenerator}.
 *
 * @author jgustie
 */
public class BdioGeneratorTest {

    @Mock
    private Subscriber<Object> subscriber;

    @Before
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void readOne() {
        BdioGenerator generator = new BdioGenerator(zipBytes("[ \"test\" ]"));
        try {
            reset(subscriber);
            generator.generate(subscriber::onNext, subscriber::onError, subscriber::onComplete);
            verify(subscriber).onNext(Lists.newArrayList("test"));
            verifyNoMoreInteractions(subscriber);

            reset(subscriber);
            generator.generate(subscriber::onNext, subscriber::onError, subscriber::onComplete);
            verify(subscriber).onComplete();
            verifyNoMoreInteractions(subscriber);
        } finally {
            generator.dispose();
        }
    }

    @Test
    public void readSyntaxError() {
        BdioGenerator generator = new BdioGenerator(zipBytes("I'm not JSON"));
        try {
            reset(subscriber);
            generator.generate(subscriber::onNext, subscriber::onError, subscriber::onComplete);
            verify(subscriber).onError(any(JsonParseException.class));
            verifyNoMoreInteractions(subscriber);
        } finally {
            generator.dispose();
        }
    }

}
