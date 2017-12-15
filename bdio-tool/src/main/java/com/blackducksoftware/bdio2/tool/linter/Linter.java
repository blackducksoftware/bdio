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
package com.blackducksoftware.bdio2.tool.linter;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.github.jsonldjava.core.JsonLdConsts;

/**
 * Collection of types used for the linter.
 *
 * @author jgustie
 */
public final class Linter {

    // Rules we still need:
    // 1. More then two license edges that are not all the same type
    // 2. Property "allowed on" validation

    /**
     * (L)int (T)okens.
     */
    public enum LT {
        _Metadata(),
        _root(),
        _id(),
        _unknown(),
        _implicit(),
        _partition(),
    }

    /**
     * A violation of a linter rule.
     */
    public static class Violation {
        private final Rule<?> rule;

        private final Object context;

        private final Optional<String> message;

        private final Object[] arguments;

        Violation(Rule<?> rule, Object context) {
            this(rule, context, null);
        }

        Violation(Rule<?> rule, Object context, @Nullable String message, Object... arguments) {
            this.rule = Objects.requireNonNull(rule);
            this.context = contextIdentifier(context);
            this.message = Optional.ofNullable(message);
            this.arguments = Objects.requireNonNull(arguments);
        }

        private static Object contextIdentifier(Object context) {
            Objects.requireNonNull(context);
            if (context instanceof Vertex) {
                return ((Vertex) context).id();
            } else if (context instanceof Edge) {
                return ((Edge) context).id();
            } else if (context instanceof Map<?, ?>) {
                return ((Map<?, ?>) context).get(JsonLdConsts.ID);
            } else {
                throw new IllegalArgumentException("unknown context");
            }
        }

        public Rule<?> rule() {
            return rule;
        }

        public Object context() {
            return context;
        }

        public String formatMessage() {
            return message.map(format -> String.format(format, arguments)).orElse("");
        }

    }

    /**
     * The severity of rule, generally used to help group and filter rules.
     */
    public enum Severity {
        error, warning
    }

    /**
     * An abstract linter rule.
     */
    public interface Rule<T> {

        /**
         * Returns the severity of this rule.
         */
        Severity severity();

        /**
         * Returns a stream of violations for the given input.
         */
        Stream<Violation> validate(T input);

    }

    /**
     * A linter rule that looks at each BDIO entry.
     */
    public interface RawEntryRule extends Rule<Object> {
    }

    /**
     * A linter rule that looks at each BDIO node.
     */
    public interface RawNodeRule extends Rule<Map<String, Object>> {
    }

    /**
     * A linter rule that looks at each vertex prior to semantic rules being applied.
     */
    public interface LoadedGraphRule extends Rule<Vertex> {
    }

    /**
     * A linter rule that traverses the graph.
     */
    public interface CompletedGraphRule extends Rule<GraphTraversalSource> {
    }

    /**
     * Returns a stream of all the known rules.
     */
    public static Stream<Rule<?>> loadAllRules() {
        return Stream.<Rule<?>> builder()
                .add(new DataPropertyRange())
                .add(new MissingFilePath())
                .add(new MissingProjectName())
                .add(new ObjectPropertyRange())
                .add(new SingleRoot())
                .add(new ValidFilePath())
                .add(new ValidIdentifier())
                .build();
    }

    private Linter() {
        assert false;
    }

}
