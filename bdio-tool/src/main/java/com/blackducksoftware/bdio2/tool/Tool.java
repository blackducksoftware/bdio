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
package com.blackducksoftware.bdio2.tool;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.annotation.OverridingMethodsMustInvokeSuper;

import com.blackducksoftware.common.io.ExtraIO;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;

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

        @Override
        protected void execute() throws Exception {
            // Do nothing
        }

        // TODO Suppress warning for not calling super
        @Override
        protected Tool parseArguments(String[] args) {
            return this;
        }
    }

    /**
     * Supported verbosity levels.
     */
    public enum Level {
        QUIET, DEFAULT, VERBOSE, DEBUG
    }

    /**
     * The print stream to use for standard output.
     */
    private final PrintStream stdout;

    /**
     * The print stream to use for standard errors.
     */
    private final PrintStream stderr;

    /**
     * The verbosity level of the error messages produced by the tool.
     */
    private Level verbosity = Level.DEFAULT;

    /**
     * Flag indicating we should produce pretty output.
     */
    private boolean pretty;

    protected Tool() {
        this(System.out, System.err);
    }

    protected Tool(PrintStream sysout, PrintStream syserr) {
        this.stdout = Objects.requireNonNull(sysout);
        this.stderr = Objects.requireNonNull(syserr);
    }

    @Override
    public final void run() {
        try {
            execute();
        } catch (Exception e) {
            if (Level.DEBUG.compareTo(verbosity) <= 0) {
                e.printStackTrace(stderr);
            } else if (Level.VERBOSE.compareTo(verbosity) <= 0) {
                stderr.println(e.toString());
            } else if (Level.DEFAULT.compareTo(verbosity) <= 0) {
                stderr.println(formatException(e));
            }
        }
    }

    /**
     * Parses the command line input, handling any errors cleanly. This is the method that should be invoked from you
     * {@code main} method.
     */
    public final Tool parseArgs(String[] args) {
        try {
            return parseArguments(args);
        } catch (Exception e) {
            if (Level.DEBUG.compareTo(verbosity) <= 0) {
                e.printStackTrace(stderr);
            } else if (Level.VERBOSE.compareTo(verbosity) <= 0) {
                stderr.println(e.toString());
            } else if (Level.DEFAULT.compareTo(verbosity) <= 0) {
                stderr.println(formatException(e));
            }
            return doNothing();
        }
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
    protected Tool parseArguments(String[] args) {
        for (String arg : options(args)) {
            if (arg.equals("--debug")) {
                checkArgument(verbosity == Level.DEFAULT, "Specify one of: --quiet --verbose --debug");
                verbosity = Level.DEBUG;
            } else if (arg.equals("--verbose")) {
                checkArgument(verbosity == Level.DEFAULT, "Specify one of: --quiet --verbose --debug");
                verbosity = Level.VERBOSE;
            } else if (arg.equals("--quiet")) {
                checkArgument(verbosity == Level.DEFAULT, "Specify one of: --quiet --verbose --debug");
                verbosity = Level.QUIET;
            } else if (arg.equals("--pretty")) {
                pretty = true;
            } else {
                stderr.format("unknown option: '%s'", arg);
                return doNothing();
            }
        }
        return this;
    }

    /**
     * Runs this tool.
     */
    protected abstract void execute() throws Exception;

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
        } else {
            return failure.getLocalizedMessage();
        }
    }

    /**
     * Prints the help page for this tool.
     * <p>
     * When overriding this method, use {@link printOutput} to ensure help is not filtered out by
     * the current verbosity settings.
     *
     * @param name
     *            the name used to invoke this tool
     */
    protected void printHelp(String name) {
        printOutput("No help is available for '%s'.%n", name);
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
     * Returns a byte source for accessing input. If the name is "-" then bytes will come from the standard input
     * stream, otherwise name is taken as a file path relative to the current working directory.
     */
    protected static ByteSource getInput(String name) {
        try {
            if (name.equals("-")) {
                return new ByteSource() {
                    @Override
                    public InputStream openStream() {
                        // TODO Block closing?
                        return System.in;
                    }
                };
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
    protected static ByteSink getOutput(String name) {
        try {
            if (name.equals("-")) {
                return new ByteSink() {
                    @Override
                    public OutputStream openStream() throws IOException {
                        // TODO Block closing?
                        return System.out;
                    }
                };
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
     * Returns a special tool that does absolutely nothing. This tool is useful to halt processing
     * when parsing command line arguments.
     */
    protected static Tool doNothing() {
        return DoNothingTool.INSTANCE;
    }

}
