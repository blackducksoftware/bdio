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
package com.blackducksoftware.bdio2.tool;

import static com.blackducksoftware.common.base.ExtraStrings.beforeFirst;
import static com.blackducksoftware.common.base.ExtraStrings.removePrefix;
import static com.blackducksoftware.common.base.ExtraStrings.removeSuffix;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;

import com.blackducksoftware.bdio2.Bdio;
import com.blackducksoftware.bdio2.BdioWriter.BdioFile;
import com.blackducksoftware.bdio2.BdioWriter.StreamSupplier;
import com.blackducksoftware.common.io.ExtraIO;
import com.blackducksoftware.common.value.Product;
import com.fasterxml.jackson.core.JsonParseException;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;

/**
 * Base class for a simple command line tool.
 * <p>
 * Any configuration done when overriding {@link #parseArguments(String[])} should also be exposed
 * as a method (or methods) which can be called to use the tool programmatically.
 *
 * @author jgustie
 */
public abstract class Tool implements Runnable {

    /**
     * @see Tool#doNothing()
     */
    private static final class DoNothingTool extends Tool {
        private static final DoNothingTool INSTANCE = new DoNothingTool();

        private DoNothingTool() {
            super(null);
        }

        @Override
        protected void execute() throws Exception {
            // Do nothing
        }

        @SuppressWarnings("MissingSuperCall")
        @Override
        protected Tool parseArguments(String[] args) {
            return this;
        }
    }

    /**
     * Thrown to force the tool to halt using the currently configured exit routine.
     */
    protected static final class ExitException extends Exception {
        private static final long serialVersionUID = 1L;

        private final int status;

        protected ExitException(int status) {
            this.status = status;
        }

        protected ExitException(int status, Throwable cause) {
            super(cause);
            this.status = status;
        }
    }

    /**
     * Supported verbosity levels.
     */
    public enum Level {
        QUIET, DEFAULT, VERBOSE, DEBUG
    }

    /**
     * A byte source for the console.
     */
    private final class StandardInputSource extends ByteSource {
        @Override
        public InputStream openStream() {
            return ExtraIO.onIdle(stdin, 5, SECONDS, () -> {
                stderr.println("No interaction on stdin, aborting.");
                sysexit.accept(66);
            });
        }
    }

    /**
     * A byte sink for the console. Also supports "pretty printing" raw BDIO.
     */
    private final class StandardOutputSink extends ByteSink implements StreamSupplier {
        /**
         * The delimiter to use between entries. Not that the delimiter is given two arguments: the current entry number
         * (starting at -1) and the default entry name.
         */
        private final String entryDelimiter;

        /**
         * The number on entries.
         */
        private final AtomicInteger entryCount = new AtomicInteger(-1);

        public StandardOutputSink() {
            this("%n%n");
        }

        public StandardOutputSink(String entryDelimiter) {
            this.entryDelimiter = Objects.requireNonNull(entryDelimiter);
        }

        @Override
        public OutputStream newStream() throws IOException {
            int entryNumber = entryCount.incrementAndGet();
            stdout.format(entryDelimiter, entryNumber, Bdio.dataEntryName(entryNumber));
            return ExtraIO.ignoreClose(stdout);
        }

        @Override
        public OutputStream openStream() throws IOException {
            return stdout;
        }
    }

    /**
     * The name of the tool.
     */
    private final String name;

    /**
     * The input stream to use for standard input.
     */
    private final InputStream stdin;

    /**
     * The print stream to use for standard output.
     */
    private final PrintStream stdout;

    /**
     * The print stream to use for standard errors.
     */
    private final PrintStream stderr;

    /**
     * The system exit function.
     */
    private final IntConsumer sysexit;

    /**
     * The verbosity level of the error messages produced by the tool.
     */
    private Level verbosity = Level.DEFAULT;

    /**
     * Flag indicating we should produce pretty output.
     */
    private boolean pretty;

