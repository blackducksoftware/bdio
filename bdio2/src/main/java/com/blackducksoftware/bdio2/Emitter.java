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

import java.util.function.Consumer;

/**
 * Abstraction of what will eventually become a reactive producer.
 *
 * @author jgustie
 */
public interface Emitter {

    /**
     * Emits a single element. Implementations must interact with only one parameter one time per call.
     */
    void emit(Consumer<Object> onNext, Consumer<Throwable> onError, Runnable onComplete);

    /**
     * Releases any resources held by this emitter.
     */
    void dispose();

}
