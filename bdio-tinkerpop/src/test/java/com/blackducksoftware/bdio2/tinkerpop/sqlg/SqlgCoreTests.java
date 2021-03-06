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
package com.blackducksoftware.bdio2.tinkerpop.sqlg;

import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

import com.blackducksoftware.bdio2.test.GraphRunner;
import com.blackducksoftware.bdio2.test.GraphRunner.GraphConfiguration;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoNormalizationTest;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoReaderTest;
import com.blackducksoftware.bdio2.tinkerpop.BlackDuckIoWriterTest;

/**
 * This test just runs the core group of tests with a different graph.
 *
 * @author jgustie
 */
@RunWith(GraphRunner.class)
@GraphConfiguration("/sqlg.properties")
@SuiteClasses({
        BlackDuckIoNormalizationTest.class,
        BlackDuckIoReaderTest.class,
        BlackDuckIoWriterTest.class,
})
public class SqlgCoreTests {
}
