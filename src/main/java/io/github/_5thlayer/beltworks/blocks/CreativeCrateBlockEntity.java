// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BlockEntitiesContent;

/**
 * What a creative crate is set to: a copy of the chosen stack, count 1, data and all, or empty for
 * none. It is saved with the block and sent to its watchers, who draw it, and it never travels with
 * the crate's item, so a broken or picked crate is blank. Its {@link #items() item handler} gives
 * the item without end and voids every insert.
 */
public final class CreativeCrateBlockEntity extends BlockEntity {

    private static final String ITEM = "item";

    private final ResourceHandler<ItemResource> items = new Supply();
    private ItemStack item = ItemStack.EMPTY;

    public CreativeCrateBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CREATIVE_CRATE.get(), pos, state);
    }

    /** The item it is set to, count 1, or empty; a copy. */
    public ItemStack item() {
        return item.copy();
    }

    /** Sets it to a copy of one of {@code stack}, or clears it with an empty stack. The stack is only read. */
    void setItem(ItemStack stack) {
        var set = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (ItemStack.matches(item, set)) return;
        item = set;
        setChanged();
        // Its watchers draw the item.
        if (level instanceof ServerLevel server) server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    /** Its item handler, the same on every side. */
    public ResourceHandler<ItemResource> items() {
        return items;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!item.isEmpty()) output.store(ITEM, ItemStack.CODEC, item);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        item = input.read(ITEM, ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    // A pick-block with data copies what this leaves: the item stays behind, as it does when the crate breaks.
    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard(ITEM);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        saveAdditional(output);
        return output.buildResult();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * One slot, always full at the item's stack size: a pull takes up to a stack of the item and
     * leaves the crate as it was, and anything put in is taken whole and gone. An unset crate's slot
     * is empty and gives nothing. Nothing it does changes the crate, so a transaction has nothing to undo.
     */
    private final class Supply implements ResourceHandler<ItemResource> {

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            Objects.checkIndex(index, size());
            return ItemResource.of(item);
        }

        @Override
        public long getAmountAsLong(int index) {
            Objects.checkIndex(index, size());
            return item.isEmpty() ? 0 : item.getMaxStackSize();
        }

        // Never full, so a hopper or a pipe that asks first still pushes in.
        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            Objects.checkIndex(index, size());
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            Objects.checkIndex(index, size());
            return true;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            return amount;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (item.isEmpty() || !resource.matches(item)) return 0;
            return Math.min(amount, item.getMaxStackSize());
        }
    }
}
