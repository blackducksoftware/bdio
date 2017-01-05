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
import java.util.Optional;
import java.util.function.Supplier;
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
        convert("Convert legacy formats into BDIO", ConvertTool::new, true),
        entries("Dump the JSON-LD content of a BDIO file", EntriesTool::new, true),
        ;

        private final String description;

        private final Supplier<Tool> factory;

        private final boolean common;

        private Command(String description, Supplier<Tool> factory, boolean common) {
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
                throw new IllegalArgumentException("bdio: '" + commandName + "' is not a bdio command. See 'bdio --help'.");
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
         * The name of the command to display help for.
         */
        private String commandName;

        @Override
        protected Tool parseArguments(String[] args) {
            commandName = Iterables.getFirst(arguments(args), "help");
            return super.parseArguments(args);
        }

        @Override
        protected void printHelp(String name) {
            printOutput("usage: bdio [--version] [--help] [--quiet|--verbose|--debug] [--pretty]%n");
            printOutput("            <command> [<args>]%n%n");
            printOutput("The most commonly used bdio commands are:%n");
            Stream.of(Command.values())
                    .filter(Command::isCommon)
                    .sorted(Comparator.comparing(Command::name))
                    .forEachOrdered(command -> printOutput("   %-12s %s%n", command.name(), command.description));
            printOutput("%nSee 'bdio help <command>' to read about a specific subcommand.%n");
        }

        @Override
        protected void execute() throws Exception {
            Command.named(commandName).factory.get().printHelp("bdio " + commandName);
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
     * Sets the command to run with it's arguments.
     */
    public void setCommand(String commandName, String... commandArgs) {
        this.commandName = Objects.requireNonNull(commandName);
        this.commandArgs = Objects.requireNonNull(commandArgs);
    }

    @Override
    protected Tool parseArguments(String[] args) {
        commandName = Iterables.getFirst(arguments(args), "help");
        commandArgs = removeFirst(commandName, args);

        // Special behavior for help
        if (options(args).contains("--help")) {
            commandArgs = removeFirst("--help", ObjectArrays.concat(commandName, commandArgs));
            commandName = "help";
            args = removeFirst("--help", args);
        }

        // Display our version number and stop (note that commands can still override '--version')
        if (options(args).contains("--version")
                && (commandName.equals("help") || isBefore("--version", commandName, args))) {
            printVersion();
            return doNothing();
        }

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        Command.named(commandName).factory.get().parseArgs(commandArgs).run();
    }

    /**
     * Prints the version of this tool to standard output.
     */
    protected void printVersion() {
        String implVersion = Optional.ofNullable(getClass().getPackage().getImplementationVersion()).orElse("unknown");
        String specVersion = Optional.ofNullable(getClass().getPackage().getSpecificationVersion()).orElse("unknown");
        printOutput("bdio version %s (specification %s)%n", implVersion, specVersion);
    }

}
