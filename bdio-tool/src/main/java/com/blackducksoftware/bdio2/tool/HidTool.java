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
package com.blackducksoftware.bdio2.tool;

import static com.blackducksoftware.common.base.ExtraThrowables.illegalArgument;
import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptyList;

import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import com.blackducksoftware.common.value.HID;
import com.google.common.base.Strings;

/**
 * Print a Hierarchical Identifier (HID) used as a file path.
 *
 * @author jgustie
 */
public class HidTool extends Tool {

    public enum Format implements Function<HID, String> {
        DEFAULT {
            @Override
            public String apply(HID hid) {
                return hid.toString();
            }
        },
        URI {
            @Override
            public String apply(HID hid) {
                return hid.toUriString();
            }
        },
        TREE {
            @Override
            public String apply(HID hid) {
                return hid.getName();
            }
        },
    }

    public enum Sort implements Comparator<HID> {
        DEFAULT {
            @Override
            public int compare(HID left, HID right) {
                return 0; // Assume stable sort on input
            }
        },
        IGNORE_PATH {
            @Override
            public int compare(HID left, HID right) {
                return HID.ignorePathOrder().compare(left, right);
            }
        },
        PREORDER {
            @Override
            public int compare(HID left, HID right) {
                return HID.preOrder().compare(left, right);
            }
        },
    }

    // TODO Add "isAnscestor" functionality and such for testing HIDs
    // TODO Maybe a separate "file" or "path" tool for building HIDs or full file nodes off the file system
    // TODO Have an interactive builder?
    // TODO `--rebase <oldbase> <newbase> <hid...>`

    public static void main(String[] args) {
        new HidTool(null).parseArgs(args).run();
    }

    private List<String> uris = Collections.emptyList();

    private Format format = Format.DEFAULT;

    private Sort sort = Sort.DEFAULT;

    public HidTool(String name) {
        super(name);
    }

    public void setFormat(Format format) {
        this.format = Objects.requireNonNull(format);
    }

    public void setSort(Sort sort) {
        this.sort = Objects.requireNonNull(sort);
    }

    @Override
    protected void printUsage() {
        printOutput("usage: %s [--tree|--uri] [--sort=ignore-path|preorder] [uri ...]%n", name());
    }

    @Override
    protected void printHelp() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("--tree", "Display all inputs as a combined file tree");
        options.put("--uri", "Display the URI");
        options.put("--sort=<order>", "Change the sort order (default is the order of input)");
        printOptionHelp(options);
    }

    @Override
    protected boolean isOptionWithArgs(String option) {
        return super.isOptionWithArgs(option) || option.equals("--sort");
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String arg : options(args)) {
            if (arg.equals("--tree")) {
                setFormat(Format.TREE);
                args = removeFirst(arg, args);
            } else if (arg.equals("--uri")) {
                setFormat(Format.URI);
                args = removeFirst(arg, args);
            } else if (arg.startsWith("--sort=")) {
                setSort(optionValue(arg).map(s -> LOWER_HYPHEN.to(UPPER_UNDERSCORE, s)).map(Sort::valueOf)
                        .orElseThrow(illegalArgument("Invalid sort order: %s", arg)));
                args = removeFirst(arg, args);
            }
        }

        uris = arguments(args);
        if (uris.isEmpty()) {
            uris = getInput("-").asCharSource(Charset.defaultCharset()).readLines();
        }

        return super.parseArguments(args);
    }

    @Override
    protected String formatException(Throwable failure) {
        if (failure instanceof IllegalArgumentException && failure.getCause() instanceof URISyntaxException) {
            URISyntaxException syntaxException = (URISyntaxException) failure.getCause();
            int index = syntaxException.getIndex();
            if (index < 0) {
                return String.format("%s", syntaxException.getReason());
            } else {
                return String.format("%s:%n<%s>%n-%s^", syntaxException.getReason(), syntaxException.getInput(), Strings.repeat("-", index));
            }
        } else {
            return super.formatException(failure);
        }
    }

    @Override
    protected void execute() throws Exception {
        checkState(!uris.isEmpty(), "input is empty");
        if (format == Format.TREE) {
            printTree();
        } else {
            uris.stream().map(HID::from).sorted(sort).map(format).forEachOrdered(h -> printOutput("%s%n", h));
        }
    }

    private void printTree() {
        // Index all of the HIDs by parent, keeping track of the unique roots
        Map<HID, Collection<HID>> children = new HashMap<>();
        Set<HID> roots = new LinkedHashSet<>();
        for (String uri : uris) {
            HID hid = HID.from(uri);
            while (hid != null) {
                HID parent = hid.getParent();
                (parent != null ? children.computeIfAbsent(parent, HidTool::newChildren) : roots).add(hid);
                hid = parent;
            }
        }

        // Traverse the resulting hierarchy
        Deque<HID> hids = new ArrayDeque<>();
        Set<Integer> childrenAtDepths = new HashSet<>();
        hids.addAll(roots);
        while (!hids.isEmpty()) {
            HID hid = hids.pop();

            children.getOrDefault(hid, emptyList()).forEach(hids::offerFirst);
            int depth = depth(hid);
            boolean hasMoreSiblings = hasMoreSiblings(hid, children);
            if (hasMoreSiblings) {
                childrenAtDepths.add(depth - 1);
            } else {
                childrenAtDepths.remove(depth - 1);
            }

            StringBuilder buffer = new StringBuilder();
            TreeFormat.appendAsciiIndent(buffer, depth, childrenAtDepths::contains, hasMoreSiblings);
            buffer.append(format.apply(hid));
            if (hid.isRoot()) {
                if (hid.getScheme().equalsIgnoreCase("file")) {
                    buffer.append(" (file system root)");
                } else {
                    buffer.append(" (").append(hid.getScheme()).append(" expansion)");
                }
            }

            printOutput("%s%n", buffer);
        }
    }

    /**
     * Returns the depth of a HID.
     */
    private static int depth(HID hid) {
        int depth = -1;
        while (hid != null) {
            hid = hid.getParent();
            depth++;
        }
        return depth;
    }

    /**
     * Returns a new collection to use for aggregating children. Maintains a reverse sort since children will be pushed
     * onto the stack; it also makes it easier to check which HID will be the last sibling.
     */
    private static Collection<HID> newChildren(HID parent) {
        return new TreeSet<>(HID.ignorePathOrder().reversed());
    }

    /**
     * Checks to see if there are additional siblings for the provided HID.
     */
    private static boolean hasMoreSiblings(HID hid, Map<HID, Collection<HID>> children) {
        // The "last child" will appear first in the child list because of the reverse sort
        HID lastChild = children.getOrDefault(hid.getParent(), emptyList()).stream().findFirst().orElse(null);
        return !Objects.equals(hid, lastChild);
    }

}
