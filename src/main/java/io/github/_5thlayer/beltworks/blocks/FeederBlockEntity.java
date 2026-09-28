// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import io.github._5thlayer.beltworks.BeltworksConfig;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.api.item.ItemApi;
import io.github._5thlayer.beltworks.model.FlowLimit;
import io.github._5thlayer.beltworks.model.LoaderEnergy;

/**
 * Moves one item at a time from the inventory its head reaches to the one its tail reaches, at a
 * tenth of its tier's loader and for FE of its own, whatever the config says of loaders.
 */
public class FeederBlockEntity extends BlockEntity {

    private final FlowLimit flow;
    private final LoaderEnergy energy;

    public FeederBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.FEEDER.get(), pos, state);
        var tier = ((FeederBlock) state.getBlock()).tier();
        flow = new FlowLimit(tier.feederItemsPerTick());
        // Read once, as a loader's is: a changed FE rate reaches a feeder when its chunk next loads.
        energy = LoaderEnergy.feeder(tier, BeltworksConfig.loaderPower());
    }

    void tick(Level level, BlockPos pos, BlockState state) {
        if (!flow.ready(level.getGameTime()) || !energy.canMove()) return;
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        var head = ItemApi.BLOCK.find(level, pos.relative(facing.getOpposite()), null, null, facing);
        var tail = ItemApi.BLOCK.find(level, pos.relative(facing), null, null, facing.getOpposite());
        if (head == null || tail == null) return;

        for (int slot = 0; slot < head.getSlotCount(); slot++) {
            var available = head.getStackInSlot(slot);
            if (available.isEmpty()) continue;
            var one = available.copyWithCount(1);
            if (tail.insert(one, true) != 1 || head.extract(one, true) != 1) continue;
            if (head.extract(one, false) != 1) continue;
            // An inventory whose simulation lied gets its item back, so nothing is lost.
            if (tail.insert(one, false) != 1) {
                head.insert(one, false);
                continue;
            }
            flow.pass();
            energy.move();
            setChanged();
            return;
        }
    }

    public LoaderEnergy getEnergy() {
        return energy;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("energy", energy.joules());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.setJoules(input.getLongOr("energy", 0));
    }
}
