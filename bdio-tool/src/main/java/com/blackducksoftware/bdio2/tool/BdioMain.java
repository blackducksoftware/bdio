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

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.common.collect.Iterables;
import com.google.common.collect.ObjectArrays;

/**
 * Main entry point for running any of the BDIO tools.
 *
 * @author jgustie
 */
public class BdioMain extends Tool {

    /**
     * The subcommands supported by this tool.
     */
    private enum Command {
        help("Display help information about BDIO", HelpTool::new, false),
        context("Print the BDIO JSON-LD context", ContextTool::new, true),
        entries("Dump the JSON-LD content of a BDIO file", EntriesTool::new, true),
        ;

        private final String description;

        private final Function<String, Tool> factory;

        private final boolean common;

        private Command(String description, Function<String, Tool> factory, boolean common) {
            this.description = Objects.requireNonNull(description);
            this.factory = Objects.requireNonNull(factory);
            this.common = common;
        }

        /**
         * Finds the command with the specified name, failing if it does not exist.
         */
        private static Command named(String commandName) {
            try {
                return Command.valueOf(commandName);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("'" + commandName + "' is not a bdio command. See 'bdio --help'.");
            }
        }

        /**
         * Tests if this is a "common" command which should be displayed in the default 'help' output.
         */
        public boolean isCommon() {
            return common;
        }
    }

    /**
     * Tool for generating help text.
     */
    private static final class HelpTool extends Tool {

        /**
         * The name of the tool we are generating help for (e.g. "bdio").
         */
        private final String toolName;

        /**
         * The name of the command to display help for.
         */
        private String commandName;

        private HelpTool(String name) {
            super(name.substring(name.indexOf(' ') + 1));
            this.toolName = name.substring(0, name.indexOf(' '));
        }

        @Override
        protected Tool parseArguments(String[] args) {
            commandName = Iterables.getFirst(arguments(args), name());
            return super.parseArguments(args);
        }

        @Override
        protected void printHelp() {
            printOutput("usage: %s [--version] [--help] [--quiet|--verbose|--debug] [--pretty]%n", toolName);
            printOutput("            <command> [<args>]%n%n");
            printOutput("The most commonly used %s commands are:%n", toolName);
            Stream.of(Command.values())
                    .filter(Command::isCommon)
                    .sorted(Comparator.comparing(Command::name))
                    .forEachOrdered(command -> printOutput("   %-12s %s%n", command.name(), command.description));
            printOutput("%nSee '%s %s <command>' to read about a specific subcommand.%n", toolName, name());
        }

        @Override
        protected void execute() throws Exception {
            Command.named(commandName).factory.apply(toolName + " " + commandName).printHelp();
        }
    }

    public static void main(String[] args) {
        new BdioMain().parseArguments(args).run();
    }

    /**
     * The name of the command to execute.
     */
    private String commandName;

    /**
     * The arguments to pass into the command.
     */
    private String[] commandArgs;

    /**
     * Private constructor, this tool should only be created through {@code main}.
     */
    private BdioMain() {
        super("bdio");
    }

    /**
     * Sets the command to run with it's arguments.
     */
    public void setCommand(String commandName, String... commandArgs) {
        this.commandName = Objects.requireNonNull(commandName);
        this.commandArgs = Objects.requireNonNull(commandArgs);
    }

    @Override
    protected Tool parseArguments(String[] args) {
        commandName = Iterables.getFirst(arguments(args), Command.help.name());
        commandArgs = removeFirst(commandName, args);

        // Special behavior for help
        if (options(args).contains("--help")) {
            commandArgs = removeFirst("--help", ObjectArrays.concat(commandName, commandArgs));
            commandName = Command.help.name();
            args = removeFirst("--help", args);
        }

        // Display our version number and stop (note that commands can still override '--version')
        if (options(args).contains("--version")
                && (commandName.equals(Command.help.name()) || isBefore("--version", commandName, args))) {
            printVersion();
            return doNothing();
        }

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        Command.named(commandName).factory.apply(name() + " " + commandName).parseArgs(commandArgs).run();
    }

    /**
     * Prints the version of this tool to standard output.
     */
    protected void printVersion() {
        printOutput("%s%n", getProduct().toString().replaceFirst("/", " version "));
    }

}
