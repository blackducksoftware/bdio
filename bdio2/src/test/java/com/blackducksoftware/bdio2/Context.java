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

import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import org.junit.rules.ExternalResource;

import com.blackducksoftware.bdio2.BdioContext.ActiveContext;

/**
 * A rule for establishing a BDIO context.
 *
 * @author jgustie
 */
public class Context extends ExternalResource {

    private static final BdioContext DEFAULT_CONTEXT = new BdioContext.Builder().expandContext(Bdio.Context.DEFAULT).build();

    /**
     * Factory method for a new rule instance with no initial context.
     */
    public static Context none() {
        return new Context(null);
    }

    @Nullable
    private ActiveContext active;

    private Context(@Nullable ActiveContext active) {
        this.active = active;
    }

    /**
     * Used to access the test context.
     */
    public BdioContext get() {
        checkState(active != null, "A context must be established, e.g. `context.using(someContext)`");
        return active.get();
    }

    /**
     * Used to establish an active context for testing.
     */
    public void using(BdioContext context) {
        checkState(active == null, "A test context is already active");
        active = context.activate();
    }

    /**
     * Establishes a context for the current default version of BDIO for testing.
     *
     * @see #using(BdioContext)
     * @see Bdio.Context#DEFAULT
     */
    public void usingCurentBdio() {
        using(DEFAULT_CONTEXT);
    }

    @Override
    protected void before() {
        // Do nothing, at this point the test will not have had the chance to update the context
    }

    @Override
    protected void after() {
        // If a context was activated, close it
        if (active != null) {
            active.close();
            active = null;
        }
    }

}
