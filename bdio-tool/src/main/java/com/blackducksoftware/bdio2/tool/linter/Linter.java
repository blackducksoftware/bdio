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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;

import java.text.MessageFormat;
import java.util.ListResourceBundle;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
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
     * The severity of rule, generally used to help group and filter rules.
     */
    public enum Severity {
        error,
        warning,
    }

    /**
     * A violation of a linter rule.
     */
    public static class Violation {

        private final Rule<?> rule;

        private final Object target;

        private final Severity severity;

        private final String message;

        private final Optional<Throwable> throwable;

        private Violation(Rule<?> rule, Object target, Severity severity, String message, Throwable throwable) {
            this.rule = Objects.requireNonNull(rule);
            this.target = Objects.requireNonNull(target);
            this.severity = Objects.requireNonNull(severity);
            this.message = Objects.requireNonNull(message);
            this.throwable = Optional.ofNullable(throwable);
        }

        public Rule<?> rule() {
            return rule;
        }

        public Object target() {
            return target;
        }

        public Severity severity() {
            return severity;
        }

        public String message() {
            return message;
        }

        public Optional<Throwable> throwable() {
            return throwable;
        }

    }

    /**
     * An abstract linter rule.
     */
    public interface Rule<T> {

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
     * Helper for building streams of violations.
     */
    public static class ViolationBuilder {

        private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(Messages.class.getName());

        private final Stream.Builder<Violation> result = Stream.builder();

        private final Rule<?> rule;

        private Object target;

        public ViolationBuilder(Rule<?> rule, Object input) {
            this.rule = Objects.requireNonNull(rule);
            this.target = getInputIdentifier(Objects.requireNonNull(input));
        }

        public ViolationBuilder(CompletedGraphRule rule) {
            this.rule = Objects.requireNonNull(rule);
        }

        public Stream<Violation> build() {
            return result.build();
        }

        /**
         * When using completed graph rules the input is the entire graph, this is rarely useful context as generally
         * the violation targets a specific vertex or edge. This method allows the target to be changed, however only if
         * the current rule is an instance of {@code CompletedGraphRule}.
         */
        public ViolationBuilder target(Object input) {
            checkState(rule instanceof CompletedGraphRule, "target can only be set for CompletedGraphRule");
            target = getInputIdentifier(input);
            return this;
        }

        // Errors

        public ViolationBuilder error(String message) {
            return addError(formatMessage(message, null), null);
        }

        public ViolationBuilder error(String message, Object arg1, Object... args) {
            return addError(formatMessage(message, arg1, args), null);
        }

        public ViolationBuilder error(String message, Throwable throwable) {
            return addError(formatMessage(message, null), Objects.requireNonNull(throwable));
        }

        public ViolationBuilder error(String message, Throwable throwable, Object arg1, Object... args) {
            return addError(formatMessage(message, arg1, args), Objects.requireNonNull(throwable));
        }

        private ViolationBuilder addError(String message, @Nullable Throwable throwable) {
            result.add(new Violation(rule, target, Severity.error, message, throwable));
            return this;
        }

        // Warnings

        public ViolationBuilder warning(String message) {
            return addWarning(formatMessage(message, null), null);
        }

        public ViolationBuilder warning(String message, Object arg1, Object... args) {
            return addWarning(formatMessage(message, arg1, args), null);
        }

        public ViolationBuilder warning(String message, Throwable throwable) {
            return addWarning(formatMessage(message, null), Objects.requireNonNull(throwable));
        }

        public ViolationBuilder warning(String message, Throwable throwable, Object arg1, Object... args) {
            return addWarning(formatMessage(message, arg1, args), Objects.requireNonNull(throwable));
        }

        private ViolationBuilder addWarning(String message, @Nullable Throwable throwable) {
            result.add(new Violation(rule, target, Severity.warning, message, throwable));
            return this;
        }

        /**
         * Formats a message by looking it up in the bundle and applying the supplied arguments.
         */
        private String formatMessage(String message, Object arg1, Object... args) {
            String pattern;
            try {
                pattern = BUNDLE.getString(rule.getClass().getSimpleName() + "." + message);
            } catch (MissingResourceException e) {
                pattern = message;
            }

            Object[] arguments;
            if (arg1 == null) {
                arguments = new Object[0];
            } else if (args.length == 0) {
                arguments = new Object[] { arg1 };
            } else {
                arguments = new Object[1 + args.length];
                arguments[0] = arg1;
                System.arraycopy(args, 0, arguments, 1, args.length);
            }

            return MessageFormat.format(pattern, arguments);
        }

        /**
         * Extracts a recognizable identifier from the supplied input.
         */
        private static Object getInputIdentifier(Object input) {
            if (input instanceof Vertex) {
                Vertex v = (Vertex) input;
                Object id = v.property(LT._id.name()).orElse(v.label() + "[" + v.id() + "]");
                return id instanceof Map<?, ?> ? getInputIdentifier(id) : id;
            } else if (input instanceof Edge) {
                return getInputIdentifier(((Edge) input).outVertex());
            } else if (input instanceof Map<?, ?>) {
                return firstNonNull(((Map<?, ?>) input).get(JsonLdConsts.ID), JsonLdConsts.DEFAULT);
            } else {
                throw new IllegalArgumentException("unknown context");
            }
        }
    }

    /**
     * A resource bundle that contains all of the linter messages. By convention, messages for individual rules are
     * prefixed with the simple name of the rule class.
     */
    public static class Messages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    { "DataPropertyRange.Invalid", "Invalid value for {}" },
                    { "DataPropertyRange.UnsupportedCharset", "Unsupported encoding" },
                    { "Domain.PropertyNotAllowed", "Property not allowed on {}: {}" },
                    { "ImpliedFileSystemTypeConflict.Parent", "Files with children should have a directory type" },
                    { "ImpliedFileSystemTypeConflict.ByteCount", "Files with sizes should have a regular type" },
                    { "ImpliedFileSystemTypeConflict.LinkPath", "Files with link paths should have symbolic link type" },
                    { "ImpliedFileSystemTypeConflict.Encoding", "Files with encodings should have text type" },
                    { "Metadata.DefaultNamedGraphIdentififer", "Named graph has default identifier" },
                    { "Metadata.MismatchedGraphLabel", "BDIO entries have different labels" },
                    { "Metadata.PropertyNotAllowed", "Property not allowed on @graph: {}" },
                    { "MissingFilePath.PathNotPresent", "File is missing path property" },
                    { "MissingProjectName.HasVersion", "Project has version but no name" },
                    { "MissingProjectName.NameNotPresent", "Project with no name should be a FileCollection" },
                    { "ObjectPropertyRange.InvalidRange", "Invalid object property range" },
                    { "SingleRoot.MissingMetadata", "Missing metadata instance" },
                    { "SingleRoot.MultipleMetadata", "Multiple metadata instances" },
                    { "SingleRoot.MultipleRoots", "Multiple roots" },
                    { "SingleRoot.MissingRoot", "Missing root" },
                    { "UnreferencedNode.UnreferencedNode", "Unreferenced node" },
                    { "ValidFilePath.PathNotNormalized", "File path should be normalized" },
                    { "ValidFilePath.String", "Path should be a string" },
                    { "ValidIdentifier.Absolute", "Node identifiers should be absolute" },
                    { "ValidIdentifier.Scheme", "Node identifier scheme is questionable" },
                    { "ValidIdentifier.MissingFileAuthority", "When using a 'file' URI as an identifier, it should include an authority" },
                    { "ValidIdentifier.Invalid", "Node identifier is not a valid URI" },
                    { "ValidIdentifier.String", "Node identifier should be a string" },
            };
        }
    }

    /**
     * Returns a stream of all the known rules.
     */
    public static Stream<Rule<?>> loadAllRules() {
        // TODO More then two license edges that are not all the same type
        // TODO Multiple canonical edges (e.g. "x -c-> y -c-> z" instead of "x -c-> z")
        // TODO Unreferenced/disconnected vertices
        // TODO Unsupported "range" values (e.g. using "chars" without an encoding)
        return Stream.<Rule<?>> builder()
                .add(new DataPropertyRange())
                .add(new Domain())
                .add(new ImpliedFileSystemTypeConflict())
                .add(new Metadata())
                .add(new MissingFilePath())
                .add(new MissingProjectName())
                .add(new Namespace())
                .add(new ObjectPropertyRange())
                .add(new SemanticRules())
                .add(new SingleRoot())
                .add(new UnreferencedNode())
                .add(new ValidFilePath())
                .add(new ValidIdentifier())
                .build();
    }

    private Linter() {
        assert false;
    }

}
