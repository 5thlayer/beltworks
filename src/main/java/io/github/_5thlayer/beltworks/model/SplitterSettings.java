// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * What a player set on a splitter from its screen: one record for the splitter, not one per half,
 * lost when the splitter is broken.
 */
public record SplitterSettings(Splitter.Priority inputPriority, Splitter.Priority outputPriority) {

    /** A splitter nobody has set, which behaves as one with no settings at all. */
    public static final SplitterSettings NONE = new SplitterSettings(Splitter.Priority.NONE, Splitter.Priority.NONE);

    public SplitterSettings withInputPriority(Splitter.Priority priority) {
        return new SplitterSettings(priority, outputPriority);
    }

    public SplitterSettings withOutputPriority(Splitter.Priority priority) {
        return new SplitterSettings(inputPriority, priority);
    }
}
