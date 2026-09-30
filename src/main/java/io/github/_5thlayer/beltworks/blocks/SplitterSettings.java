// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.world.item.ItemStack;
import io.github._5thlayer.beltworks.model.Splitter;

import java.util.Objects;

/**
 * What a player set on a splitter from its screen: one record for the splitter, not one per half,
 * lost when the splitter is broken. A filter never exists without an output priority side, so the
 * record cannot hold one: it holds a copy of one item, and none where the output priority is none.
 */
public record SplitterSettings(Splitter.Priority inputPriority, Splitter.Priority outputPriority, ItemStack filter) {

    /** A splitter nobody has set, which behaves as one with no settings at all. */
    public static final SplitterSettings NONE = new SplitterSettings(Splitter.Priority.NONE, Splitter.Priority.NONE, ItemStack.EMPTY);

    public SplitterSettings {
        filter = filter.isEmpty() || outputPriority == Splitter.Priority.NONE ? ItemStack.EMPTY : filter.copyWithCount(1);
    }

    public SplitterSettings withInputPriority(Splitter.Priority priority) {
        return new SplitterSettings(priority, outputPriority, filter);
    }

    /** Clearing the output priority clears the filter with it. */
    public SplitterSettings withOutputPriority(Splitter.Priority priority) {
        return new SplitterSettings(inputPriority, priority, filter);
    }

    /**
     * Sets the filter, or clears it when the stack is empty. A filter set with no output priority
     * sets it to {@code side}, the side the screen's switch shows.
     */
    public SplitterSettings withFilter(ItemStack stack, Splitter.Priority side) {
        var output = !stack.isEmpty() && outputPriority == Splitter.Priority.NONE ? side : outputPriority;
        return new SplitterSettings(inputPriority, output, stack);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SplitterSettings settings && inputPriority == settings.inputPriority
                 && outputPriority == settings.outputPriority && ItemStack.matches(filter, settings.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputPriority, outputPriority, ItemStack.hashItemAndComponents(filter));
    }
}
