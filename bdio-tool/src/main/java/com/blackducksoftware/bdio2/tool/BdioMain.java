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

import static java.util.stream.Collectors.toList;

import java.util.Comparator;
import java.util.List;
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
        cat("Concatenate BDIO files", ConcatenateTool::new, false),
        context("Print a BDIO JSON-LD context", ContextTool::new, false),
        dependencies("Prints a dependency tree", DependenciesTool::new, true),
        entries("Dump the JSON-LD content of a BDIO file", EntriesTool::new, true),
        filter("Filter the contents of a BDIO file", FilterTool::new, false),
        graph("Import BDIO to a TinkerPop graph", GraphTool::new, false),
        head("Print metadata for BDIO files", HeadTool::new, true),
        hid("Print a Hierarchical Identifier (HID) used as a file path", HidTool::new, false),
        jsonld("Perform JSON-LD operations", JsonLdTool::new, false),
        lint("Check BDIO for common mistakes", LintTool::new, true),
        spdxdoc("Generates a BDIO document describing the standard SPDX licenses", SpdxDocumentTool::new, false),
        spec("Outputs the BDIO specification", SpecificationTool::new, false),
        tree("List contents of files in a tree-like format", TreeTool::new, true),
        viz("View an interactive BDIO graph in a web browser", VizTool::new, false),
        ;

        private final String description;

        @SuppressWarnings("ImmutableEnumChecker")
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

        /**
         * Prints the names of all the commands in the help output.
         */
        private boolean printAllCommands;

        private HelpTool(String name) {
            super(name.substring(name.indexOf(' ') + 1));
            this.toolName = name.substring(0, name.indexOf(' '));
        }

        @Override
        protected Tool parseArguments(String[] args) throws Exception {
            for (String arg : options(args)) {
                if (arg.equals("-a")) {
                    printAllCommands = true;
                    args = removeFirst(arg, args);
                }
            }

            commandName = Iterables.getFirst(arguments(args), name());
            return super.parseArguments(args);
        }

        @Override
        protected void printUsage() {
            printOutput("usage: %s [--version] [--help] [--quiet|--verbose|--debug] [--pretty]%n", toolName);
            printOutput("            <command> [<args>]%n%n");
        }

        @Override
        protected void printHelp() {
            int columnWidth = Stream.of(Command.values()).map(Command::name).mapToInt(String::length).max().getAsInt() + 2;
            if (printAllCommands) {
                printOutput("All the available %s commands:%n", toolName);
                List<Command> commands = Stream.of(Command.values())
                        .filter(c -> c != Command.help)
                        .sorted(Comparator.comparing(Command::name))
                        .collect(toList());
                int rows = (commands.size() + 5) / 6;
                for (int offset = 0; offset < rows; ++offset) {
                    printOutput("%n  ");
                    for (int i = offset; i < commands.size(); i += rows) {
                        printOutput("%-" + columnWidth + "s", commands.get(i).name());
                    }
                }
                printOutput("%n");
            } else {
                printOutput("The most commonly used %s commands are:%n", toolName);
                Stream.of(Command.values())
                        .filter(Command::isCommon)
                        .sorted(Comparator.comparing(Command::name))
                        .forEachOrdered(command -> printOutput("   %-" + columnWidth + "s %s%n", command.name(), command.description));
                printOutput("%n");
                printOutput("'%s help -a' lists available subcommands. ", toolName);
                printOutput("See '%s %s <command>' to read about a specific subcommand.%n", toolName, name());
            }
        }

        @Override
        protected void execute() throws Exception {
            Tool tool = Command.named(commandName).factory.apply(toolName + " " + commandName);
            if (tool instanceof HelpTool) {
                ((HelpTool) tool).printAllCommands = printAllCommands;
            }
            tool.printUsage();
            tool.printHelp();
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

    @SuppressWarnings("MissingSuperCall")
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

        // Do not delegate to the super, allow the commands to do the parsing
        return this;
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
