// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The proving set's stand-in for a target that accepts a simulation and then refuses the real insert
 * (ADR 0008): it takes every other insert it is asked for, committed or not, and keeps honestly what
 * a committed one put in. A test stands one at a lodestone, which has no items of its own; a lodestone
 * no test stood one at has none.
 */
final class FickleInventory extends DelegatingResourceHandler<ItemResource> {

    // By absolute position: the game tests share one level.
    private static final Map<BlockPos, FickleInventory> STANDING = new HashMap<>();

    // Every insert asked for, committed or not: the stand-in takes the even ones.
    private int asked;

    private FickleInventory() {
        super(new ItemStacksResourceHandler(27));
    }

    static FickleInventory standAt(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.LODESTONE);
        var inventory = new FickleInventory();
        STANDING.put(helper.absolutePos(pos), inventory);
        return inventory;
    }

    /** Forgets the stand-ins stood so far, as the tests are registered again. */
    static void clear() {
        STANDING.clear();
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) -> STANDING.get(pos), Blocks.LODESTONE);
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        return asked++ % 2 == 0 ? super.insert(resource, amount, transaction) : 0;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return asked++ % 2 == 0 ? super.insert(index, resource, amount, transaction) : 0;
    }

    /** How many inserts it was asked for. */
    int asked() {
        return asked;
    }

    /** How many items committed inserts put in. */
    int count() {
        int total = 0;
        for (int index = 0; index < size(); index++) total += getAmountAsInt(index);
        return total;
    }
}
