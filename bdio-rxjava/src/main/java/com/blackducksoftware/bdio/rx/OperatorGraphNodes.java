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
package com.blackducksoftware.bdio.rx;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.blackducksoftware.bdio.jsonld.JsonLdKeyword;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdError.Error;

import rx.Observable;
import rx.Observable.Operator;
import rx.Subscriber;

/**
 * Operator to get the graph nodes from JSON-LD input.
 *
 * @author jgustie
 */
public class OperatorGraphNodes implements Operator<Observable<Map<String, Object>>, Object> {

    // TODO Instead of a singleton, should there be a way to specify the graph label?

    private static class Holder {
        static final OperatorGraphNodes INSTANCE = new OperatorGraphNodes();
    }

    public static OperatorGraphNodes instance() {
        return Holder.INSTANCE;
    }

    private OperatorGraphNodes() {
    }

    @Override
    public Subscriber<? super Object> call(final Subscriber<? super Observable<Map<String, Object>>> child) {
        return new Subscriber<Object>(child, false) {
            @Override
            public void onNext(Object input) {
                // First try to pull out the `@graph` list
                Object nodes = null;
                if (input instanceof List<?>) {
                    for (Object item : (List<?>) input) {
                        nodes = getGraph(item);
                        if (nodes != null) {
                            break;
                        }
                    }
                    if (nodes == null) {
                        nodes = input;
                    }
                } else if (input instanceof Map<?, ?>) {
                    nodes = getGraph(input);
                }

                // Wrap the node list in an observable
                if (nodes instanceof List<?>) {
                    // TODO How can we verify these casts? What does the JSON-LD library do?
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> nodeList = (List<Map<String, Object>>) nodes;
                    // TODO Sort these while we have the in memory list?
                    child.onNext(Observable.from(nodeList));
                } else {
                    // TODO Emit just the input as a single node list? Only if it has '@id'?
                    unsubscribe();
                    onError(new JsonLdError(Error.SYNTAX_ERROR));
                }
            }

            @Override
            public void onCompleted() {
                child.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                child.onError(e);
            }
        };
    }

    @Nullable
    public static Object getGraph(@Nullable Object input) {
        if (input instanceof Map<?, ?>) {
            // TODO Do we need an ID to check to determine which graph to get?
            return ((Map<?, ?>) input).get(JsonLdKeyword.graph.toString());
        } else {
            return null;
        }
    }

}