    protected Tool(@Nullable String name) {
        this(name, System.in, System.out, System.err, System::exit);
    }

    protected Tool(@Nullable String name, InputStream stdin, PrintStream stdout, PrintStream stderr, IntConsumer sysexit) {
        this.name = Optional.ofNullable(name).orElse(getClass().getSimpleName());
        this.stdin = Objects.requireNonNull(stdin);
        this.stdout = Objects.requireNonNull(stdout);
        this.stderr = Objects.requireNonNull(stderr);
        this.sysexit = Objects.requireNonNull(sysexit);

        // Make sure exceptions going through RxJava are handled correctly
        RxJavaPlugins.setErrorHandler(this::handleException);

        // Make sure exceptions on other threads are handled correctly
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> handleException(e));
    }

    @Override
    public final void run() {
        try {
            execute();
        } catch (Throwable e) {
            handleException(e);
            sysexit.accept(1);
        }
    }

    /**
     * Updates the verbosity level of this tool.
     */
    public void setVerbosity(Level verbosity) {
        this.verbosity = verbosity;
    }

    /**
     * Updates the pretty setting of this tool.
     */
    public void setPretty(boolean pretty) {
        this.pretty = pretty;
    }

    /**
     * Parses the command line input, handling any errors cleanly. This is the method that should be invoked from your
     * {@code main} method.
     */
    public final Tool parseArgs(String[] args) {
        try {
            // Normalize the options with arguments (e.g. ['-foo', 'bar'] becomes ['-foo=bar'])
            List<String> normalizedArgs = new ArrayList<>(args.length);
            for (int i = 0; i < args.length; ++i) {
                String arg = args[i];
                if (isOptionWithArgs(arg)) {
                    if (i < args.length - 1 && !args[i + 1].startsWith("-")) {
                        // There is another arg available and it doesn't look like an option...
                        normalizedArgs.add(arg + '=' + args[++i]);
                    } else {
                        return optionRequiresArgument(arg);
                    }
                } else if (arg.startsWith("-") && !arg.startsWith("--") && (arg.length() < 3 || arg.charAt(2) != '=')) {
                    // Split ['-xyz'] into ['-x', '-y', '-z']
                    arg.substring(1).chars().mapToObj(c -> "-" + (char) c).forEach(normalizedArgs::add);
                } else {
                    // Pass the argument through unchanged
                    normalizedArgs.add(arg);
                }
            }
            return parseArguments(Iterables.toArray(normalizedArgs, String.class));
        } catch (Exception e) {
            handleException(e);
            return doNothing();
        }
    }

    /**
     * Internal method for handling an exception based on the current verbosity level.
     */
    private void handleException(Throwable e) {
        if (e instanceof ExitException) {
            if (e.getCause() != null) {
                handleException(e.getCause());
            }
            sysexit.accept(((ExitException) e).status);
        } else if (Level.QUIET.compareTo(verbosity) < 0) {
            String prefix = beforeFirst(name(), ' ') + ": ";
            stderr.print(prefix);
            if (Level.DEBUG.compareTo(verbosity) <= 0) {
                e.printStackTrace(stderr);
            } else if (Level.VERBOSE.compareTo(verbosity) <= 0) {
                int depth = 0;
                Throwable failure = e;
                while (failure != null) {
                    stderr.print(Strings.repeat(" ", (depth > 0 ? prefix.length() - 4 : 0) + (4 * depth++)));
                    stderr.println(removePrefix(failure.toString(), failure.getClass().getName() + ": "));
                    failure = failure.getCause();
                }
            } else if (Level.DEFAULT.compareTo(verbosity) <= 0) {
                stderr.println(formatException(e));
            }
        }
    }

    /**
     * Internal method for reporting a missing argument to an option.
     */
    private Tool optionRequiresArgument(String option) {
        stderr.format("option requires an argument -- %s%n", CharMatcher.is('-').trimLeadingFrom(option));
        return doNothing();
    }

    /**
     * Internal method for reporting an unknown option.
     */
    private Tool unknownOption(String option) {
        printOutput("unknown option: '%s'%n", option);
        printUsage();
        return doNothing();
    }

    /**
     * Method for reporting a missing required option.
     */
    // TODO Have a "missingOptionGroup(String...options)" method?
    protected final Tool missingRequiredOption(String option) {
        stderr.format("missing required option: '%s'%n", option);
        return doNothing();
    }

    /**
     * Checks to see if an option has arguments. This should be overridden by subclasses, each option should include any
     * expected "-" or "--". Note that any argument matched by this test will automatically be concatenated with the
     * following argument (delimited by '=') when passed into {@link #parseArguments(String[])}. This also means that
     * users can choose between `--foo bar` or `--foo=bar` from the shell.
     */
    protected boolean isOptionWithArgs(String option) {
        return false;
    }

    /**
     * Parses the command line input. Returns the tool instance to be run (which may or may not be this instance).
     * <p>
     * IMPORTANT: When overriding this method, you must remove any option (arg starting with "-")
     * that you have already handled to avoid getting an "unknown option" failure.
     *
     * @see #removeFirst(String, String...)
     */
    @OverridingMethodsMustInvokeSuper
    protected Tool parseArguments(String[] args) throws Exception {
        for (String arg : options(args)) {
            if (arg.equals("--debug")) {
                checkArgument(verbosity == Level.DEFAULT, "Specify one of: --quiet --verbose --debug");
                setVerbosity(Level.DEBUG);
            } else if (arg.equals("--verbose")) {
                checkArgument(verbosity == Level.DEFAULT, "Specify one of: --quiet --verbose --debug");
                setVerbosity(Level.VERBOSE);
            } else if (arg.equals("--quiet")) {
                checkArgument(verbosity == Level.DEFAULT, "Specify one of: --quiet --verbose --debug");
                setVerbosity(Level.QUIET);
            } else if (arg.equals("--pretty")) {
                setPretty(true);
            } else {
                return unknownOption(arg);
            }
        }
        return this;
    }

    /**
     * Runs this tool.
     */
    protected abstract void execute() throws Exception;

    /**
     * Returns the name of this tool.
     */
    protected final String name() {
        return name;
    }

    /**
     * Check if this tool should produce pretty output.
     */
    protected final boolean isPretty() {
        return pretty;
    }

    /**
     * Formats an exception for reporting to the user.
     */
    protected String formatException(Throwable failure) {
        Throwable rootCause = Throwables.getRootCause(failure);
        if (rootCause instanceof FileNotFoundException || rootCause instanceof NoSuchFileException) {
            return rootCause.getMessage() + ": No such file or directory";
        } else if (rootCause instanceof JsonParseException) {
            JsonParseException parseException = (JsonParseException) rootCause;
            return String.format("%s at line %d, column %d", parseException.getOriginalMessage(),
                    parseException.getLocation().getLineNr(), parseException.getLocation().getColumnNr());
        } else {
            String message = failure.getLocalizedMessage();
            if (message == null) {
                // Anything is more useful then "null"
                message = failure.getClass().getSimpleName();
                message = removeSuffix(message, "Exception");
                message = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, message).replace('_', ' ');
            }
            return message;
        }
    }

    /**
     * Returns a product label with the supplied name.
     */
    protected final Product getProduct() {
        Product.Builder product = new Product.Builder()
                .implementationTitle(getClass())
                .implementationVersion(getClass());
        Optional.ofNullable(getClass().getPackage())
                .flatMap(p -> Optional.ofNullable(p.getSpecificationVersion()))
                .map(specVersion -> "(specification " + specVersion + ")")
                .ifPresent(product::comment);
        return product.build();
    }

    /**
     * Prints the usage for this tool.
     * <p>
     * When overriding this method, use {@link printOutput}: e.g. {@code printOutput("usage: %s%n", name())}.
     */
    protected void printUsage() {
        printOutput("usage: %s%n", name());
    }

    /**
     * Prints the help page for this tool.
     * <p>
     * When overriding this method, use {@link printOutput} to ensure help is not filtered out by
     * the current verbosity settings.
     */
    protected void printHelp() {
        printOutput("No help is available for '%s'.%n", name());
    }

    /**
     * Prints the option descriptions from the supplied map.
     */
    protected final void printOptionHelp(Map<String, String> optionDescriptions) {
        int maxOptionLength = optionDescriptions.entrySet().stream().mapToInt(e -> e.getValue() != null ? e.getKey().length() : 0).max().orElse(0);
        optionDescriptions.forEach((option, description) -> {
            if (description != null) {
                printOutput("  %-" + maxOptionLength + "s  %s%n", option, description);
            } else {
                printOutput("  ------- %s -------%n", option);
            }
        });
    }

    /**
     * Prints JSON output.
     */
    protected final void printJson(Object object) {
        try {
            PrintWriter sysout = ExtraIO.newPrintWriter(this.stdout, Charset.defaultCharset());
            if (pretty) {
                JsonUtils.writePrettyPrint(sysout, object);
            } else {
                JsonUtils.write(sysout, object);
            }
            sysout.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Prints a format string.
     */
    protected final void printOutput(String format, Object... args) {
        stdout.format(format, args);
    }

    /**
     * Prints a message.
     */
    protected final void printMessage(String message, Object... args) {
        if (Level.DEFAULT.compareTo(verbosity) <= 0) {
            stderr.format(message, args);
        }
    }

    /**
     * Prints a debug message.
     */
    protected final void printDebugMessage(String message, Object... args) {
        if (Level.DEBUG.compareTo(verbosity) <= 0) {
            stderr.format(message, args);
        }
    }

    /**
     * Returns a byte source for accessing input. If the name is "-" then bytes will come from the standard input
     * stream, otherwise name is taken as a file path relative to the current working directory.
     */
    protected final ByteSource getInput(String name) {
        try {
            if (name.equals("-")) {
                return new StandardInputSource();
            } else {
                File file = new File(name);
                if (!file.exists()) {
                    throw new FileNotFoundException(name);
                } else if (file.isDirectory()) {
                    throw new IOException(name); // TODO Message? Exception?
                } else if (!file.canRead()) {
                    throw new IOException(name); // TODO Message? Exception?
                } else {
                    return Files.asByteSource(file);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns a byte sink for generating output. If the name is "-" then bytes will go to the standard output
     * stream, otherwise name is taken as a file path relative to the current working directory.
     */
    protected final ByteSink getOutput(String name) {
        try {
            if (name.equals("-")) {
                return new StandardOutputSink();
            } else {
                File file = new File(name);
                if (file.isDirectory()) {
                    throw new IOException(name); // TODO Message? Exception?
                } else if (file.exists() && !file.canWrite()) {
                    throw new IOException(name); // TODO Message? Exception?
                } else {
                    return Files.asByteSink(file);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Removes the first occurrence of a string from an array of strings.
     */
    protected static String[] removeFirst(String value, String... values) {
        List<String> valueList = Lists.newArrayList(values);
        valueList.remove(value);
        return Iterables.toArray(valueList, String.class);
    }

    /**
     * Check to see if a string occurs before another string in an array of strings.
     */
    protected static boolean isBefore(String first, String second, String... values) {
        List<String> valueList = Arrays.asList(values);
        return valueList.indexOf(first) < valueList.indexOf(second);
    }

    /**
     * Extracts the value from the supplied option argument. Options start with "-" and the value starts after the first
     * "="; for example given the string "--foo=bar" this method returns a non-empty optional with the value "bar".
     */
    protected static Optional<String> optionValue(String arg) {
        checkArgument(arg.startsWith("-"), "expected option string: %s", arg);
        List<String> option = Splitter.on('=').limit(2).splitToList(arg);
        return option.size() > 1 ? Optional.of(option.get(1)) : Optional.empty();
    }

    /**
     * Returns the options from the command line input. Options start with "-" and are before "--".
     */
    protected static List<String> options(String[] args) {
        List<String> options = new ArrayList<>(args.length);
        for (String arg : args) {
            if (arg.equals("--")) {
                break;
            } else if (arg.startsWith("-")) {
                options.add(arg);
            }
        }
        return options;
    }

    /**
     * Returns the arguments from the command line input. Arguments do not start with "-" or are after "--".
     */
    protected static List<String> arguments(String[] args) {
        List<String> arguments = new ArrayList<>(args.length);
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("--")) {
                arguments.addAll(Arrays.asList(args).subList(i + 1, args.length));
                break;
            } else if (!args[i].startsWith("-")) {
                arguments.add(args[i]);
            }
        }
        return arguments;
    }

    /**
     * Borrows a subset of arguments initiated by the specified option. The resulting two dimensional array will include
     * the original arguments (minus those that were borrowed) at position 0 and the borrowed arguments at position 1.
     */
    protected static String[][] borrowArguments(String[] args, String option) {
        // Find the range of borrowed arguments starting with the option and going until the ';'
        int start = -1;
        int end = args.length;
        for (int i = 0; i < args.length; ++i) {
            if (args[i].startsWith(option)) {
                start = i;
            } else if (args[i].equals(";")) {
                end = i;
            }
        }

        String[][] result = new String[2][];
        if (start >= 0) {
            // Start by extracting the borrowed arguments (correct the first argument by removing the option)
            result[1] = Arrays.copyOfRange(args, start, end);
            result[1][0] = removePrefix(result[1][0], option);

            // Remove the borrowed arguments from the main list of arguments
            result[0] = new String[args.length - result[1].length - (end < args.length ? 1 : 0)];
            System.arraycopy(args, 0, result[0], 0, start);
            if (result[0].length > start) {
                System.arraycopy(args, end + 1, result[0], start, args.length - result[0].length - result[1].length);
            }
        } else {
            // No borrowing, just keep all the original options
            result[0] = args;
            result[1] = new String[0];
        }
        return result;
    }

    /**
     * Simulates shell tokenization on an argument.
     */
    protected static List<String> commandArgument(String arg) {
        ImmutableList.Builder<String> result = ImmutableList.builder();
        try {
            // Setup the tokenizer to be more shell like
            StreamTokenizer tokenizer = new StreamTokenizer(new StringReader(arg));
            tokenizer.ordinaryChar('/');
            tokenizer.wordChars('/', '/');
            tokenizer.commentChar('#');

            // Spin through the tokenizer to generate the command
            int token = tokenizer.nextToken();
            while (token != StreamTokenizer.TT_EOF) {
                // Eat line breaks
                if (tokenizer.ttype != StreamTokenizer.TT_EOL) {
                    result.add(tokenizer.sval);
                }
                token = tokenizer.nextToken();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result.build();
    }

    /**
     * Returns a special tool that does absolutely nothing. This tool is useful to halt processing
     * when parsing command line arguments.
     */
    protected static Tool doNothing() {
        return DoNothingTool.INSTANCE;
    }

    /**
     * Returns an environment variable as an optional.
     */
    protected static Optional<String> env(String name) {
        return Optional.ofNullable(System.getenv(name));
    }

    /**
     * Returns a BDIO stream supplier for a given byte sink. Capturing a byte sink using {@link #getOutput(String)} and
     * then using this method ensures the opening a file is deferred until the file is needed.
     */
    protected StreamSupplier getBdioOutput(ByteSink output) throws IOException {
        // There is no 'isatty' test in Java for just stdout, checking for a console tests stdout AND stdin
        if (output instanceof StandardOutputSink && (isPretty() || System.console() != null)) {
            return (StreamSupplier) output;
        } else {
            return new BdioFile(output.openStream());
        }
    }

}
